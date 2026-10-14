// World: terrain, sky/day-night cycle, locations, props, colliders, pickups, beacons.
import * as THREE from 'three';
import { CONFIG } from './config.js';
import { groundHeight, rand, makeCanvasTexture } from './utils.js';

export const LOC = {
  spawn:   { x: -6, z: 34 },
  village: { x: 0, z: 20 },
  camp:    { x: 90, z: 0 },
  cave:    { x: -95, z: -40 },
  temple:  { x: -30, z: 120 },
  summit:  { x: 120, z: -130 },
  heirloom:{ x: -120, z: 40 },
};

export class World {
  constructor(scene, gfx) {
    this.scene = scene; this.gfx = gfx;
    this.colliders = [];   // {x,z,r}
    this.chests = []; this.herbs = [];
    this.openedChests = new Set(); this.gatheredHerbs = 0;
    this.time = 0.28;      // day fraction (0.25 = dawn)
    this.lamps = [];
    this.beacon = null; this.beaconLight = null;
  }
  addCollider(x, z, r) { this.colliders.push({ x, z, r }); }
  resolve(pos, radius = 0.6) {
    for (const c of this.colliders) {
      const dx = pos.x - c.x, dz = pos.z - c.z;
      const d = Math.hypot(dx, dz), min = c.r + radius;
      if (d < min && d > 0.001) { pos.x = c.x + dx / d * min; pos.z = c.z + dz / d * min; }
    }
    const B = CONFIG.worldSize / 2 - 8;
    pos.x = Math.max(-B, Math.min(B, pos.x));
    pos.z = Math.max(-B, Math.min(B, pos.z));
  }

  build(progress) {
    const tick = (p, m) => progress && progress(p, m);
    tick(0.05, 'Raising terrain...');
    this._terrain();
    tick(0.2, 'Painting sky...');
    this._sky();
    tick(0.32, 'Growing forests...');
    this._forest();
    tick(0.5, 'Raising the village...');
    this._village();
    tick(0.62, 'Pitching bandit camp...');
    this._camp();
    tick(0.72, 'Carving cave & temple...');
    this._cave(); this._temple();
    tick(0.84, 'Crowns of the summit...');
    this._summit(); this._heirloomSite();
    tick(0.92, 'Scattering treasures...');
    this._pickups();
    this._beaconMesh();
    tick(1, 'Ready');
  }

  _terrain() {
    const S = CONFIG.worldSize, SEG = 150;
    const g = new THREE.PlaneGeometry(S, S, SEG, SEG);
    g.rotateX(-Math.PI / 2);
    const p = g.attributes.position;
    const colors = new Float32Array(p.count * 3);
    const cGrass = new THREE.Color(0x3f7a34), cDry = new THREE.Color(0x7a8a3f),
          cRock = new THREE.Color(0x6f6a63), cSand = new THREE.Color(0xb09a6a),
          cSnow = new THREE.Color(0xdfe6ee), tmp = new THREE.Color();
    for (let i = 0; i < p.count; i++) {
      const x = p.getX(i), z = p.getZ(i);
      const h = groundHeight(x, z);
      p.setY(i, h);
      const n = Math.sin(x * 0.05) * Math.cos(z * 0.06);
      tmp.copy(cGrass).lerp(cDry, (n + 1) / 2 * 0.6);
      if (h > 14) tmp.lerp(cRock, Math.min(1, (h - 14) / 10));
      if (h > 30) tmp.lerp(cSnow, Math.min(1, (h - 30) / 8));
      const dv = Math.hypot(x - 0, z - 20);
      if (dv < 30) tmp.lerp(cSand, 0.35 * (1 - dv / 30));
      colors[i * 3] = tmp.r; colors[i * 3 + 1] = tmp.g; colors[i * 3 + 2] = tmp.b;
    }
    g.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    g.computeVertexNormals();
    const m = new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 1, metalness: 0 });
    this.terrain = new THREE.Mesh(g, m);
    this.terrain.receiveShadow = true;
    this.scene.add(this.terrain);
    // water ring / pond near cave + river-ish pond
    const wgeo = new THREE.CircleGeometry(26, 40);
    const wmat = new THREE.MeshStandardMaterial({ color: 0x1e5a8a, transparent: true, opacity: 0.75, roughness: 0.15, metalness: 0.4 });
    this.water = new THREE.Mesh(wgeo, wmat);
    this.water.rotation.x = -Math.PI / 2;
    this.water.position.set(-70, 1.1, -70);
    this.scene.add(this.water);
    const w2 = this.water.clone(); w2.position.set(40, 0.6, 90); w2.scale.setScalar(0.7);
    this.scene.add(w2);
  }

  _sky() {
    this.scene.fog = new THREE.FogExp2(0x9db8d8, 0.0022);
    this.sun = new THREE.DirectionalLight(0xffffff, 2.4);
    this.sun.castShadow = true;
    this.sun.shadow.mapSize.set(1024, 1024);
    this.sun.shadow.camera.left = -60; this.sun.shadow.camera.right = 60;
    this.sun.shadow.camera.top = 60; this.sun.shadow.camera.bottom = -60;
    this.sun.shadow.camera.far = 300; this.sun.shadow.bias = -0.0006;
    this.scene.add(this.sun); this.scene.add(this.sun.target);
    this.hemi = new THREE.HemisphereLight(0xbcd4ff, 0x3a4a2f, 0.7);
    this.scene.add(this.hemi);
    this.amb = new THREE.AmbientLight(0x404860, 0.5);
    this.scene.add(this.amb);
    // stars
    const n = 600, sp = new Float32Array(n * 3);
    for (let i = 0; i < n; i++) {
      const t = rand(Math.PI * 2), ph = rand(0.05, 1.2);
      sp[i * 3] = Math.cos(t) * Math.cos(ph) * 800; sp[i * 3 + 1] = Math.sin(ph) * 800; sp[i * 3 + 2] = Math.sin(t) * Math.cos(ph) * 800;
    }
    const sg = new THREE.BufferGeometry(); sg.setAttribute('position', new THREE.BufferAttribute(sp, 3));
    this.stars = new THREE.Points(sg, new THREE.PointsMaterial({ color: 0xcfd8ff, size: 1.6, sizeAttenuation: false, transparent: true, opacity: 0 }));
    this.stars.frustumCulled = false;
    this.scene.add(this.stars);
    // sun + moon sprites
    this.sunSprite = new THREE.Mesh(new THREE.SphereGeometry(18, 12, 12), new THREE.MeshBasicMaterial({ color: 0xffe9a8, fog: false }));
    this.moonSprite = new THREE.Mesh(new THREE.SphereGeometry(12, 12, 12), new THREE.MeshBasicMaterial({ color: 0xdde6ff, fog: false }));
    this.scene.add(this.sunSprite, this.moonSprite);
  }

  updateSky(dt, camPos) {
    this.time = (this.time + dt / CONFIG.dayLength) % 1;
    const t = this.time, ang = t * Math.PI * 2 - Math.PI / 2;
    const elev = Math.sin(ang); // -1..1
    const day = Math.max(0, Math.min(1, elev * 2 + 0.25));
    const dusk = Math.max(0, 1 - Math.abs(elev) * 3) * (elev > -0.15 ? 1 : 0);
    const night = 1 - Math.max(0, Math.min(1, elev * 3 + 0.6));
    const sunDir = new THREE.Vector3(Math.cos(ang), Math.sin(ang), 0.35).normalize();
    this.sun.position.copy(camPos).addScaledVector(sunDir, 120);
    this.sun.target.position.copy(camPos);
    this.sun.intensity = 0.12 + day * 2.3;
    this.sun.color.setHSL(0.1 + day * 0.035 - dusk * 0.06, 0.5, 0.5 + day * 0.28);
    this.hemi.intensity = 0.24 + day * 0.55;
    this.amb.intensity = 0.38 + day * 0.28;
    // sky color via fog + background
    const sky = new THREE.Color().setHSL(0.6 - dusk * 0.5, 0.55, 0.06 + day * 0.5 - night * 0.02 + dusk * 0.12);
    this.scene.background = sky;
    this.scene.fog.color.copy(sky).lerp(new THREE.Color(0x9db8d8), day * 0.5);
    this.stars.material.opacity = night * 0.9;
    this.stars.position.copy(camPos);
    this.sunSprite.position.copy(camPos).addScaledVector(sunDir, 700);
    this.sunSprite.material.color.setHSL(0.12 - dusk * 0.08, 0.9, 0.6 + day * 0.15);
    this.moonSprite.position.copy(camPos).addScaledVector(sunDir.clone().negate(), 700);
    // lamps at night
    const lampOn = night > 0.35;
    for (const l of this.lamps) l.intensity = lampOn ? l.userData.base : 0;
    const hh = Math.floor(t * 24), mm = Math.floor((t * 24 % 1) * 60);
    return { hh, mm, night, day, isNight: night > 0.5 };
  }

  // ---- vegetation ----
  _forest() {
    // instanced trunks + canopies for perf (LOD-ish via count by gfx)
    const trunkG = new THREE.CylinderGeometry(0.35, 0.55, 4.4, 6);
    const trunkM = new THREE.MeshStandardMaterial({ color: 0x5a4028, roughness: 1 });
    const canG = new THREE.ConeGeometry(2.6, 6.5, 7);
    const canM = new THREE.MeshStandardMaterial({ color: 0x2c5a2a, roughness: 1 });
    const N = 340;
    this.trunks = new THREE.InstancedMesh(trunkG, trunkM, N);
    this.cans = new THREE.InstancedMesh(canG, canM, N);
    this.trunks.castShadow = this.cans.castShadow = true;
    const d = new THREE.Object3D();
    let placed = 0, guard = 0;
    this.treePos = [];
    while (placed < N && guard++ < 4000) {
      const x = rand(-190, 190), z = rand(-190, 190);
      if (this._nearSite(x, z)) continue;
      const h = groundHeight(x, z);
      if (h > 20 || h < 0.5) continue;
      d.position.set(x, h + 2, z);
      d.rotation.y = rand(Math.PI * 2);
      const s = rand(0.7, 1.5); d.scale.set(s, s, s); d.updateMatrix();
      this.trunks.setMatrixAt(placed, d.matrix);
      d.position.y = h + 4.4 * s + 2.4 * s; d.updateMatrix();
      this.cans.setMatrixAt(placed, d.matrix);
      if (placed % 2 === 0) this.addCollider(x, z, 0.7);
      this.treePos.push({ x, z });
      placed++;
    }
    this.trunks.count = this.cans.count = placed;
    this.treeTotal = placed;
    this.scene.add(this.trunks, this.cans);
    // grass tufts (instanced crosses) — cheap
    const gg = new THREE.ConeGeometry(0.22, 0.9, 4);
    const gm = new THREE.MeshStandardMaterial({ color: 0x4d8a3a, roughness: 1 });
    this.grass = new THREE.InstancedMesh(gg, gm, 4000);
    let g2 = 0; guard = 0;
    while (g2 < 4000 && guard++ < 20000) {
      const x = rand(-190, 190), z = rand(-190, 190);
      if (this._nearSite(x, z)) continue;
      const h = groundHeight(x, z);
      if (h > 16 || h < 0.5) continue;
      d.position.set(x, h + 0.4, z); d.scale.setScalar(rand(0.7, 1.6)); d.rotation.y = rand(6); d.updateMatrix();
      this.grass.setMatrixAt(g2++, d.matrix);
    }
    this.grassTotal = g2;
    this.scene.add(this.grass);
    // rocks (dodecahedrons)
    const rg = new THREE.DodecahedronGeometry(1.1, 0);
    const rm = new THREE.MeshStandardMaterial({ color: 0x77716a, roughness: 1, flatShading: true });
    this.rocks = new THREE.InstancedMesh(rg, rm, 120);
    for (let i = 0; i < 120; i++) {
      const x = rand(-190, 190), z = rand(-190, 190);
      d.position.set(x, groundHeight(x, z) + 0.3, z);
      d.scale.set(rand(0.5, 2.4), rand(0.4, 1.6), rand(0.5, 2.4));
      d.rotation.set(rand(3), rand(3), rand(3)); d.updateMatrix();
      this.rocks.setMatrixAt(i, d.matrix);
      if (i % 3 === 0) this.addCollider(x, z, 1.2);
    }
    this.rocks.castShadow = true;
    this.scene.add(this.rocks);
  }
  _nearSite(x, z) {
    for (const k of Object.values(LOC)) { if (Math.hypot(x - k.x, z - k.z) < 30) return true; }
    return false;
  }
  applyGraphics(gfx) {
    this.gfx = gfx;
    const t = Math.min(this.treeTotal, gfx.trees);
    this.trunks.count = t; this.cans.count = t;
    this.grass.count = Math.min(this.grassTotal, gfx.grass);
    this.sun.castShadow = gfx.shadows;
    this.sun.shadow.mapSize.set(gfx.shadowSize, gfx.shadowSize);
    if (this.sun.shadow.map) { this.sun.shadow.map.dispose(); this.sun.shadow.map = null; }
    const vd = gfx.viewDist;
    this.scene.fog.density = Math.max(this.scene.fog.density, 1 / (vd * 2.2));
  }

  _mat(color, opt = {}) { return new THREE.MeshStandardMaterial({ color, roughness: 0.9, ...opt }); }

  _house(x, z, ry, w = 6, dep = 5, wallC = 0xb59a72) {
    const g = new THREE.Group();
    const h = groundHeight(x, z);
    const walls = new THREE.Mesh(new THREE.BoxGeometry(w, 3.2, dep), this._mat(wallC));
    walls.position.y = 1.6; walls.castShadow = walls.receiveShadow = true;
    const roof = new THREE.Mesh(new THREE.ConeGeometry(Math.max(w, dep) * 0.85, 2.4, 4), this._mat(0x7a3a26));
    roof.position.y = 4.4; roof.rotation.y = Math.PI / 4; roof.castShadow = true;
    const door = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 2.2), this._mat(0x3a2415));
    door.position.set(0, 1.1, dep / 2 + 0.01);
    const lampM = new THREE.Mesh(new THREE.SphereGeometry(0.18, 8, 8), new THREE.MeshBasicMaterial({ color: 0xffd27a }));
    lampM.position.set(w / 2 - 0.5, 2.6, dep / 2 + 0.3);
    const pl = new THREE.PointLight(0xffb85a, 0, 22, 1.8);
    pl.position.copy(lampM.position); pl.userData.base = 14; this.lamps.push(pl);
    g.add(walls, roof, door, lampM, pl);
    g.position.set(x, h, z); g.rotation.y = ry;
    this.scene.add(g);
    this.addCollider(x, z, Math.hypot(w, dep) / 2 + 0.9);
    return g;
  }

  _village() {
    const v = LOC.village;
    this._house(v.x - 8, v.z - 4, 0.4);
    this._house(v.x + 8, v.z - 6, -0.5);
    this._house(v.x - 2, v.z + 12, 0.1, 7, 6, 0xa8906a);
    this._house(v.x + 12, v.z + 8, 1.2, 5, 5);
    // well
    const wx = v.x, wz = v.z - 1;
    const well = new THREE.Mesh(new THREE.CylinderGeometry(1.4, 1.5, 1.2, 10, 1, true), this._mat(0x8a8a8a, { side: THREE.DoubleSide }));
    well.position.set(wx, groundHeight(wx, wz) + 0.6, wz); well.castShadow = true;
    const wroof = new THREE.Mesh(new THREE.ConeGeometry(2, 1.4, 6), this._mat(0x7a3a26));
    wroof.position.set(wx, groundHeight(wx, wz) + 3.4, wz);
    this.scene.add(well, wroof);
    this.addCollider(wx, wz, 1.8);
    // palisade torches
    for (let i = 0; i < 6; i++) {
      const a = i / 6 * Math.PI * 2;
      const x = v.x + Math.cos(a) * 24, z = v.z + Math.sin(a) * 24;
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.14, 3, 6), this._mat(0x4a3220));
      pole.position.set(x, groundHeight(x, z) + 1.5, z);
      const fl = new THREE.Mesh(new THREE.SphereGeometry(0.22, 8, 8), new THREE.MeshBasicMaterial({ color: 0xffc46a }));
      fl.position.set(x, groundHeight(x, z) + 3.1, z);
      const pl = new THREE.PointLight(0xff9a3a, 0, 20, 1.8);
      pl.position.copy(fl.position); pl.userData.base = 10; this.lamps.push(pl);
      this.scene.add(pole, fl, pl);
    }
    // market stall
    const sx = v.x - 12, sz = v.z + 6;
    const stall = new THREE.Mesh(new THREE.BoxGeometry(3, 1.1, 2), this._mat(0x6a4a2a));
    stall.position.set(sx, groundHeight(sx, sz) + 0.55, sz); stall.castShadow = true;
    const awn = new THREE.Mesh(new THREE.BoxGeometry(3.6, 0.15, 2.6), this._mat(0xa03a3a));
    awn.position.set(sx, groundHeight(sx, sz) + 2.4, sz);
    this.scene.add(stall, awn);
  }

  _camp() {
    const c = LOC.camp;
    // tents
    for (let i = 0; i < 4; i++) {
      const x = c.x + rand(-10, 10), z = c.z + rand(-10, 10);
      const tent = new THREE.Mesh(new THREE.ConeGeometry(2.4, 3.4, 6), this._mat(i % 2 ? 0x6a5a48 : 0x54443a));
      tent.position.set(x, groundHeight(x, z) + 1.7, z); tent.castShadow = true;
      this.scene.add(tent);
      this.addCollider(x, z, 2.2);
    }
    // campfire
    const fy = groundHeight(c.x, c.z);
    const stones = new THREE.Mesh(new THREE.TorusGeometry(1.1, 0.25, 6, 12), this._mat(0x555555));
    stones.rotation.x = Math.PI / 2; stones.position.set(c.x, fy + 0.15, c.z);
    const logs = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.18, 2.4, 6), this._mat(0x3a2818));
    logs.rotation.z = Math.PI / 2.3; logs.position.set(c.x, fy + 0.35, c.z);
    const fire = new THREE.Mesh(new THREE.ConeGeometry(0.7, 1.6, 7), new THREE.MeshBasicMaterial({ color: 0xff7a2a, transparent: true, opacity: 0.9 }));
    fire.position.set(c.x, fy + 1.0, c.z); fire.name = 'campfire';
    const fl = new THREE.PointLight(0xff7a2a, 20, 30, 1.7);
    fl.position.set(c.x, fy + 2, c.z);
    this.campFire = fire; this.campLight = fl;
    this.scene.add(stones, logs, fire, fl);
    // cage / loot pile
    const loot = new THREE.Mesh(new THREE.BoxGeometry(1.6, 1.2, 1.6), this._mat(0x7a5a2a));
    loot.position.set(c.x + 6, groundHeight(c.x + 6, c.z + 5) + 0.6, c.z + 5); loot.castShadow = true;
    this.scene.add(loot);
    // banner poles
    for (let i = 0; i < 3; i++) {
      const x = c.x + rand(-14, 14), z = c.z + rand(-14, 14);
      const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, 5, 6), this._mat(0x333333));
      pole.position.set(x, groundHeight(x, z) + 2.5, z);
      const flag = new THREE.Mesh(new THREE.PlaneGeometry(1.6, 1), this._mat(0x8a1a1a, { side: THREE.DoubleSide }));
      flag.position.set(x + 0.8, groundHeight(x, z) + 4.2, z);
      this.scene.add(pole, flag);
    }
  }

  _cave() {
    const c = LOC.cave;
    const y = groundHeight(c.x, c.z);
    // rock arch mouth
    const rockM = this._mat(0x4a4a52, { flatShading: true });
    for (let i = 0; i < 9; i++) {
      const a = (i / 8 - 0.5) * Math.PI;
      const x = c.x + Math.sin(a) * 9, z = c.z + Math.cos(a) * 3 - 2;
      const r = new THREE.Mesh(new THREE.DodecahedronGeometry(rand(2.5, 4.5), 0), rockM);
      r.position.set(x, y + rand(1, 5), z); r.castShadow = true;
      this.scene.add(r);
      this.addCollider(x, z, 2.6);
    }
    // glowing crystals inside
    for (let i = 0; i < 7; i++) {
      const x = c.x + rand(-6, 6), z = c.z - rand(2, 9);
      const cr = new THREE.Mesh(new THREE.OctahedronGeometry(rand(0.4, 0.9), 0),
        new THREE.MeshStandardMaterial({ color: 0x44ddff, emissive: 0x2288cc, emissiveIntensity: 1.4 }));
      cr.position.set(x, groundHeight(x, z) + 0.7, z);
      this.scene.add(cr);
    }
    const cl = new THREE.PointLight(0x44ccff, 18, 26, 1.8);
    cl.position.set(c.x, y + 3, c.z - 5);
    this.scene.add(cl);
    // key altar
    const ax = c.x, az = c.z - 6;
    const altar = new THREE.Mesh(new THREE.BoxGeometry(1.6, 1.1, 1.6), this._mat(0x6a6a7a));
    altar.position.set(ax, groundHeight(ax, az) + 0.55, az); altar.castShadow = true;
    this.scene.add(altar);
    this.keyAltar = new THREE.Vector3(ax, groundHeight(ax, az) + 1.4, az);
    const keyGlow = new THREE.Mesh(new THREE.OctahedronGeometry(0.45, 0),
      new THREE.MeshStandardMaterial({ color: 0xffd76a, emissive: 0xcc8a1a, emissiveIntensity: 2 }));
    keyGlow.position.copy(this.keyAltar); keyGlow.name = 'templeKey';
    this.scene.add(keyGlow);
    this.keyMesh = keyGlow;
  }

  _temple() {
    const t = LOC.temple;
    const y = groundHeight(t.x, t.z);
    const stone = this._mat(0x9a917f), dark = this._mat(0x5a5348);
    // platform
    const plat = new THREE.Mesh(new THREE.BoxGeometry(26, 1.2, 26), dark);
    plat.position.set(t.x, y + 0.2, t.z); plat.receiveShadow = true;
    this.scene.add(plat);
    // pillars
    for (let i = 0; i < 8; i++) {
      const a = i / 8 * Math.PI * 2;
      const x = t.x + Math.cos(a) * 10, z = t.z + Math.sin(a) * 10;
      const p = new THREE.Mesh(new THREE.CylinderGeometry(0.9, 1.1, 9, 8), stone);
      p.position.set(x, y + 5, z); p.castShadow = true;
      const cap = new THREE.Mesh(new THREE.BoxGeometry(2.6, 0.8, 2.6), dark);
      cap.position.set(x, y + 9.6, z);
      this.scene.add(p, cap);
      this.addCollider(x, z, 1.4);
    }
    // seal door (opens with key)
    const seal = new THREE.Mesh(new THREE.BoxGeometry(6, 7, 1), this._mat(0x3a3f5a));
    seal.position.set(t.x, y + 4, t.z - 12.4);
    const rune = new THREE.Mesh(new THREE.PlaneGeometry(3, 3),
      new THREE.MeshBasicMaterial({ color: 0x66ccff, transparent: true, opacity: 0.85 }));
    rune.position.set(t.x, y + 4.2, t.z - 11.8);
    this.scene.add(seal, rune);
    this.seal = seal; this.sealRune = rune;
    this.sealPos = new THREE.Vector3(t.x, y + 1, t.z - 10);
    // inner sanctum glow
    const sl = new THREE.PointLight(0x8877ff, 12, 30, 1.8);
    sl.position.set(t.x, y + 5, t.z);
    this.scene.add(sl);
    // guardian braziers
    for (const sx of [-6, 6]) {
      const b = new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.4, 1.4, 8), dark);
      b.position.set(t.x + sx, y + 1.2, t.z - 6);
      const f = new THREE.Mesh(new THREE.SphereGeometry(0.35, 8, 8), new THREE.MeshBasicMaterial({ color: 0xff9a3a }));
      f.position.set(t.x + sx, y + 2.2, t.z - 6);
      const pl = new THREE.PointLight(0xff8a3a, 8, 16, 1.8);
      pl.position.copy(f.position);
      this.scene.add(b, f, pl);
    }
  }

  _summit() {
    const s = LOC.summit;
    const y = groundHeight(s.x, s.z);
    // arena ring of obsidian pillars + runes
    for (let i = 0; i < 10; i++) {
      const a = i / 10 * Math.PI * 2;
      const x = s.x + Math.cos(a) * 16, z = s.z + Math.sin(a) * 16;
      const p = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 1.2, rand(5, 8), 6), this._mat(0x1a1420, { flatShading: true }));
      p.position.set(x, groundHeight(x, z) + 3, z); p.castShadow = true;
      this.scene.add(p);
      this.addCollider(x, z, 1.3);
    }
    const disc = new THREE.Mesh(new THREE.CircleGeometry(13, 32),
      new THREE.MeshStandardMaterial({ color: 0x2a2030, roughness: 0.8 }));
    disc.rotation.x = -Math.PI / 2; disc.position.set(s.x, y + 0.65, s.z);
    this.scene.add(disc);
    const bl = new THREE.PointLight(0xaa3355, 14, 40, 1.8);
    bl.position.set(s.x, y + 6, s.z);
    this.scene.add(bl);
  }

  _heirloomSite() {
    const hsite = LOC.heirloom;
    // mossy ruins: broken pillars
    for (let i = 0; i < 5; i++) {
      const x = hsite.x + rand(-8, 8), z = hsite.z + rand(-8, 8);
      const p = new THREE.Mesh(new THREE.CylinderGeometry(0.8, 1, rand(1.5, 4), 7), this._mat(0x7a8a6a));
      p.position.set(x, groundHeight(x, z) + 1, z);
      p.rotation.z = rand(-0.35, 0.35); p.castShadow = true;
      this.scene.add(p);
      this.addCollider(x, z, 1.1);
    }
  }

  _pickups() {
    // chests: camp loot, heirloom, temple reward, summit cache, hidden cave cache
    this._chest(LOC.camp.x + 6, LOC.camp.z - 7, 'campLoot', 120);
    this._chest(LOC.heirloom.x, LOC.heirloom.z, 'heirloom', 150, true);
    this._chest(LOC.temple.x + 8, LOC.temple.z + 6, 'templeCache', 200);
    this._chest(-60, 60, 'hidden', 180, true);
    this._chest(60, -60, 'hidden2', 150, true);
    // moonherbs (glowing flowers)
    const herbSpots = [[-70, -60], [-80, -50], [30, 80], [-40, 100], [50, 40], [-100, 10], [20, -40], [-20, -80]];
    for (const [x, z] of herbSpots) this._herb(x + rand(-3, 3), z + rand(-3, 3));
  }
  _chest(x, z, id, gold, hidden = false) {
    const y = groundHeight(x, z);
    const g = new THREE.Group();
    const box = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.8, 1), this._mat(0x6a4a22));
    box.position.y = 0.4; box.castShadow = true;
    const lid = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.35, 1), this._mat(0x8a622e));
    lid.position.y = 0.95;
    const glow = new THREE.Mesh(new THREE.SphereGeometry(0.16, 8, 8), new THREE.MeshBasicMaterial({ color: 0xffd76a }));
    glow.position.y = 1.4;
    g.add(box, lid, glow);
    g.position.set(x, y, z);
    g.userData = { id, gold, hidden, lid };
    this.scene.add(g);
    this.chests.push(g);
  }
  _herb(x, z) {
    const y = groundHeight(x, z);
    const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 0.8, 5), this._mat(0x3a7a3a));
    stem.position.set(x, y + 0.4, z);
    const bloom = new THREE.Mesh(new THREE.SphereGeometry(0.28, 8, 8),
      new THREE.MeshStandardMaterial({ color: 0x66ffee, emissive: 0x22ccaa, emissiveIntensity: 2 }));
    bloom.position.set(x, y + 0.9, z);
    const grp = new THREE.Group(); grp.add(stem, bloom);
    grp.userData.bloom = bloom;
    this.scene.add(grp);
    this.herbs.push(grp);
  }

  _beaconMesh() {
    const g = new THREE.CylinderGeometry(1.2, 1.6, 60, 10, 1, true);
    const m = new THREE.MeshBasicMaterial({ color: 0xffc94a, transparent: true, opacity: 0.35, side: THREE.DoubleSide, depthWrite: false, fog: false });
    this.beacon = new THREE.Mesh(g, m);
    this.beacon.position.set(0, 30, 20);
    this.scene.add(this.beacon);
  }
  setBeacon(x, z) {
    if (x === null || x === undefined) { this.beacon.visible = false; return; }
    this.beacon.visible = true;
    this.beacon.position.set(x, groundHeight(x, z) + 28, z);
  }
  update(dt, elapsed) {
    if (this.campFire) {
      this.campFire.scale.y = 1 + Math.sin(elapsed * 9) * 0.15;
      this.campLight.intensity = 18 + Math.sin(elapsed * 11) * 4;
    }
    if (this.keyMesh) { this.keyMesh.rotation.y += dt * 2; this.keyMesh.position.y += Math.sin(elapsed * 3) * dt * 0.6; }
    if (this.beacon.visible) this.beacon.rotation.y += dt * 0.8;
    for (const h of this.herbs) { if (h.visible) h.userData.bloom.scale.setScalar(1 + Math.sin(elapsed * 4 + h.position.x) * 0.15); }
  }
}
