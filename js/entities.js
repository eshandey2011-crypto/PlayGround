// Procedural low-poly humanoid rigs + player/enemy/boss/NPC logic with code-driven animation.
import * as THREE from 'three';
import { CONFIG } from './config.js';
import { clamp, lerp, damp, angleLerp, groundHeight, rand } from './utils.js';
import { audio } from './audio.js';

function limbMat(c) { return new THREE.MeshStandardMaterial({ color: c, roughness: 0.85 }); }

// Build an articulated humanoid; returns {group, parts} with named pivots.
export function buildHumanoid(opt = {}) {
  const { skin = 0xc89878, cloth = 0x3a4a6b, armor = 0x555c6e, scale = 1, sword = true, bulk = 1 } = opt;
  const g = new THREE.Group();
  const parts = {};
  const mat = (m, x, y, z, sx, sy, sz, parent = g) => {
    const ms = new THREE.Mesh(new THREE.BoxGeometry(sx, sy, sz), m);
    ms.position.set(x, y, z); ms.castShadow = true; parent.add(ms); return ms;
  };
  const mSkin = limbMat(skin), mCloth = limbMat(cloth), mArmor = limbMat(armor);
  // torso pivot at hips
  const hips = new THREE.Group(); hips.position.y = 1.02; g.add(hips); parts.hips = hips;
  const torso = mat(mArmor, 0, 0.55, 0, 0.62 * bulk, 0.85, 0.36, hips);
  parts.torso = torso;
  const head = new THREE.Group(); head.position.y = 1.25; hips.add(head); parts.head = head;
  const skull = mat(mSkin, 0, 0.22, 0, 0.4, 0.44, 0.4, head);
  parts.skull = skull;
  // eyes glow for enemies optionally
  // arms: shoulder pivots
  for (const s of [-1, 1]) {
    const sh = new THREE.Group(); sh.position.set(0.42 * s * bulk, 0.92, 0); hips.add(sh);
    const upper = mat(mCloth, 0, -0.28, 0, 0.2, 0.55, 0.2, sh);
    const el = new THREE.Group(); el.position.set(0, -0.55, 0); sh.add(el);
    mat(mSkin, 0, -0.22, 0, 0.17, 0.45, 0.17, el);
    if (s === 1) parts.armR = sh, parts.elbowR = el;
    else parts.armL = sh, parts.elbowL = el;
  }
  // legs: hip pivots
  for (const s of [-1, 1]) {
    const hip = new THREE.Group(); hip.position.set(0.17 * s, 0.05, 0); g.add(hip);
    mat(mCloth, 0, -0.45, 0, 0.24, 0.85, 0.24, hip);
    if (s === 1) parts.legR = hip; else parts.legL = hip;
  }
  // sword in right hand
  if (sword) {
    const blade = new THREE.Mesh(new THREE.BoxGeometry(0.09, 1.25, 0.16),
      new THREE.MeshStandardMaterial({ color: 0xb9c4d6, roughness: 0.25, metalness: 0.85 }));
    blade.position.set(0, -0.95, 0.1);
    const guard = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.07, 0.24), limbMat(0x6a5a2a));
    guard.position.set(0, -0.42, 0.05);
    parts.elbowR.add(blade, guard);
    parts.sword = blade;
    // shield on left arm for player
    const sh2 = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.7, 0.1), limbMat(0x4a5a7a));
    sh2.position.set(-0.1, -0.4, -0.15);
    parts.armL.add(sh2); parts.shield = sh2;
  }
  g.scale.setScalar(scale);
  return { group: g, parts };
}

export class Player {
  constructor(scene, particles) {
    this.scene = scene; this.fx = particles;
    const { group, parts } = buildHumanoid({ cloth: 0x2f4a7a, armor: 0x6a7688, skin: 0xd0a080 });
    // plume
    const plume = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.4, 0.3), limbMat(0xa03030));
    plume.position.set(0, 0.5, -0.05); parts.skull.add(plume);
    this.group = group; this.parts = parts;
    scene.add(group);
    this.pos = group.position;
    this.vel = new THREE.Vector3();
    this.yaw = Math.PI; this.targetYaw = Math.PI;
    this.vy = 0; this.grounded = true;
    this.resetStats();
    // action state
    this.state = 'idle'; this.stateT = 0;
    this.atkStage = 0; this.atkTimer = 0; this.combo = 0; this.comboT = 0;
    this.dodgeT = 0; this.dodgeCD = 0; this.iframes = 0;
    this.blocking = false; this.hurtT = 0; this.stepT = 0;
    this.skillCD = { sunder: 0, wrath: 0 };
    this.lockTarget = null;
    this.animT = 0;
  }
  resetStats() {
    this.level = 1; this.xp = 0; this.gold = 40;
    this.maxHP = CONFIG.player.baseHP; this.maxST = CONFIG.player.baseST; this.maxEN = CONFIG.player.baseEN;
    this.hp = this.maxHP; this.st = this.maxST; this.en = this.maxEN;
    this.weaponId = 'rusty';
    this.inv = { potion: 2, elixir: 1, focus: 1, bomb: 1 };
    this.skillsUnlocked = new Set();
    this.kills = 0; this.deaths = 0;
  }
  get weapon() { return CONFIG.weapons.find((w) => w.id === this.weaponId); }
  get speedMul() { return this.skillsUnlocked.has('windfoot') ? 1.15 : 1; }
  get alive() { return this.hp > 0; }
  xpNeed() { return CONFIG.player.xpCurve(this.level); }
  gainXP(n, ui) {
    this.xp += Math.round(n);
    while (this.xp >= this.xpNeed()) {
      this.xp -= this.xpNeed(); this.level++;
      this.maxHP += CONFIG.player.hpPerLevel; this.maxST += CONFIG.player.stPerLevel; this.maxEN += CONFIG.player.enPerLevel;
      this.hp = this.maxHP; this.st = this.maxST; this.en = this.maxEN;
      this.fx.levelup(this.pos.clone().add(new THREE.Vector3(0, 1, 0)));
      audio.levelup();
      ui.toast(`⬆ LEVEL ${this.level}! HP restored. ${this._unlockForLevel()}`);
    }
  }
  _unlockForLevel() {
    const map = { 2: 'Sunder Strike ✦ (press 1)', 3: 'Iron Will passive', 4: 'Heaven\'s Wrath ❖ (press 2)', 5: 'Wind Foot passive', 6: 'Vampiric Edge passive' };
    const id = { 2: 'sunder', 3: 'ironwill', 4: 'wrath', 5: 'windfoot', 6: 'vampiric' }[this.level];
    if (id) { this.skillsUnlocked.add(id); return 'Unlocked: ' + map[this.level]; }
    return 'Stats increased.';
  }
  hasSkill(id) {
    const need = CONFIG.skills.find((s) => s.id === id)?.unlock || 99;
    return this.level >= need;
  }
  takeDamage(amount, fromPos, opts = {}) {
    if (!this.alive || this.iframes > 0) return 0;
    if (this.dodgeT > 0) return 0;
    let dmg = amount;
    if (this.blocking && !opts.unblockable) {
      const facing = this._facingDot(fromPos);
      if (facing > -0.2) {
        dmg *= (1 - CONFIG.player.blockMitigation);
        if (!this.skillsUnlocked.has('ironwill')) this.st = Math.max(0, this.st - amount * 0.5);
        this.fx.spark(this.pos.clone().add(new THREE.Vector3(0, 1.3, 0)));
        audio.block();
        this.hurtT = 0.15;
        this.hp -= dmg;
        return dmg;
      }
    }
    this.hp -= dmg;
    this.hurtT = 0.35;
    this.iframes = 0.35;
    this.fx.blood(this.pos.clone().add(new THREE.Vector3(0, 1.2, 0)));
    audio.hit();
    return dmg;
  }
  _facingDot(fromPos) {
    const fx = Math.sin(this.yaw), fz = Math.cos(this.yaw);
    const dx = this.pos.x - fromPos.x, dz = this.pos.z - fromPos.z;
    const m = Math.hypot(dx, dz) || 1;
    return (fx * dx / m + fz * dz / m);
  }

  update(dt, input, world, cam) {
    if (!this.alive) { this._animate(dt, 'dead'); return; }
    const P = CONFIG.player;
    this.animT += dt;
    // regen
    this.st = Math.min(this.maxST, this.st + P.staminaRegen * dt * (this.blocking ? 0.4 : 1));
    this.en = Math.min(this.maxEN, this.en + P.energyRegen * dt);
    this.hp = Math.min(this.maxHP, this.hp + P.hpRegen * dt);
    this.iframes = Math.max(0, this.iframes - dt);
    this.dodgeCD = Math.max(0, this.dodgeCD - dt);
    this.hurtT = Math.max(0, this.hurtT - dt);
    this.skillCD.sunder = Math.max(0, this.skillCD.sunder - dt);
    this.skillCD.wrath = Math.max(0, this.skillCD.wrath - dt);
    this.comboT -= dt; if (this.comboT <= 0) this.combo = 0;
    this.blocking = input.blocking() && this.st > 1 && this.dodgeT <= 0;

    const mv = input.getMove();
    const busy = this.atkTimer > 0 || this.dodgeT > 0 || this.hurtT > 0.25;
    // camera-relative move dir
    let wishX = 0, wishZ = 0, moving = false;
    if (!busy && (mv.x !== 0 || mv.y !== 0)) {
      const fwd = cam.yaw;
      const ix = mv.x, iy = mv.y;
      wishX = Math.sin(fwd) * iy + Math.cos(fwd) * ix;
      wishZ = Math.cos(fwd) * iy - Math.sin(fwd) * ix;
      moving = true;
      this.targetYaw = Math.atan2(wishX, wishZ);
    }
    const sprinting = moving && (mv.sprint) && this.st > 2 && !this.blocking && mv.y > 0.1;
    let speed = moving ? (sprinting ? P.runSpeed : P.walkSpeed) * this.speedMul : 0;
    if (this.blocking) speed *= 0.45;
    if (sprinting) this.st = Math.max(0, this.st - 12 * dt);
    this.yaw = angleLerp(this.yaw, this.targetYaw, Math.min(1, dt * 12));

    // dodge
    if (input.consumeAction('dodge') && this.dodgeCD <= 0 && this.dodgeT <= 0 && this.atkTimer <= 0.1) {
      const cost = this.skillsUnlocked.has('windfoot') ? P.dodgeCost * 0.6 : P.dodgeCost;
      if (this.st >= cost * 0.5) {
        this.st = Math.max(0, this.st - cost);
        this.dodgeT = P.dodgeTime; this.dodgeCD = 0.9; this.iframes = Math.max(this.iframes, P.dodgeIframes);
        this.dodgeDir = moving ? { x: wishX, z: wishZ } : { x: Math.sin(this.yaw), z: Math.cos(this.yaw) };
        audio.dodge();
        this.fx.dust(this.pos);
      }
    }
    // jump / gravity
    const gy = groundHeight(this.pos.x, this.pos.z);
    if (input.consumeAction('jump') && this.grounded && this.st > P.jumpCost && this.dodgeT <= 0) {
      this.vy = P.jumpVel; this.grounded = false; this.st -= P.jumpCost;
      audio.jump();
    }
    if (!this.grounded || this.pos.y > gy + 0.05) {
      this.vy -= 24 * dt;
      this.pos.y += this.vy * dt;
      if (this.pos.y <= gy) { this.pos.y = gy; this.vy = 0; this.grounded = true; audio.land(); this.fx.dust(this.pos); }
    } else { this.pos.y = gy; }
    // locomotion integrate
    if (this.dodgeT > 0) {
      this.dodgeT -= dt;
      this.pos.x += this.dodgeDir.x * P.dodgeSpeed * dt;
      this.pos.z += this.dodgeDir.z * P.dodgeSpeed * dt;
      this.targetYaw = Math.atan2(this.dodgeDir.x, this.dodgeDir.z);
      this.yaw = this.targetYaw;
    } else if (!busy) {
      this.pos.x += wishX * speed * dt * (moving ? 1 / Math.max(0.5, Math.hypot(wishX, wishZ)) : 0) * Math.max(1, Math.hypot(wishX, wishZ));
      this.pos.z += wishZ * speed * dt * (moving ? 1 / Math.max(0.5, Math.hypot(wishX, wishZ)) : 0) * Math.max(1, Math.hypot(wishX, wishZ));
    }
    world.resolve(this.pos, 0.6);
    this.pos.y = Math.max(this.pos.y, groundHeight(this.pos.x, this.pos.z));
    if (this.pos.y < CONFIG.killY) { this.hp = 0; }
    // footsteps
    if (moving && this.grounded) {
      this.stepT -= dt * (sprinting ? 2 : 1);
      if (this.stepT <= 0) { this.stepT = 0.42; audio.step(); }
    }
    // attack timing decay
    this.atkTimer = Math.max(0, this.atkTimer - dt);
    this.group.rotation.y = this.yaw;
    // animation
    let mode = 'idle';
    if (!this.grounded) mode = 'jump';
    else if (this.dodgeT > 0) mode = 'dodge';
    else if (this.atkTimer > 0) mode = 'attack';
    else if (this.hurtT > 0) mode = 'hurt';
    else if (this.blocking) mode = 'block';
    else if (moving) mode = sprinting ? 'run' : 'walk';
    this._animate(dt, mode, sprinting);
  }

  _animate(dt, mode, sprint = false) {
    const p = this.parts, t = this.animT;
    const sw = mode === 'walk' ? 7 : 11;
    const amp = mode === 'run' ? 0.85 : 0.6;
    // reset-ish targets
    let legSwing = 0, armSwing = 0, crouch = 0, torsoPitch = 0;
    let armRAtk = null;
    if (mode === 'walk' || mode === 'run') { legSwing = Math.sin(t * sw) * amp; armSwing = Math.sin(t * sw) * amp * 0.8; }
    else if (mode === 'attack') {
      const k = 1 - this.atkTimer / 0.42; // 0..1
      const swing = Math.sin(k * Math.PI);
      armRAtk = -2.4 * swing - 0.4;
      torsoPitch = 0.35 * swing;
      p.elbowR.rotation.x = -0.4;
    } else if (mode === 'block') { p.armL.rotation.x = -1.4; p.armL.rotation.z = 0.5; crouch = 0.12; }
    else if (mode === 'dodge') { crouch = 0.45; torsoPitch = 0.7; }
    else if (mode === 'hurt') { torsoPitch = -0.4; crouch = 0.15; }
    else if (mode === 'jump') { legSwing = 0.5; armSwing = -0.6; }
    else if (mode === 'dead') { this.group.rotation.x = lerp(this.group.rotation.x, -Math.PI / 2, dt * 4); return; }
    else { // idle breathe
      armSwing = Math.sin(t * 1.8) * 0.06; torsoPitch = Math.sin(t * 1.8) * 0.03;
    }
    this.group.rotation.x = 0;
    p.legL.rotation.x = damp(p.legL.rotation.x, legSwing, 18, dt);
    p.legR.rotation.x = damp(p.legR.rotation.x, -legSwing, 18, dt);
    p.armL.rotation.x = damp(p.armL.rotation.x, mode === 'block' ? p.armL.rotation.x : -armSwing, 14, dt);
    if (armRAtk !== null) p.armR.rotation.x = armRAtk;
    else p.armR.rotation.x = damp(p.armR.rotation.x, armSwing, 14, dt);
    p.torso.rotation.x = damp(p.torso.rotation.x, torsoPitch, 12, dt);
    p.hips.position.y = damp(p.hips.position.y, 1.02 - crouch, 12, dt);
    void sprint;
  }
}

// ---------- Enemies ----------
let ENEMY_ID = 0;
export class Enemy {
  constructor(scene, type, x, z, diff) {
    this.id = ENEMY_ID++;
    this.scene = scene;
    this.type = type;
    const cfg = CONFIG.enemies[type];
    this.cfg = cfg;
    this.name = cfg.name;
    const bulk = type === 'brute' || type === 'boss' ? 1.5 : type === 'wolf' ? 0.8 : 1;
    const scale = type === 'boss' ? 1.65 : type === 'brute' ? 1.3 : type === 'wolf' ? 0.85 : 1;
    const { group, parts } = buildHumanoid({ cloth: cfg.color, armor: cfg.color, skin: type === 'wolf' ? 0x555a66 : 0xb08a68, scale, bulk, sword: type !== 'wolf' });
    if (type === 'wolf') { // quadruped-ish tweak: lower + head forward
      parts.hips.position.y = 0.7; group.scale.y *= 0.85;
      const glow = new THREE.Mesh(new THREE.SphereGeometry(0.07, 6, 6), new THREE.MeshBasicMaterial({ color: 0xff2222 }));
      glow.position.set(0, 0.25, 0.22); parts.skull.add(glow);
    }
    if (type === 'boss') {
      const crown = new THREE.Mesh(new THREE.ConeGeometry(0.16, 0.5, 5), limbMat(0x8a1a3a));
      crown.position.set(0, 0.55, 0); parts.skull.add(crown);
      parts.sword.scale.set(1.4, 1.8, 1.2);
      const glow = new THREE.Mesh(new THREE.SphereGeometry(0.08, 6, 6), new THREE.MeshBasicMaterial({ color: 0xff3333 }));
      glow.position.set(-0.1, 0.25, 0.21); parts.skull.add(glow);
      const glow2 = glow.clone(); glow2.position.x = 0.1; parts.skull.add(glow2);
    }
    this.group = group; this.parts = parts;
    group.position.set(x, groundHeight(x, z), z);
    scene.add(group);
    this.pos = group.position;
    this.home = new THREE.Vector3(x, 0, z);
    this.zone = 'wild';
    this.maxHP = Math.round(cfg.hp * diff.enemyHP);
    this.hp = this.maxHP;
    this.dmg = Math.round(cfg.dmg * diff.enemyDmg);
    this.alive = true;
    this.state = 'idle'; this.stateT = rand(2);
    this.atkT = 0; this.atkCD = 0; this.hurtT = 0; this.animT = rand(10);
    this.yaw = rand(Math.PI * 2);
    this.strafeDir = Math.random() < 0.5 ? 1 : -1;
    this.phase = 1;
    this.projectiles = null; // set by game for ranged
  }
  takeDamage(amount, fromPos, fx, opts = {}) {
    if (!this.alive) return 0;
    this.hp -= amount;
    this.hurtT = 0.3;
    fx.blood(this.pos.clone().add(new THREE.Vector3(0, 1.2, 0)));
    // face attacker
    this.yaw = Math.atan2(fromPos.x - this.pos.x, fromPos.z - this.pos.z);
    if (this.hp <= 0) {
      this.alive = false;
      this.state = 'dead'; this.stateT = 0;
      fx.burst(this.pos.clone().add(new THREE.Vector3(0, 1, 0)), 26, { color: 0x881111, spread: 4, up: 6, life: 1 });
      if (this.type === 'boss') audio.roar();
    } else if (this.type === 'boss' && this.phase === 1 && this.hp < this.maxHP * 0.5) {
      this.phase = 2; // enrage
      fx.magic(this.pos.clone().add(new THREE.Vector3(0, 1.5, 0)), 0xff3355);
      audio.roar();
    }
    return amount;
  }
  update(dt, player, world, fx) {
    this.animT += dt;
    if (!this.alive) {
      this.stateT += dt;
      this.group.rotation.x = lerp(this.group.rotation.x, -Math.PI / 2, dt * 5);
      this.group.position.y = Math.max(groundHeight(this.pos.x, this.pos.z) - Math.min(1.2, this.stateT * 0.8), groundHeight(this.pos.x, this.pos.z) - 1.2);
      return this.stateT > 6 ? 'despawn' : null; // game removes far corpses? keep boss corpse
    }
    const cfg = this.cfg;
    this.atkCD = Math.max(0, this.atkCD - dt);
    this.hurtT = Math.max(0, this.hurtT - dt);
    this.atkT = Math.max(0, this.atkT - dt);
    const toPlayer = Math.hypot(player.pos.x - this.pos.x, player.pos.z - this.pos.z);
    const aggro = player.alive ? cfg.aggro * (this.type === 'boss' ? 99 : 1) : 0;
    const alerted = toPlayer < aggro;
    let moving = false, mode = 'idle';
    if (alerted && player.alive) {
      const wantYaw = Math.atan2(player.pos.x - this.pos.x, player.pos.z - this.pos.z);
      this.yaw = angleLerp(this.yaw, wantYaw, Math.min(1, dt * 6));
      const speedMul = this.type === 'boss' && this.phase === 2 ? 1.45 : 1;
      if (this.type === 'ranged' || cfg.behavior === 'ranged') {
        // keep distance, shoot orbs
        if (toPlayer > cfg.attackRange * 0.8) { this._stepToward(player, cfg.speed * speedMul * dt, world); moving = true; }
        else if (toPlayer < 8) { this._stepToward(player, -cfg.speed * 0.7 * dt, world); moving = true; }
        else { // strafe
          const a = this.yaw + Math.PI / 2 * this.strafeDir;
          this.pos.x += Math.sin(a) * cfg.speed * 0.5 * dt; this.pos.z += Math.cos(a) * cfg.speed * 0.5 * dt;
          if (Math.random() < dt * 0.5) this.strafeDir *= -1;
          moving = true;
        }
        if (toPlayer < cfg.attackRange && this.atkCD <= 0) {
          this.atkCD = cfg.atkCd; this.atkT = 0.5;
          if (this.projectiles) this.projectiles.fire(this.pos.clone().add(new THREE.Vector3(0, 1.6, 0)), player, this.dmg, 0xaa55ff);
          audio.skill();
          fx.magic(this.pos.clone().add(new THREE.Vector3(0, 1.6, 0)), 0xaa55ff);
        }
      } else if (toPlayer > cfg.attackRange) {
        let sp = cfg.speed * speedMul;
        if (cfg.behavior === 'circler' && toPlayer < 9) {
          // circle then lunge
          const a = this.yaw + Math.PI / 2 * this.strafeDir;
          this.pos.x += Math.sin(a) * sp * 0.6 * dt + Math.sin(this.yaw) * sp * 0.5 * dt;
          this.pos.z += Math.cos(a) * sp * 0.6 * dt + Math.cos(this.yaw) * sp * 0.5 * dt;
          if (Math.random() < dt * 0.4) this.strafeDir *= -1;
        } else {
          this._stepToward(player, sp * dt, world);
        }
        moving = true;
      } else {
        // in range: attack
        if (this.atkCD <= 0) {
          this.atkCD = cfg.atkCd * (this.type === 'boss' && this.phase === 2 ? 0.65 : 1);
          this.atkT = 0.55;
          this.didHit = false;
          audio.swing();
          // boss slam telegraphs AoE
          if (this.type === 'boss' && Math.random() < 0.4) { this.aoeSlam = 0.55; }
        }
      }
      // melee hit resolution at strike moment
      if (this.atkT > 0.2 && this.atkT < 0.4 && !this.didHit && toPlayer < cfg.attackRange + 0.6) {
        const pd = player.takeDamage(this.dmg * (this.type === 'boss' && this.phase === 2 ? 1.3 : 1), this.pos);
        if (pd > 0) { this.didHit = true; }
      }
      if (this.aoeSlam) {
        this.aoeSlam -= dt;
        if (this.aoeSlam <= 0) {
          this.aoeSlam = 0;
          fx.burst(this.pos.clone(), 30, { color: 0xff5533, spread: 7, up: 4, life: 0.7 });
          audio.heavySwing();
          if (toPlayer < 6) player.takeDamage(this.dmg * 1.2, this.pos, { unblockable: false });
        }
      }
    } else {
      // idle wander around home
      this.stateT -= dt;
      if (this.stateT <= 0) {
        this.stateT = rand(2, 5);
        this.wanderA = rand(Math.PI * 2);
        this.wanderOn = !this.wanderOn;
      }
      if (this.wanderOn) {
        this.yaw = angleLerp(this.yaw, this.wanderA, dt * 2);
        this.pos.x += Math.sin(this.yaw) * cfg.speed * 0.25 * dt;
        this.pos.z += Math.cos(this.yaw) * cfg.speed * 0.25 * dt;
        moving = true;
        if (Math.hypot(this.pos.x - this.home.x, this.pos.z - this.home.z) > 18) {
          this.yaw = Math.atan2(this.home.x - this.pos.x, this.home.z - this.pos.z);
        }
      }
    }
    world.resolve(this.pos, 0.55);
    this.pos.y = groundHeight(this.pos.x, this.pos.z);
    this.group.rotation.y = this.yaw;
    // animate
    if (this.hurtT > 0) mode = 'hurt';
    else if (this.atkT > 0) mode = 'attack';
    else if (moving) mode = 'walk';
    this._animate(dt, mode);
    return null;
  }
  _stepToward(player, amount, world) {
    const dx = player.pos.x - this.pos.x, dz = player.pos.z - this.pos.z;
    const m = Math.hypot(dx, dz) || 1;
    this.pos.x += dx / m * amount; this.pos.z += dz / m * amount;
    void world;
  }
  _animate(dt, mode) {
    const p = this.parts, t = this.animT;
    if (mode === 'walk') {
      const s = Math.sin(t * 9) * 0.7;
      p.legL.rotation.x = s; p.legR.rotation.x = -s;
      p.armL.rotation.x = -s * 0.7; p.armR.rotation.x = s * 0.7;
    } else if (mode === 'attack') {
      const k = 1 - this.atkT / 0.55;
      p.armR.rotation.x = -2.6 * Math.sin(k * Math.PI) - 0.3;
      p.torso.rotation.x = 0.3 * Math.sin(k * Math.PI);
    } else if (mode === 'hurt') {
      p.torso.rotation.x = -0.45; p.armL.rotation.x = -1;
    } else {
      p.legL.rotation.x *= 0.9; p.legR.rotation.x *= 0.9;
      p.armL.rotation.x = Math.sin(t * 2) * 0.08; p.armR.rotation.x = Math.sin(t * 2 + 1) * 0.08;
      p.torso.rotation.x *= 0.9;
    }
  }
}

// ---------- NPCs ----------
export class NPC {
  constructor(scene, name, x, z, cloth, dialogueId, opts = {}) {
    this.name = name; this.dialogueId = dialogueId;
    const { group, parts } = buildHumanoid({ cloth, armor: cloth, skin: 0xc89878, sword: false });
    group.position.set(x, groundHeight(x, z), z);
    group.rotation.y = opts.ry || 0;
    scene.add(group);
    this.group = group; this.parts = parts; this.pos = group.position;
    this.baseY = group.position.y;
    this.t = rand(10);
    // marker (!) billboard
    const cv = document.createElement('canvas'); cv.width = cv.height = 64;
    const cx = cv.getContext('2d');
    cx.font = 'bold 44px serif'; cx.textAlign = 'center'; cx.fillStyle = '#ffd76a';
    cx.strokeStyle = '#000'; cx.lineWidth = 6; cx.strokeText('!', 32, 46); cx.fillText('!', 32, 46);
    const tex = new THREE.CanvasTexture(cv);
    this.marker = new THREE.Sprite(new THREE.SpriteMaterial({ map: tex, depthTest: false, transparent: true }));
    this.marker.scale.setScalar(1.2);
    this.marker.position.set(x, this.baseY + 2.9, z);
    scene.add(this.marker);
    this.hasQuest = true;
  }
  update(dt) {
    this.t += dt;
    this.parts.armL.rotation.x = Math.sin(this.t * 1.5) * 0.1;
    this.parts.armR.rotation.x = Math.sin(this.t * 1.5 + 1) * 0.1;
    this.marker.position.y = this.baseY + 2.9 + Math.sin(this.t * 3) * 0.15;
    this.marker.visible = this.hasQuest;
  }
}

// ---------- Enemy projectiles (pooled) ----------
export class Projectiles {
  constructor(scene) {
    this.scene = scene;
    this.items = [];
    const geo = new THREE.SphereGeometry(0.3, 8, 8);
    for (let i = 0; i < 24; i++) {
      const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: 0xaa55ff }));
      m.visible = false; scene.add(m);
      this.items.push({ mesh: m, vel: new THREE.Vector3(), life: 0, dmg: 0, foe: true });
    }
    this.fx = null;
  }
  fire(from, target, dmg, color = 0xaa55ff) {
    const p = this.items.find((i) => i.life <= 0); if (!p) return;
    p.mesh.visible = true; p.mesh.material.color.set(color);
    p.mesh.position.copy(from);
    const aim = target.pos.clone().add(new THREE.Vector3(0, 1.1, 0));
    p.vel.copy(aim.sub(from).normalize().multiplyScalar(16));
    p.life = 2.2; p.dmg = dmg;
  }
  fireBomb(from, dir, dmg, game) {
    const p = this.items.find((i) => i.life <= 0); if (!p) return;
    p.mesh.visible = true; p.mesh.material.color.set(0xff6a2a);
    p.mesh.scale.setScalar(1.4);
    p.mesh.position.copy(from);
    p.vel.copy(dir).multiplyScalar(14); p.vel.y = 6;
    p.life = 2.0; p.dmg = dmg; p.bomb = true; p.game = game;
  }
  update(dt, player, enemies, fx) {
    for (const p of this.items) {
      if (p.life <= 0) continue;
      p.life -= dt;
      if (p.bomb) p.vel.y -= 18 * dt;
      p.mesh.position.addScaledVector(p.vel, dt);
      const mp = p.mesh.position;
      fx.spark(mp.clone ? mp : mp);
      // hit player
      if (!p.bomb && player.alive && mp.distanceTo(player.pos.clone().add(new THREE.Vector3(0, 1, 0))) < 1.1) {
        player.takeDamage(p.dmg, mp);
        fx.burst(mp, 8, { color: 0xaa55ff, spread: 3, up: 3, life: 0.5 });
        p.life = 0; p.mesh.visible = false; p.mesh.scale.setScalar(1);
        continue;
      }
      if (p.bomb) {
        const gy = groundHeight(mp.x, mp.z);
        let boom = mp.y <= gy + 0.3;
        if (!boom && p.game) for (const e of p.game.enemies) {
          if (e.alive && mp.distanceTo(e.pos.clone().add(new THREE.Vector3(0, 1, 0))) < 1.6) { boom = true; break; }
        }
        if (boom) {
          fx.burst(mp, 30, { color: 0xff6a2a, spread: 6, up: 6, life: 0.8 });
          audio.heavySwing();
          if (p.game) for (const e of p.game.enemies) {
            if (e.alive && mp.distanceTo(e.pos) < 5.5) e.takeDamage(p.dmg, player.pos, fx);
          }
          p.life = 0; p.mesh.visible = false; p.mesh.scale.setScalar(1); p.bomb = false;
          continue;
        }
      }
      if (p.life <= 0 || mp.y < groundHeight(mp.x, mp.z)) { p.life = 0; p.mesh.visible = false; p.mesh.scale.setScalar(1); p.bomb = false; }
    }
  }
}
