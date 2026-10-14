// Shared math / pooling / misc utilities.
import * as THREE from 'three';

export const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const damp = (a, b, k, dt) => lerp(a, b, 1 - Math.exp(-k * dt));
export const rand = (a = 1, b) => (b === undefined ? Math.random() * a : a + Math.random() * (b - a));
export const randPick = (arr) => arr[(Math.random() * arr.length) | 0];
export const dist2D = (a, b) => Math.hypot(a.x - b.x, a.z - b.z);

export function angleLerp(a, b, t) {
  let d = (b - a) % (Math.PI * 2);
  if (d > Math.PI) d -= Math.PI * 2;
  if (d < -Math.PI) d += Math.PI * 2;
  return a + d * t;
}

// Analytic terrain height shared by world + entities (no raycasts needed).
export function groundHeight(x, z) {
  const d = Math.hypot(x, z);
  let h = Math.sin(x * 0.045) * Math.cos(z * 0.05) * 3.2
        + Math.sin(x * 0.013 + 1.7) * Math.cos(z * 0.017 + 0.6) * 7.0
        + Math.sin(x * 0.11) * Math.sin(z * 0.09) * 0.5;
  // mountain in the north-east (summit arena)
  const m = Math.hypot(x - 120, z + 130);
  h += Math.max(0, 1 - m / 110) * 26;
  // crater flatten for village / temple / summit / camp
  h = flatten(h, x, z, 0, 20, 2.2);        // village
  h = flatten(h, x, z, 90, 0, 3.4);        // camp
  h = flatten(h, x, z, -95, -40, 4.0);     // cave mouth
  h = flatten(h, x, z, -30, 120, 3.0);     // temple
  h = flatten(h, x, z, 120, -130, 30.5);   // summit arena flat top
  // rim mountains at world edge
  const edge = Math.max(Math.abs(x), Math.abs(z));
  if (edge > 175) h += (edge - 175) * 1.4;
  return h;
}
function flatten(h, x, z, cx, cz, target) {
  const d = Math.hypot(x - cx, z - cz);
  const r = 26;
  if (d >= r) return h;
  const t = (Math.cos((d / r) * Math.PI) + 1) / 2;
  return lerp(h, target, t);
}

// Simple fixed-capacity object pool.
export class Pool {
  constructor(create, size) { this.items = []; for (let i = 0; i < size; i++) { const o = create(i); o.visible = false; this.items.push(o); } this.i = 0; }
  next() { const o = this.items[this.i]; this.i = (this.i + 1) % this.items.length; o.visible = true; return o; }
}

// Tiny event bus.
export class Bus {
  constructor() { this.m = new Map(); }
  on(ev, fn) { if (!this.m.has(ev)) this.m.set(ev, []); this.m.get(ev).push(fn); }
  emit(ev, data) { const l = this.m.get(ev); if (l) for (const f of l) f(data); }
}
export const bus = new Bus();

export function makeCanvasTexture(draw, w = 128, h = 128) {
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  draw(c.getContext('2d'), w, h);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
  return t;
}
