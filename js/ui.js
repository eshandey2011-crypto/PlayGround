// All DOM UI: HUD, minimap, tracker, panels (inventory/skills/quests/stats/settings), dialogue, toasts.
import { CONFIG } from './config.js';
import { audio } from './audio.js';

export class UI {
  constructor(game) {
    this.game = game;
    this.el = (id) => document.getElementById(id);
    this.comboTimer = null;
    this.mm = this.el('minimap').getContext('2d');
  }
  toast(msg, ms = 2600) {
    const w = this.el('toast-wrap');
    const d = document.createElement('div');
    d.className = 'toast'; d.textContent = msg;
    w.appendChild(d);
    while (w.children.length > 3) w.removeChild(w.firstChild);
    setTimeout(() => { d.style.opacity = '0'; d.style.transition = 'opacity .4s'; setTimeout(() => d.remove(), 400); }, ms);
  }
  showCombo(n) {
    const c = this.el('combo-pop');
    c.textContent = n + ' HITS!';
    c.classList.remove('hidden');
    clearTimeout(this.comboTimer);
    this.comboTimer = setTimeout(() => c.classList.add('hidden'), 1400);
  }
  damageNumber(worldPos, text, cls = '') {
    const cam = this.game.camera; if (!cam) return;
    const v = worldPos.clone(); v.y += 2.2;
    v.project(cam);
    if (v.z > 1) return;
    const x = (v.x * 0.5 + 0.5) * window.innerWidth;
    const y = (-v.y * 0.5 + 0.5) * window.innerHeight;
    const d = document.createElement('div');
    d.className = 'dmgnum ' + cls; d.textContent = text;
    d.style.left = (x + (Math.random() * 40 - 20)) + 'px'; d.style.top = y + 'px';
    this.el('dmgnums').appendChild(d);
    requestAnimationFrame(() => { d.style.transform = 'translateY(-60px)'; d.style.opacity = '0'; });
    setTimeout(() => d.remove(), 850);
  }
  updateHUD() {
    const g = this.game, p = g.player;
    this.el('hp-fill').style.width = (100 * p.hp / p.maxHP) + '%';
    this.el('hp-text').textContent = `${Math.max(0, Math.ceil(p.hp))} / ${p.maxHP}`;
    this.el('st-fill').style.width = (100 * p.st / p.maxST) + '%';
    this.el('en-fill').style.width = (100 * p.en / p.maxEN) + '%';
    this.el('level-num').textContent = p.level;
    this.el('xp-fill').style.width = (100 * p.xp / p.xpNeed()) + '%';
    const q = g.quests.objectiveText();
    this.el('qt-title').textContent = q.title;
    this.el('qt-obj').textContent = q.obj;
    this.el('qt-dist').textContent = q.dist ? '◈ ' + q.dist : '';
    // clock
    const t = g.world.time, hh = String(Math.floor(t * 24)).padStart(2, '0'), mm = String(Math.floor((t * 24 % 1) * 60)).padStart(2, '0');
    const night = g.skyInfo?.isNight;
    this.el('clock').textContent = `${night ? '🌙' : '☀'} ${hh}:${mm}`;
    this.el('weather-ico').textContent = { clear: '🌤', rain: '🌧', storm: '⛈', fog: '🌫' }[g.weather.state] || '';
    // boss bar
    const boss = g.enemies.find((e) => e.type === 'boss' && e.alive && g.bossActive);
    if (boss) {
      this.el('boss-bar').classList.remove('hidden');
      this.el('boss-fill').style.width = (100 * boss.hp / boss.maxHP) + '%';
    } else this.el('boss-bar').classList.add('hidden');
    // low-hp vignette
    this.el('vignette').classList.toggle('hurt', p.hp < p.maxHP * 0.3);
  }
  drawMinimap() {
    const g = this.game, ctx = this.mm, S = 168, R = 90;
    ctx.clearRect(0, 0, S, S);
    const px = g.player.pos.x, pz = g.player.pos.z;
    const dot = (x, z, color, r = 3) => {
      const dx = (x - px) / R * (S / 2), dz = (z - pz) / R * (S / 2);
      if (Math.hypot(dx, dz) > S / 2 - 4) return;
      ctx.fillStyle = color; ctx.beginPath(); ctx.arc(S / 2 + dx, S / 2 + dz, r, 0, 7); ctx.fill();
    };
    // bg rings
    ctx.strokeStyle = 'rgba(232,182,76,.25)';
    ctx.beginPath(); ctx.arc(S / 2, S / 2, S / 3, 0, 7); ctx.stroke();
    const L = g.world;
    dot(0, 20, '#ffd76a', 4);                    // village gold
    dot(90, 0, '#ff6a5a', 4);                    // camp red
    dot(-95, -40, '#66ccff', 4);                 // cave cyan
    dot(-30, 120, '#bb88ff', 4);                 // temple purple
    dot(120, -130, '#ff3355', 5);                // summit
    for (const e of g.enemies) if (e.alive) dot(e.pos.x, e.pos.z, '#ff4444', 2.4);
    for (const n of g.npcs) dot(n.pos.x, n.pos.z, '#7dff9a', 2.6);
    for (const c of L.chests) if (c.visible) dot(c.position.x, c.position.z, '#e8b64c', 2.2);
    const b = g.quests.beaconTarget();
    if (b) {
      dot(b.x, b.z, '#ffffff', 3.5);
      ctx.strokeStyle = '#fff';
      ctx.beginPath(); ctx.moveTo(S / 2, S / 2);
      const a = Math.atan2(b.x - px, b.z - pz);
      ctx.lineTo(S / 2 + Math.sin(a) * (S / 2 - 8), S / 2 + Math.cos(a) * (S / 2 - 8)); ctx.stroke();
    }
    // player arrow (camera-relative north = -z up)
    ctx.save(); ctx.translate(S / 2, S / 2); ctx.rotate(Math.atan2(Math.sin(g.player.yaw), Math.cos(g.player.yaw)) + Math.PI);
    ctx.fillStyle = '#fff'; ctx.beginPath(); ctx.moveTo(0, -7); ctx.lineTo(5, 5); ctx.lineTo(-5, 5); ctx.closePath(); ctx.fill();
    ctx.restore();
  }

  // ---- dialogue ----
  showDialogue(name, lines, onDone, options = null) {
    this.el('dialogue').classList.remove('hidden');
    this.el('dlg-name').textContent = name;
    let i = 0;
    const optsBox = this.el('dlg-opts'); optsBox.innerHTML = '';
    const next = this.el('dlg-next');
    const render = () => {
      if (i < lines.length) {
        const L = lines[i];
        this.el('dlg-name').textContent = L.who;
        this.el('dlg-text').textContent = L.text;
        audio.ui();
      } else if (options) {
        this.el('dlg-text').textContent = 'Anything else, warrior?';
        optsBox.innerHTML = '';
        for (const o of options) {
          const b = document.createElement('button');
          b.className = 'mbtn'; b.style.marginTop = '4px'; b.textContent = o.label;
          b.onclick = () => { audio.ui(); o.fn(); };
          optsBox.appendChild(b);
        }
        next.textContent = 'Leave ✕';
      } else { this.hideDialogue(); onDone && onDone(); return; }
      next.textContent = (i >= lines.length - 1 && !options) ? 'Finish ✓' : 'Continue ▸';
    };
    next.onclick = () => { i++; render(); };
    render();
    this._dlgNext = next;
  }
  hideDialogue() { this.el('dialogue').classList.add('hidden'); }

  // ---- panel ----
  openPanel(tab = 'inv') {
    this.el('panel').classList.remove('hidden');
    this.renderPanel(tab);
    this.game.paused = true;
  }
  closePanel() { this.el('panel').classList.add('hidden'); if (!this.game.dead) this.game.paused = false; }
  renderPanel(tab) {
    document.querySelectorAll('.ptab[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === tab));
    for (const t of ['inv', 'skills', 'quests', 'stats', 'settings']) this.el('tab-' + t).classList.add('hidden');
    this.el('tab-' + tab).classList.remove('hidden');
    const g = this.game, p = g.player;
    if (tab === 'inv') {
      const box = this.el('tab-inv'); box.innerHTML = `<p>🪙 Gold: <b style="color:var(--gold)">${p.gold}</b> &nbsp; 🗡 ${p.weapon.name} <span style="color:#9aa3bd">(${p.weapon.desc})</span></p><div class="inv-grid"></div>`;
      const grid = box.querySelector('.inv-grid');
      const use = (id, label, fn) => {
        const d = document.createElement('div'); d.className = 'inv-item';
        d.innerHTML = `<b>${label}</b> × ${p.inv[id]}<br><span style="color:#9aa3bd">${CONFIG.items[id].desc}</span>`;
        const b = document.createElement('button'); b.className = 'mbtn'; b.textContent = 'Use';
        b.onclick = () => { fn(); this.renderPanel('inv'); };
        d.appendChild(b); grid.appendChild(d);
      };
      use('potion', '🧪 Health Potion', () => g.useItem('potion'));
      use('elixir', '⚡ Stamina Elixir', () => g.useItem('elixir'));
      use('focus', '🔮 Focus Charm', () => g.useItem('focus'));
      use('bomb', '💥 Fire Bomb', () => g.useItem('bomb'));
      // weapons shop-ish
      const w = document.createElement('div'); w.className = 'inv-item';
      w.innerHTML = `<b>Weapon Rack</b><br><span style="color:#9aa3bd">Better steel = more damage.</span>`;
      for (const wpn of CONFIG.weapons) {
        const b = document.createElement('button'); b.className = 'mbtn';
        const owned = p.weaponId === wpn.id;
        b.textContent = owned ? `✔ ${wpn.name}` : wpn.price ? `${wpn.name} (+${Math.round((wpn.dmgMul - 1) * 100)}%) — ${wpn.price}g` : `${wpn.name} (loot)`;
        b.disabled = owned;
        b.onclick = () => g.buyWeapon(wpn.id);
        w.appendChild(b);
      }
      grid.appendChild(w);
    }
    if (tab === 'skills') {
      const box = this.el('tab-skills'); box.innerHTML = `<p>Level ${p.level} &bull; ${p.xp}/${p.xpNeed()} XP &bull; Active: <b>1</b>=Sunder ✦ <b>2</b>=Wrath ❖</p>`;
      for (const s of CONFIG.skills) {
        const un = p.hasSkill(s.id);
        const d = document.createElement('div'); d.className = 'skill-card' + (un ? '' : ' locked');
        const cd = s.id === 'sunder' ? p.skillCD.sunder : s.id === 'wrath' ? p.skillCD.wrath : 0;
        d.innerHTML = `<b>${s.name}</b> ${un ? '✅' : `🔒 Lv ${s.unlock}`}<br>${s.desc}${s.cd ? `<br>Cooldown: ${s.cd}s ${cd > 0 ? `(ready in ${cd.toFixed(1)}s)` : '(ready)'}` : ''}${s.cost ? ` &bull; Cost: ${s.cost} EN` : ''}`;
        box.appendChild(d);
      }
    }
    if (tab === 'quests') {
      const box = this.el('tab-quests'); box.innerHTML = '';
      const card = (q, done, side) => {
        const d = document.createElement('div'); d.className = 'qcard' + (done ? ' done' : '') + (side ? ' side' : '');
        d.innerHTML = `<b>${done ? '✅' : '◈'} ${q.name}</b> <span style="color:#9aa3bd">[${q.kind}]</span><br>${q.obj}<br><i style="color:#9aa3bd">${q.hint}</i>`;
        box.appendChild(d);
      };
      const mains = (g.questsDoneCache || []).concat([]);
      import('./config.js').then(({ QUESTS }) => {
        box.innerHTML = '';
        const order = ['q1', 'q2', 'q3', 'q4', 'q5'];
        const idx = { q1: 0, q2: 1, q3: 2, q4: 3, q5: 4 }[g.quests.current?.id] ?? 9;
        for (const q of QUESTS.filter((x) => x.kind === 'main')) {
          const n = { q1: 0, q2: 1, q3: 2, q4: 3, q5: 4 }[q.id];
          card(q, g.quests.done || n < idx, false);
        }
        const smap = { s1: g.quests.s1done, s2: g.quests.s2done, s3: g.quests.s3done };
        for (const q of QUESTS.filter((x) => x.kind === 'side')) card(q, smap[q.id], true);
        void mains; void order;
      });
      box.innerHTML = '<p>Loading quests...</p>';
    }
    if (tab === 'stats') {
      this.el('tab-stats').innerHTML = `
        <p><b>${p.weapon.name}</b> ×${p.weapon.dmgMul} &bull; Kills: ${p.kills} &bull; Play time: ${Math.floor(g.playTime / 60)}m</p>
        <div class="set-row"><span>❤ Max HP</span><b>${p.maxHP}</b></div>
        <div class="set-row"><span>🏃 Max Stamina</span><b>${p.maxST}</b></div>
        <div class="set-row"><span>🔮 Max Energy</span><b>${p.maxEN}</b></div>
        <div class="set-row"><span>⚔ Weapon power</span><b>×${p.weapon.dmgMul}</b></div>
        <div class="set-row"><span>🎖 Difficulty</span><b>${g.diff.label}</b></div>
        <div class="set-row"><span>🪙 Gold</span><b>${p.gold}</b></div>`;
    }
    if (tab === 'settings') this.renderSettings();
  }
  renderSettings() {
    const g = this.game, s = g.settings;
    const box = this.el('tab-settings');
    box.innerHTML = `
      <div class="set-row"><span>Graphics quality</span><select id="set-gfx">
        ${['low', 'medium', 'high', 'ultra'].map((v) => `<option ${s.graphics === v ? 'selected' : ''}>${v}</option>`).join('')}
      </select></div>
      <div class="set-row"><span>Music volume</span><input id="set-mus" type="range" min="0" max="100" value="${s.music * 100}"></div>
      <div class="set-row"><span>SFX volume</span><input id="set-sfx" type="range" min="0" max="100" value="${s.sfx * 100}"></div>
      <div class="set-row"><span>Camera sensitivity</span><input id="set-sen" type="range" min="30" max="250" value="${s.sens * 100}"></div>
      <div class="set-row"><span>Camera shake</span><input id="set-shk" type="checkbox" ${s.shake ? 'checked' : ''}></div>
      <div class="set-row"><span>Difficulty</span><select id="set-dif">
        ${['story', 'normal', 'hard'].map((v) => `<option ${g.difficulty === v ? 'selected' : ''}>${v}</option>`).join('')}
      </select></div>
      <div class="set-row"><span>Save</span><button id="set-save" class="mbtn">💾 Save now</button></div>`;
    box.querySelector('#set-gfx').onchange = (e) => g.setGraphics(e.target.value);
    box.querySelector('#set-mus').oninput = (e) => { s.music = e.target.value / 100; audio.setVolumes(s.music * 0.5, s.sfx); g.persistSettings(); };
    box.querySelector('#set-sfx').oninput = (e) => { s.sfx = e.target.value / 100; audio.setVolumes(s.music * 0.5, s.sfx); g.persistSettings(); };
    box.querySelector('#set-sen').oninput = (e) => { s.sens = e.target.value / 100; g.input.camSens = s.sens; g.persistSettings(); };
    box.querySelector('#set-shk').onchange = (e) => { s.shake = e.target.checked; g.persistSettings(); };
    box.querySelector('#set-dif').onchange = (e) => g.setDifficulty(e.target.value);
    box.querySelector('#set-save').onclick = () => { g.saveGame(); };
  }
  showBossWarn(text) {
    const w = this.el('boss-warn');
    w.textContent = '⚠ ' + text + ' ⚠';
    w.classList.remove('hidden');
    setTimeout(() => w.classList.add('hidden'), 3500);
  }
  fade(inOut, ms = 500) {
    const f = this.el('fader');
    f.style.transitionDuration = (ms / 1000) + 's';
    f.style.opacity = inOut ? '1' : '0';
  }
}
