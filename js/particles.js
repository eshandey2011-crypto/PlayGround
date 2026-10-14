// Pooled GPU particle system (hits, dust, magic, leaves, campfire) + rain + lightning.
import * as THREE from 'three';
import { rand } from './utils.js';

export class Particles {
  constructor(scene, quality = 1) {
    this.scene = scene; this.quality = quality;
    this.MAX = 2200;
    this.pos = new Float32Array(this.MAX * 3);
    this.col = new Float32Array(this.MAX * 3);
    this.vel = new Float32Array(this.MAX * 3);
    this.life = new Float32Array(this.MAX);
    this.maxLife = new Float32Array(this.MAX);
    this.grav = new Float32Array(this.MAX);
    this.size = new Float32Array(this.MAX);
    this.head = 0; this.active = 0;
    const g = new THREE.BufferGeometry();
    g.setAttribute('position', new THREE.BufferAttribute(this.pos, 3));
    g.setAttribute('color', new THREE.BufferAttribute(this.col, 3));
    // soft round sprite so points render as glowing dots, not squares
    const cv = document.createElement('canvas'); cv.width = cv.height = 64;
    const cx = cv.getContext('2d');
    const grad = cx.createRadialGradient(32, 32, 2, 32, 32, 30);
    grad.addColorStop(0, 'rgba(255,255,255,1)');
    grad.addColorStop(0.4, 'rgba(255,255,255,.8)');
    grad.addColorStop(1, 'rgba(255,255,255,0)');
    cx.fillStyle = grad; cx.fillRect(0, 0, 64, 64);
    const sprite = new THREE.CanvasTexture(cv);
    const m = new THREE.PointsMaterial({ size: 0.3, map: sprite, vertexColors: true, transparent: true, opacity: 0.95, depthWrite: false, blending: THREE.AdditiveBlending, sizeAttenuation: true, alphaTest: 0.01 });
    this.points = new THREE.Points(g, m);
    this.points.frustumCulled = false;
    scene.add(this.points);
    this._c = new THREE.Color();
  }
  spawn(x, y, z, vx, vy, vz, life, color, grav = 0) {
    const i = this.head; this.head = (this.head + 1) % this.MAX;
    this.pos[i * 3] = x; this.pos[i * 3 + 1] = y; this.pos[i * 3 + 2] = z;
    this.vel[i * 3] = vx; this.vel[i * 3 + 1] = vy; this.vel[i * 3 + 2] = vz;
    this.life[i] = life; this.maxLife[i] = life; this.grav[i] = grav;
    this._c.set(color);
    this.col[i * 3] = this._c.r; this.col[i * 3 + 1] = this._c.g; this.col[i * 3 + 2] = this._c.b;
  }
  burst(p, n, opt = {}) {
    n = Math.round(n * this.quality);
    const { spread = 4, up = 4, life = 0.7, color = 0xffcc55, grav = -9 } = opt;
    for (let k = 0; k < n; k++) {
      this.spawn(p.x + rand(-0.3, 0.3), p.y + rand(0, 0.5), p.z + rand(-0.3, 0.3),
        rand(-spread, spread), rand(0, up), rand(-spread, spread), life * rand(0.6, 1.3), color, grav);
    }
  }
  blood(p) { this.burst(p, 14, { color: 0xc01818, spread: 3.5, up: 4.5, life: 0.6 }); }
  spark(p) { this.burst(p, 12, { color: 0xffe08a, spread: 5, up: 3, life: 0.4, grav: -4 }); }
  magic(p, color = 0x66ccff) { this.burst(p, 22, { color, spread: 2.5, up: 5, life: 0.9, grav: 2 }); }
  dust(p) { this.burst(p, 6, { color: 0x9a8a70, spread: 1.5, up: 1.5, life: 0.8, grav: 1 }); }
  levelup(p) { this.burst(p, 40, { color: 0xe8b64c, spread: 3, up: 8, life: 1.2, grav: 3 }); }
  heal(p) { this.burst(p, 16, { color: 0x51ff9a, spread: 2, up: 4, life: 1.0, grav: 3 }); }
  update(dt) {
    const { pos, vel, life, maxLife, grav, col } = this;
    for (let i = 0; i < this.MAX; i++) {
      if (life[i] <= 0) { pos[i * 3 + 1] = -9999; continue; }
      life[i] -= dt;
      vel[i * 3 + 1] += grav[i] * dt;
      pos[i * 3] += vel[i * 3] * dt; pos[i * 3 + 1] += vel[i * 3 + 1] * dt; pos[i * 3 + 2] += vel[i * 3 + 2] * dt;
      if (pos[i * 3 + 1] < 0 && grav[i] < 0) { life[i] = 0; pos[i * 3 + 1] = -9999; continue; }
      const f = Math.max(0, life[i] / maxLife[i]);
      const s = 0.25 + 0.75 * f;
      col[i * 3] *= (0.985); col[i * 3 + 1] *= 0.985; col[i * 3 + 2] *= 0.985;
      void s;
    }
    this.points.geometry.attributes.position.needsUpdate = true;
    this.points.geometry.attributes.color.needsUpdate = true;
  }
}

// Rain streaks + fog + lightning weather controller.
export class Weather {
  constructor(scene, renderer) {
    this.scene = scene; this.renderer = renderer;
    this.state = 'clear';       // clear | rain | storm | fog
    this.intensity = 0; this.target = 0;
    this.timer = 40; this.flash = 0;
    this.N = 2500;
    const g = new THREE.BufferGeometry();
    this.rpos = new Float32Array(this.N * 3);
    for (let i = 0; i < this.N; i++) { this.rpos[i * 3] = rand(-60, 60); this.rpos[i * 3 + 1] = rand(0, 40); this.rpos[i * 3 + 2] = rand(-60, 60); }
    g.setAttribute('position', new THREE.BufferAttribute(this.rpos, 3));
    const rcv = document.createElement('canvas'); rcv.width = 16; rcv.height = 32;
    const rcx = rcv.getContext('2d');
    const rgrad = rcx.createLinearGradient(0, 0, 0, 32);
    rgrad.addColorStop(0, 'rgba(180,200,235,0)');
    rgrad.addColorStop(0.5, 'rgba(180,200,235,.9)');
    rgrad.addColorStop(1, 'rgba(180,200,235,0)');
    rcx.fillStyle = rgrad; rcx.fillRect(6, 0, 4, 32);
    this.rain = new THREE.Points(g, new THREE.PointsMaterial({ color: 0x9ab8dd, size: 0.9, map: new THREE.CanvasTexture(rcv), transparent: true, opacity: 0.0, depthWrite: false }));
    this.rain.frustumCulled = false;
    scene.add(this.rain);
    this.light = new THREE.DirectionalLight(0xbfd0ff, 0);
    scene.add(this.light);
    this.onThunder = null;
  }
  setCount(n) { this.rain.geometry.setDrawRange(0, Math.min(n, this.N)); }
  update(dt, camPos, elapsed) {
    this.timer -= dt;
    if (this.timer <= 0) {
      this.timer = rand(45, 90);
      const roll = Math.random();
      this.state = roll < 0.45 ? 'clear' : roll < 0.7 ? 'rain' : roll < 0.85 ? 'storm' : 'fog';
      this.target = this.state === 'clear' ? 0 : this.state === 'fog' ? 0.25 : this.state === 'rain' ? 0.7 : 1.0;
    }
    this.intensity += (this.target - this.intensity) * Math.min(1, dt * 0.4);
    const k = this.intensity;
    // rain falls around camera
    if (k > 0.02) {
      const draw = this.rain.geometry.drawRange.count;
      for (let i = 0; i < draw; i++) {
        this.rpos[i * 3 + 1] -= dt * (28 + k * 22);
        this.rpos[i * 3] += dt * 3;
        if (this.rpos[i * 3 + 1] < 0) {
          this.rpos[i * 3] = camPos.x + rand(-60, 60);
          this.rpos[i * 3 + 1] = rand(25, 40);
          this.rpos[i * 3 + 2] = camPos.z + rand(-60, 60);
        }
      }
      this.rain.geometry.attributes.position.needsUpdate = true;
    }
    this.rain.material.opacity = k * 0.75;
    this.rain.position.set(0, 0, 0);
    // fog
    const baseFog = this.scene.fog ? this.scene.fog.color : null;
    if (this.scene.fog) {
      const want = this.state === 'fog' ? 0.0075 : this.state === 'storm' ? 0.004 : 0.0022;
      this.scene.fog.density += (want - this.scene.fog.density) * Math.min(1, dt * 0.5);
    }
    void baseFog; void elapsed;
    // lightning
    if (this.state === 'storm' && Math.random() < dt * 0.25) {
      this.flash = 1;
      if (this.onThunder) this.onThunder();
    }
    if (this.flash > 0) {
      this.flash -= dt * 2.2;
      this.light.intensity = Math.max(0, this.flash) * 3;
      this.light.position.copy(camPos).add(new THREE.Vector3(20, 40, 10));
    } else this.light.intensity = 0;
    return { label: this.state, wet: k };
  }
}
