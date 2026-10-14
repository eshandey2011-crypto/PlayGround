// Unified keyboard/mouse + Android touch input (joystick, buttons, camera drag).
export class Input {
  constructor() {
    this.keys = {};
    this.move = { x: 0, y: 0 };       // -1..1 (x: strafe, y: forward)
    this.joy = { x: 0, y: 0 };
    this.sprint = false;
    this.camDX = 0; this.camDY = 0;   // accumulated camera deltas
    this.touchMode = ('ontouchstart' in window) || navigator.maxTouchPoints > 0;
    this.pressed = {};                // edge-triggered queue
    this.camSens = 1.0;
    this.enabled = true;
  }
  attach(canvas) {
    window.addEventListener('keydown', (e) => {
      if (e.repeat) return;
      this.keys[e.code] = true;
      this.pressed[e.code] = true;
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) e.preventDefault();
    });
    window.addEventListener('keyup', (e) => { this.keys[e.code] = false; });
    // mouse camera
    let dragging = false, lx = 0, ly = 0;
    canvas.addEventListener('mousedown', (e) => { dragging = true; lx = e.clientX; ly = e.clientY; });
    window.addEventListener('mouseup', () => { dragging = false; });
    window.addEventListener('mousemove', (e) => {
      if (!dragging || !this.enabled) return;
      this.camDX += (e.clientX - lx) * 0.0045 * this.camSens;
      this.camDY += (e.clientY - ly) * 0.0045 * this.camSens;
      lx = e.clientX; ly = e.clientY;
    });
    this._bindTouch();
    this._pollKeys();
  }
  _pollKeys() {
    const k = this.keys;
    const x = (k['KeyD'] || k['ArrowRight'] ? 1 : 0) - (k['KeyA'] || k['ArrowLeft'] ? 1 : 0);
    const y = (k['KeyW'] || k['ArrowUp'] ? 1 : 0) - (k['KeyS'] || k['ArrowDown'] ? 1 : 0);
    this.keyMove = { x, y };
    this.sprint = !!(k['ShiftLeft'] || k['ShiftRight']);
  }
  consume(code) { // e.g. 'KeyJ'
    this._pollKeys();
    if (this.pressed[code]) { delete this.pressed[code]; return true; }
    return false;
  }
  consumeAction(name) { // mapped actions
    this._pollKeys();
    const map = {
      attack: ['KeyJ'], heavy: ['KeyK'], jump: ['KeyF'], dodge: ['Space'],
      interact: ['KeyE'], lock: ['Tab', 'KeyQ'], skill1: ['Digit1'], skill2: ['Digit2'],
      potion: ['Digit3'], inv: ['KeyI'], pause: ['Escape'],
    };
    let hit = false;
    for (const c of (map[name] || [])) if (this.pressed[c]) { delete this.pressed[c]; hit = true; }
    if (this._touchFlags && this._touchFlags[name]) { this._touchFlags[name] = false; hit = true; }
    return hit;
  }
  blocking() {
    this._pollKeys();
    return !!(this.keys['KeyL'] || (this._touchFlags && this._touchHeld.block));
  }
  getMove() {
    this._pollKeys();
    const x = clamp1(this.keyMove.x + this.joy.x);
    const y = clamp1(this.keyMove.y + this.joy.y);
    return { x, y, sprint: this.sprint || this.joySprint };
  }
  takeCam() { const dx = this.camDX, dy = this.camDY; this.camDX = 0; this.camDY = 0; return { dx, dy }; }

  _bindTouch() {
    this._touchFlags = {};
    this._touchHeld = { block: false };
    const joy = document.getElementById('joystick');
    const stick = document.getElementById('stick');
    if (!joy) return;
    let joyId = null, cx = 0, cy = 0;
    const R = 44;
    const setStick = (dx, dy) => { stick.style.transform = `translate(calc(-50% + ${dx}px), calc(-50% + ${dy}px))`; };
    joy.addEventListener('touchstart', (e) => {
      e.preventDefault();
      const t = e.changedTouches[0]; joyId = t.identifier;
      const r = joy.getBoundingClientRect(); cx = r.left + r.width / 2; cy = r.top + r.height / 2;
    }, { passive: false });
    const moveH = (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier !== joyId) continue;
        e.preventDefault();
        let dx = t.clientX - cx, dy = t.clientY - cy;
        const m = Math.hypot(dx, dy);
        if (m > R) { dx = dx / m * R; dy = dy / m * R; }
        setStick(dx, dy);
        this.joy.x = dx / R; this.joy.y = -dy / R;
        this.joySprint = m > R * 0.92;
      }
    };
    joy.addEventListener('touchmove', moveH, { passive: false });
    const endH = (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier !== joyId) continue;
        joyId = null; this.joy.x = 0; this.joy.y = 0; this.joySprint = false; setStick(0, 0);
      }
    };
    joy.addEventListener('touchend', endH); joy.addEventListener('touchcancel', endH);
    // camera drag on canvas (right side, single finger not on buttons)
    const canvas = document.getElementById('game-canvas');
    let camId = null, px = 0, py = 0;
    canvas.addEventListener('touchstart', (e) => {
      for (const t of e.changedTouches) {
        if (t.clientX > window.innerWidth * 0.35 && camId === null) { camId = t.identifier; px = t.clientX; py = t.clientY; }
      }
    }, { passive: true });
    canvas.addEventListener('touchmove', (e) => {
      for (const t of e.changedTouches) {
        if (t.identifier !== camId) continue;
        this.camDX += (t.clientX - px) * 0.006 * this.camSens;
        this.camDY += (t.clientY - py) * 0.006 * this.camSens;
        px = t.clientX; py = t.clientY;
      }
    }, { passive: true });
    const camEnd = (e) => { for (const t of e.changedTouches) if (t.identifier === camId) camId = null; };
    canvas.addEventListener('touchend', camEnd); canvas.addEventListener('touchcancel', camEnd);
    // buttons
    const btn = (id, name, holdable) => {
      const el = document.getElementById(id); if (!el) return;
      el.addEventListener('touchstart', (e) => {
        e.preventDefault();
        if (holdable) { this._touchHeld[name] = true; el.classList.add('on'); }
        else this._touchFlags[name] = true;
      }, { passive: false });
      if (holdable) {
        const off = (e) => { e.preventDefault(); this._touchHeld[name] = false; el.classList.remove('on'); };
        el.addEventListener('touchend', off); el.addEventListener('touchcancel', off);
      }
      el.addEventListener('mousedown', (e) => { e.preventDefault(); if (!holdable) this._touchFlags[name] = true; });
    };
    btn('tb-atk', 'attack'); btn('tb-hvy', 'heavy'); btn('tb-jump', 'jump');
    btn('tb-dodge', 'dodge'); btn('tb-lock', 'lock'); btn('tb-interact', 'interact');
    btn('tb-s1', 'skill1'); btn('tb-s2', 'skill2');
    btn('tb-block', 'block', true);
  }
}
function clamp1(v) { return Math.max(-1, Math.min(1, v)); }
