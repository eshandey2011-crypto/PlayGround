// Game orchestrator: renderer, camera, spawning, interactions, main loop, optimization.
import * as THREE from 'three';
import { CONFIG, DIALOGUES } from './config.js';
import { groundHeight, clamp } from './utils.js';
import { World, LOC } from './world.js';
import { Player, Enemy, NPC, Projectiles } from './entities.js';
import { Particles, Weather } from './particles.js';
import { Combat } from './combat.js';
import { Quests } from './quests.js';
import { UI } from './ui.js';
import { Input } from './input.js';
import { audio } from './audio.js';
import { SaveSystem } from './save.js';

export class Game {
  constructor() {
    this.renderer = null; this.scene = null; this.camera = null;
    this.player = null; this.enemies = []; this.npcs = [];
    this.paused = true; this.dead = false; this.won = false;
    this.difficulty = 'normal';
    this.settings = { graphics: 'medium', music: 0.7, sfx: 0.9, sens: 1.0, shake: true };
    this.playTime = 0; this.hitstop = 0; this.shake = 0;
    this.bossActive = false; this.bossSpawned = false;
    this.skyInfo = null;
    const saved = SaveSystem.loadSettings();
    if (saved) Object.assign(this.settings, saved);
  }
  get diff() { return CONFIG.difficulties[this.difficulty]; }

  async init(progress) {
    const canvas = document.getElementById('game-canvas');
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x87a5cc);
    this.camera = new THREE.PerspectiveCamera(58, innerWidth / innerHeight, 0.1, 900);
    this.input = new Input();
    this.input.camSens = this.settings.sens;
    this.input.attach(canvas);
    addEventListener('resize', () => {
      this.camera.aspect = innerWidth / innerHeight;
      this.camera.updateProjectionMatrix();
      this.renderer.setSize(innerWidth, innerHeight);
    });
    this.renderer.setSize(innerWidth, innerHeight);

    this.world = new World(this.scene, CONFIG.graphics[this.settings.graphics]);
    this.world.build(progress);
    this.fx = new Particles(this.scene, CONFIG.graphics[this.settings.graphics].particles);
    this.weather = new Weather(this.scene, this.renderer);
    this.weather.setCount(CONFIG.graphics[this.settings.graphics].rain);
    this.weather.onThunder = () => audio.thunder();
    this.player = new Player(this.scene, this.fx);
    this.combat = new Combat(this.player, this.fx);
    this.quests = new Quests(this);
    this.ui = new UI(this);
    this.projectiles = new Projectiles(this.scene);
    for (const e of this.enemies) e.projectiles = this.projectiles;

    this._spawnNPCs();
    this._spawnEnemies();
    this.setGraphics(this.settings.graphics, true);
    audio.setVolumes(this.settings.music * 0.5, this.settings.sfx);
    // camera state
    this.cam = { yaw: Math.PI, pitch: 0.32, dist: 8.5 };
    this._bindMenus();
    this._loop = this._loop.bind(this);
  }

  // ---------- spawning ----------
  _spawnNPCs() {
    const v = LOC.village;
    this.npcs.push(new NPC(this.scene, 'Elder Maren', v.x - 1, v.z + 3, 0x7a2a3a, 'elder', { ry: 2.6 }));
    this.npcs.push(new NPC(this.scene, 'Healer Sera', v.x + 6, v.z + 2, 0x2a7a5a, 'healer', { ry: -1.2 }));
    this.npcs.push(new NPC(this.scene, 'Smith Dain', v.x - 11, v.z + 3, 0x5a5a6a, 'smith', { ry: 1.4 }));
  }
  _spawnEnemies() {
    const D = this.diff;
    const add = (type, x, z, zone) => {
      const e = new Enemy(this.scene, type, x, z, D);
      e.zone = zone; e.projectiles = this.projectiles;
      this.enemies.push(e); return e;
    };
    // bandit camp (quest q2): 2 scouts + 2 brutes
    add('scout', LOC.camp.x - 6, LOC.camp.z + 4, 'camp');
    add('scout', LOC.camp.x + 8, LOC.camp.z - 2, 'camp');
    add('brute', LOC.camp.x + 2, LOC.camp.z + 8, 'camp');
    add('brute', LOC.camp.x - 4, LOC.camp.z - 8, 'camp');
    add('shaman', LOC.camp.x + 12, LOC.camp.z + 6, 'camp');
    // wolves in wilds / near cave
    const wolfSpots = [[-60, -20], [-80, -60], [-40, -50], [40, -30], [-120, 10], [20, 60], [60, 60], [-50, 60], [100, 50], [30, -90]];
    for (const [x, z] of wolfSpots) add('wolf', x, z, 'wild');
    // cave guards
    add('wolf', LOC.cave.x + 8, LOC.cave.z + 6, 'wild');
    add('scout', LOC.cave.x - 10, LOC.cave.z + 2, 'wild');
    // temple approach
    add('shaman', LOC.temple.x - 12, LOC.temple.z - 8, 'wild');
    add('brute', LOC.temple.x + 12, LOC.temple.z - 4, 'wild');
    add('scout', LOC.temple.x + 4, LOC.temple.z + 16, 'wild');
    // summit approach
    add('brute', 100, -100, 'wild');
    add('shaman', 110, -115, 'wild');
  }
  spawnTempleGuardian() {
    if (this.templeGuardian) return;
    const t = LOC.temple;
    const e = new Enemy(this.scene, 'brute', t.x, t.z + 2, this.diff);
    e.zone = 'temple'; e.projectiles = this.projectiles;
    e.name = 'Temple Guardian'; e.maxHP = Math.round(e.maxHP * 1.6); e.hp = e.maxHP;
    e.group.scale.multiplyScalar(1.25);
    this.enemies.push(e);
    this.templeGuardian = e;
    this.ui.showBossWarn('THE TEMPLE GUARDIAN RISES');
    audio.roar();
  }
  spawnBoss() {
    if (this.bossSpawned) return;
    this.bossSpawned = true; this.bossActive = true;
    const s = LOC.summit;
    const e = new Enemy(this.scene, 'boss', s.x, s.z - 6, this.diff);
    e.zone = 'summit'; e.projectiles = this.projectiles;
    this.enemies.push(e);
    this.ui.showBossWarn('THE SHADOW WARLORD AWAKENS');
    audio.roar();
  }

  nearestEnemy(maxD = 30) {
    let best = null, bd = maxD;
    for (const e of this.enemies) {
      if (!e.alive) continue;
      const d = Math.hypot(e.pos.x - this.player.pos.x, e.pos.z - this.player.pos.z);
      if (d < bd) { bd = d; best = e; }
    }
    return best;
  }
  onEnemyHit(e, dmg) {
    this.shake = Math.min(0.5, this.shake + (dmg > 30 ? 0.25 : 0.08));
    audio.combatMode = true;
    clearTimeout(this._calmT);
    this._calmT = setTimeout(() => { audio.combatMode = false; }, 6000);
    if (!e.alive) this._onKill(e);
  }
  _onKill(e) {
    const p = this.player;
    p.kills++;
    p.gainXP(Math.round(e.cfg.xp * this.diff.xp), this.ui);
    const gold = 8 + Math.floor(Math.random() * 20) + (e.type === 'brute' ? 25 : 0);
    p.gold += gold;
    this.ui.damageNumber(e.pos, `+${gold}g`, 'heal');
    this.quests.advance('kill', { zone: e.zone, type: e.type });
    // camp weapon reward
    if (this.quests.campKills === 4 && p.weaponId === 'rusty') {
      p.weaponId = 'iron';
      this.ui.toast('🗡 Looted: IRON FANG (+25% damage)! Check Inventory.');
    }
    if (e === this.templeGuardian) { /* handled via kill event zone temple */ }
    if (e.type === 'boss') { /* victory via quests */ }
  }

  // ---------- items / shop ----------
  useItem(id) {
    const p = this.player;
    if ((p.inv[id] || 0) <= 0) { this.ui.toast('None left!'); return; }
    if (id === 'potion') {
      if (p.hp >= p.maxHP) { this.ui.toast('HP already full.'); return; }
      p.inv.potion--; p.hp = Math.min(p.maxHP, p.hp + CONFIG.items.potion.heal);
      this.fx.heal(p.pos.clone().add(new THREE.Vector3(0, 1.2, 0)));
      this.ui.damageNumber(p.pos, '+' + CONFIG.items.potion.heal, 'heal');
      audio.potion();
    }
    if (id === 'elixir') { p.inv.elixir--; p.st = Math.min(p.maxST, p.st + 60); audio.potion(); this.ui.toast('⚡ Stamina restored'); }
    if (id === 'focus') { p.inv.focus--; p.en = Math.min(p.maxEN, p.en + 60); audio.potion(); this.ui.toast('🔮 Energy restored'); }
    if (id === 'bomb') {
      p.inv.bomb--;
      const dir = new THREE.Vector3(Math.sin(p.yaw), 0, Math.cos(p.yaw));
      this.projectiles.fireBomb(p.pos.clone().add(new THREE.Vector3(0, 1.5, 0)), dir, CONFIG.items.bomb.dmg, this);
      audio.heavySwing();
    }
  }
  buyWeapon(id) {
    const w = CONFIG.weapons.find((x) => x.id === id);
    const p = this.player;
    if (p.weaponId === id) return;
    if (w.price && p.gold < w.price) { this.ui.toast(`Need ${w.price}g for ${w.name} (you have ${p.gold}g)`); return; }
    if (w.price) p.gold -= w.price;
    p.weaponId = id; audio.pickup();
    this.ui.toast(`🗡 Equipped ${w.name}!`); this.ui.renderPanel('inv');
  }

  // ---------- settings / difficulty / save ----------
  setGraphics(name, silent = false) {
    if (!CONFIG.graphics[name]) return;
    this.settings.graphics = name;
    const g = CONFIG.graphics[name];
    this.renderer.setPixelRatio(Math.min(devicePixelRatio || 1, g.pixelRatio * 1.5) * (name === 'low' ? 0.8 : 1));
    this.renderer.shadowMap.enabled = g.shadows;
    this.renderer.shadowMap.type = THREE.PCFShadowMap;
    this.world.applyGraphics(g);
    this.fx.quality = g.particles;
    this.weather.setCount(g.rain);
    const sel = document.getElementById('sel-graphics'); if (sel) sel.value = name;
    this.persistSettings();
    if (!silent) { this.ui.toast('Graphics: ' + name); this.ui.renderSettings(); }
    this.scene.traverse((o) => { if (o.material) o.material.needsUpdate = true; });
  }
  setDifficulty(d) {
    this.difficulty = d;
    // rescale living enemies
    for (const e of this.enemies) {
      if (!e.alive) continue;
      const ratio = e.hp / e.maxHP;
      e.maxHP = Math.round(e.cfg.hp * this.diff.enemyHP * (e === this.templeGuardian ? 1.6 : 1));
      e.hp = Math.round(e.maxHP * ratio);
      e.dmg = Math.round(e.cfg.dmg * this.diff.enemyDmg);
    }
    this.ui.toast('Difficulty: ' + this.diff.label);
    const sel = document.getElementById('sel-difficulty'); if (sel) sel.value = d;
  }
  persistSettings() { SaveSystem.saveSettings(this.settings); }
  saveGame() {
    if (SaveSystem.save(this)) this.ui.toast('💾 Game saved.');
    else this.ui.toast('⚠ Save failed (storage blocked).');
  }
  applySave(d) {
    const p = this.player;
    p.level = d.level || 1; p.xp = d.xp || 0; p.gold = d.gold ?? 40;
    p.maxHP = CONFIG.player.baseHP + CONFIG.player.hpPerLevel * (p.level - 1);
    p.maxST = CONFIG.player.baseST + CONFIG.player.stPerLevel * (p.level - 1);
    p.maxEN = CONFIG.player.baseEN + CONFIG.player.enPerLevel * (p.level - 1);
    p.hp = Math.min(p.maxHP, d.hp || p.maxHP); p.st = p.maxST; p.en = p.maxEN;
    p.weaponId = d.weapon || 'rusty';
    Object.assign(p.inv, d.inv || {});
    p.pos.set(d.px || LOC.spawn.x, 0, d.pz || LOC.spawn.z);
    p.pos.y = groundHeight(p.pos.x, p.pos.z);
    for (const id of (d.skillsUnlocked || [])) p.skillsUnlocked.add(id);
    this.quests.mainIndex = d.questIdx || 0;
    this.quests.campKills = this.quests.mainIndex > 1 ? 4 : 0;
    if ((d.questIdx || 0) >= 3) { this.quests.hasKey = true; if (this.world.keyMesh) this.world.keyMesh.visible = false; }
    if ((d.questIdx || 0) >= 4) { this.quests.sealOpen = true; this.world.seal.visible = false; this.world.sealRune.visible = false; }
    if (d.openedChests) for (const id of d.openedChests) { this.world.openedChests.add(id); const c = this.world.chests.find((x) => x.userData.id === id); if (c) c.visible = false; }
    this.playTime = d.playTime || 0;
    this.setDifficulty(d.difficulty || 'normal');
  }

  // ---------- menus ----------
  _bindMenus() {
    const show = (id, on) => document.getElementById(id).classList.toggle('hidden', !on);
    document.getElementById('btn-new').onclick = () => { audio.resume(); audio.startMusic(); audio.ui(); this.startFresh(); };
    document.getElementById('btn-continue').onclick = () => {
      audio.resume(); audio.startMusic(); audio.ui();
      const d = SaveSystem.load();
      if (!d) { this.ui.toast('No save found — starting fresh.'); this.startFresh(); return; }
      this.startFresh(d);
    };
    document.getElementById('btn-help').onclick = () => show('help-panel', true);
    document.getElementById('btn-help-close').onclick = () => show('help-panel', false);
    document.getElementById('btn-resume').onclick = () => { show('pause-menu', false); this.paused = false; };
    document.getElementById('btn-open-panel').onclick = () => { show('pause-menu', true); this.ui.openPanel('inv'); };
    document.getElementById('btn-save').onclick = () => this.saveGame();
    document.getElementById('btn-quit').onclick = () => location.reload();
    document.getElementById('btn-respawn').onclick = () => this.respawn();
    document.getElementById('btn-load-death').onclick = () => {
      const d = SaveSystem.load();
      if (d) { this.applySave(d); this._revive(); } else this.respawn();
    };
    document.getElementById('btn-victory-free').onclick = () => show('victory-screen', false);
    document.getElementById('btn-victory-title').onclick = () => location.reload();
    document.getElementById('sel-difficulty').onchange = (e) => this.setDifficulty(e.target.value);
    document.getElementById('sel-graphics').onchange = (e) => this.setGraphics(e.target.value);
    document.querySelectorAll('.ptab[data-tab]').forEach((b) => { b.onclick = () => { audio.ui(); this.ui.renderPanel(b.dataset.tab); }; });
    document.getElementById('panel-close').onclick = () => this.ui.closePanel();
  }
  startFresh(saveData = null) {
    if (saveData) this.applySave(saveData);
    else {
      this.setDifficulty(document.getElementById('sel-difficulty').value || 'normal');
      this.player.pos.set(LOC.spawn.x, groundHeight(LOC.spawn.x, LOC.spawn.z), LOC.spawn.z);
    }
    document.getElementById('main-menu').classList.add('hidden');
    document.getElementById('loading-screen').classList.add('hidden');
    document.getElementById('hud').classList.remove('hidden');
    if (this.input.touchMode) document.getElementById('touch-ui').classList.remove('hidden');
    this.paused = false;
    this.ui.fade(false);
    this.ui.toast('⚔ Shadow Warrior: Rise of the Legend');
    setTimeout(() => this.ui.toast('Follow the golden beacon →'), 2800);
    this._started = true;
  }
  _revive() {
    const p = this.player;
    p.hp = p.maxHP; p.st = p.maxST; p.en = p.maxEN;
    this.dead = false; this.paused = false;
    document.getElementById('death-screen').classList.add('hidden');
    this.ui.fade(false);
  }
  respawn() {
    const p = this.player;
    // checkpoint: village (or temple if late game)
    const cp = this.quests.mainIndex >= 4 ? LOC.temple : LOC.village;
    p.pos.set(cp.x + 3, groundHeight(cp.x + 3, cp.z + 3), cp.z + 3);
    p.deaths++;
    // enemies near checkpoint despawn-aggro reset: heal non-boss enemies partially
    for (const e of this.enemies) { if (e.alive && e.type !== 'boss') { e.hp = e.maxHP; e.pos.set(e.home.x, groundHeight(e.home.x, e.home.z), e.home.z); } }
    this._revive();
    this.ui.toast('Risen at checkpoint. Enemies regroup...');
  }
  onVictory() {
    this.won = true;
    audio.levelup();
    document.getElementById('victory-info').textContent =
      `The Shadow Warlord falls! Time: ${Math.floor(this.playTime / 60)}m ${Math.floor(this.playTime % 60)}s • Level ${this.player.level} • Kills ${this.player.kills} • Deaths ${this.player.deaths}`;
    setTimeout(() => document.getElementById('victory-screen').classList.remove('hidden'), 1800);
    this.saveGame();
  }
  _onDeath() {
    this.dead = true; this.paused = true;
    audio.death();
    document.getElementById('death-info').textContent =
      `Level ${this.player.level} • ${Math.floor(this.playTime / 60)}m in • ${this.player.kills} kills. Your legend is not over.`;
    setTimeout(() => document.getElementById('death-screen').classList.remove('hidden'), 1400);
  }

  // ---------- interaction scan ----------
  _interactTarget() {
    const p = this.player.pos;
    let best = null, bd = 3.4;
    for (const n of this.npcs) {
      const d = Math.hypot(n.pos.x - p.x, n.pos.z - p.z);
      if (d < bd) { bd = d; best = { kind: 'npc', npc: n, label: 'Talk to ' + n.name }; }
    }
    if (this.world.keyMesh.visible) {
      const k = this.world.keyAltar;
      if (Math.hypot(k.x - p.x, k.z - p.z) < 3.2) best = { kind: 'key', label: 'Take the Temple Key' };
    }
    if (this.quests.hasKey && !this.quests.sealOpen) {
      const s = this.world.sealPos;
      if (Math.hypot(s.x - p.x, s.z - p.z) < 5) best = { kind: 'seal', label: 'Unlock the Temple Seal 🗝' };
    }
    for (const c of this.world.chests) {
      if (!c.visible) continue;
      if (Math.hypot(c.position.x - p.x, c.position.z - p.z) < 3) { best = { kind: 'chest', chest: c, label: 'Open chest' }; break; }
    }
    for (const h of this.world.herbs) {
      if (!h.visible) continue;
      if (Math.hypot(h.position.x - p.x, h.position.z - p.z) < 2.6) { best = { kind: 'herb', herb: h, label: 'Gather moonherb' }; break; }
    }
    return best;
  }
  _doInteract(t) {
    const p = this.player;
    if (t.kind === 'npc') { this._talk(t.npc); }
    if (t.kind === 'key') { this.quests.advance('got-key'); audio.pickup(); }
    if (t.kind === 'seal') { this.quests.advance('open-seal'); }
    if (t.kind === 'chest') {
      const c = t.chest;
      c.visible = false;
      this.world.openedChests.add(c.userData.id);
      p.gold += c.userData.gold;
      p.inv.potion += 1;
      p.gainXP(40, this.ui);
      audio.coin();
      this.ui.toast(`🎁 +${c.userData.gold}g, +1 potion!`);
      this.fx.magic(c.position.clone().add(new THREE.Vector3(0, 1, 0)));
      if (c.userData.id === 'heirloom') this.quests.advance('chest-heirloom');
      if (c.userData.id === 'campLoot' && p.weaponId === 'rusty') { p.weaponId = 'iron'; this.ui.toast('🗡 Looted: IRON FANG!'); }
    }
    if (t.kind === 'herb') {
      t.herb.visible = false;
      this.quests.advance('herb');
      audio.pickup();
      this.fx.heal(t.herb.position.clone().add(new THREE.Vector3(0, 1, 0)));
    }
  }
  _talk(npc) {
    this.paused = true;
    const q = this.quests;
    let lines = DIALOGUES[npc.dialogueId] || [{ who: npc.name, text: 'The shadow stirs. Stay sharp.' }];
    if (npc.name === 'Elder Maren') {
      if (q.mainIndex === 0) lines = DIALOGUES.elder0;
      else if (q.mainIndex === 1 && q.campKills >= 4) lines = DIALOGUES.elder1;
      else if (q.mainIndex === 1) lines = [{ who: 'Elder Maren', text: `The camp still stands — ${Math.min(4, q.campKills)}/4 cleared. East, warrior. No mercy for bandits.` }];
      else lines = [{ who: 'Elder Maren', text: 'The key, the temple, the summit. The legend walks with you. Buy steel from Dain if you can.' }];
    }
    const opts = [];
    if (npc.name === 'Smith Dain') opts.push({ label: '🗡 Browse weapons (opens inventory)', fn: () => { this.ui.hideDialogue(); this.ui.openPanel('inv'); } });
    if (npc.name === 'Healer Sera') {
      opts.push({ label: '🧪 Buy potion (30g)', fn: () => this._buy('potion', 30) });
      opts.push({ label: '⚡ Buy elixir (22g)', fn: () => this._buy('elixir', 22) });
      opts.push({ label: '🔮 Buy charm (25g)', fn: () => this._buy('focus', 25) });
      opts.push({ label: '💥 Buy bomb (45g)', fn: () => this._buy('bomb', 45) });
    }
    this.ui.showDialogue(npc.name, lines, () => {
      this.paused = false;
      if (npc.name === 'Elder Maren') {
        q.advance('talk-elder');
        npc.hasQuest = q.mainIndex <= 1;
      }
    }, opts.length ? opts : null);
  }
  _buy(id, price) {
    const p = this.player;
    if (p.gold < price) { this.ui.toast('Not enough gold!'); return; }
    p.gold -= price; p.inv[id]++;
    audio.coin(); this.ui.toast(`Bought ${CONFIG.items[id].name}.`);
  }

  // ---------- camera ----------
  _updateCamera(dt) {
    const inp = this.input.takeCam();
    this.cam.yaw -= inp.dx;
    this.cam.pitch = clamp(this.cam.pitch + inp.dy, -0.15, 1.15);
    const p = this.player;
    // lock-on bias
    if (p.lockTarget && p.lockTarget.alive) {
      const want = Math.atan2(p.lockTarget.pos.x - p.pos.x, p.lockTarget.pos.z - p.pos.z);
      let d = want - this.cam.yaw;
      while (d > Math.PI) d -= Math.PI * 2;
      while (d < -Math.PI) d += Math.PI * 2;
      this.cam.yaw += d * Math.min(1, dt * 3);
    }
    const pivot = p.pos.clone().add(new THREE.Vector3(0, 2.1, 0));
    // pitch > 0: camera rises above the player looking down; pitch < 0: low dramatic angle
    const dir = new THREE.Vector3(
      -Math.sin(this.cam.yaw) * Math.cos(this.cam.pitch),
      Math.sin(this.cam.pitch) + 0.22,
      -Math.cos(this.cam.yaw) * Math.cos(this.cam.pitch)
    ).normalize();
    let dist = this.cam.dist;
    // collision: step back until above terrain / out of colliders (trees, houses, pillars)
    const blocked = (probe) => {
      const gy = groundHeight(probe.x, probe.z) + 0.4;
      if (probe.y <= gy) return true;
      for (const c of this.world.colliders) {
        const dx = probe.x - c.x, dz = probe.z - c.z;
        if (dx * dx + dz * dz < (c.r + 0.6) * (c.r + 0.6) && probe.y < groundHeight(c.x, c.z) + 6) return true;
      }
      return false;
    };
    for (let d = dist; d > 1.2; d -= 0.6) {
      const probe = pivot.clone().addScaledVector(dir, d);
      if (!blocked(probe)) { dist = d; break; }
      dist = d - 0.6;
    }
    const eye = pivot.clone().addScaledVector(dir, dist);
    // shake
    if (this.shake > 0 && this.settings.shake) {
      eye.x += (Math.random() - 0.5) * this.shake * 0.5;
      eye.y += (Math.random() - 0.5) * this.shake * 0.5;
      this.shake = Math.max(0, this.shake - dt * 2.2);
    }
    this.camera.position.lerp(eye, Math.min(1, dt * 14));
    this.camera.lookAt(pivot.x, pivot.y - 0.3, pivot.z);
    // sun shadow follows player
    this.world.sun.target.position.copy(p.pos);
  }

  // ---------- main loop ----------
  start() { this._last = performance.now(); requestAnimationFrame(this._loop); }
  _loop(now) {
    requestAnimationFrame(this._loop);
    let dt = Math.min(0.05, (now - this._last) / 1000);
    this._last = now;
    if (this.paused) { this.renderer.render(this.scene, this.camera); return; }
    if (this.hitstop > 0) { this.hitstop -= dt; dt *= 0.08; }
    const p = this.player;
    this.playTime += dt;
    // inputs → combat
    if (this.input.consumeAction('attack')) this.combat.tryLight(this);
    if (this.input.consumeAction('heavy')) this.combat.tryHeavy(this);
    if (this.input.consumeAction('skill1')) this.combat.trySkill(this, 'sunder');
    if (this.input.consumeAction('skill2')) this.combat.trySkill(this, 'wrath');
    if (this.input.consumeAction('potion')) this.useItem('potion');
    if (this.input.consumeAction('inv')) this.ui.openPanel('inv');
    if (this.input.consumeAction('pause')) {
      const pm = document.getElementById('pause-menu');
      const open = pm.classList.contains('hidden');
      pm.classList.toggle('hidden', !open);
      this.paused = open ? true : false;
      if (!open && !document.getElementById('panel').classList.contains('hidden')) this.paused = true;
    }
    this.combat.updateLock(this, this.input);
    // player
    p.update(dt, this.input, this.world, this.cam);
    if (!p.alive && !this.dead) this._onDeath();
    // interaction prompt
    const tgt = p.alive ? this._interactTarget() : null;
    const ip = document.getElementById('interact-prompt');
    if (tgt) {
      ip.classList.remove('hidden');
      document.getElementById('interact-text').textContent = tgt.label;
      document.getElementById('interact-key').textContent = this.input.touchMode ? '👁' : 'E';
      if (this.input.consumeAction('interact')) this._doInteract(tgt);
    } else ip.classList.add('hidden');
    // enemies
    for (let i = this.enemies.length - 1; i >= 0; i--) {
      const e = this.enemies[i];
      const res = e.update(dt, p, this.world, this.fx);
      if (res === 'despawn' && e.type !== 'boss' && e !== this.templeGuardian) {
        this.scene.remove(e.group);
        this.enemies.splice(i, 1);
      }
    }
    // boss trigger
    if (!this.bossSpawned && p.alive) {
      const s = LOC.summit;
      if (Math.hypot(p.pos.x - s.x, p.pos.z - s.z) < 26 && this.quests.mainIndex >= 4) this.spawnBoss();
    }
    for (const n of this.npcs) n.update(dt);
    this.projectiles.update(dt, p, this.enemies, this.fx);
    this.fx.update(dt);
    // sky + weather
    this.skyInfo = this.world.updateSky(dt, this.camera.position);
    const w = this.weather.update(dt, this.camera.position, this.playTime);
    audio.setRain(w.wet);
    audio.combatMode = audio.combatMode || w.label === 'storm';
    this.world.update(dt, this.playTime);
    // quest beacon follows current objective
    const b = this.quests.beaconTarget();
    if (b) this.world.setBeacon(b.x, b.z); else this.world.setBeacon(null);
    // HUD + minimap (throttle minimap to 15fps)
    this.ui.updateHUD();
    this._mmT = (this._mmT || 0) + dt;
    if (this._mmT > 0.066) { this._mmT = 0; this.ui.drawMinimap(); }
    // autosave every 60s
    this._saveT = (this._saveT || 0) + dt;
    if (this._saveT > 60) { this._saveT = 0; if (this._started && p.alive) this.saveGame(); }
    this._updateCamera(dt);
    // LOD: hide distant enemies' shadows / skip far AI at low fps? simple distance cull for corpses
    this.renderer.render(this.scene, this.camera);
    // fps adapt: if slow, suggest lower gfx (toast once)
    this._fpsAcc = (this._fpsAcc || 0) + dt; this._fpsN = (this._fpsN || 0) + 1;
    if (this._fpsAcc > 5) {
      const fps = this._fpsN / this._fpsAcc;
      this._fpsAcc = 0; this._fpsN = 0;
      if (fps < 24 && !this._fpsWarned && this.settings.graphics !== 'low') {
        this._fpsWarned = true;
        this.ui.toast('⚠ Low FPS — try Low graphics in Settings (pause menu).', 5000);
      }
    }
  }
}
