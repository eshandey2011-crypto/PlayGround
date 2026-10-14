// Real-time third-person combat: light/heavy chains, combos, specials, lock-on.
import * as THREE from 'three';
import { CONFIG } from './config.js';
import { audio } from './audio.js';

export class Combat {
  constructor(player, fx) {
    this.p = player; this.fx = fx;
    this.lightChain = [0.42, 0.42, 0.55]; // per-stage durations
  }
  _dmgMul() { return this.p.weapon.dmgMul; }
  _diffMul(game) { return game.diff.playerDmg; }

  tryLight(game) {
    const p = this.p;
    if (p.atkTimer > 0.12 || p.dodgeT > 0 || !p.alive) return false;
    if (p.st < CONFIG.player.lightCost * 0.5) { game.ui.toast('Too exhausted!'); return false; }
    p.st = Math.max(0, p.st - CONFIG.player.lightCost);
    p.atkStage = p.atkTimer > 0 ? 0 : (p.combo % 3);
    p.atkTimer = this.lightChain[p.atkStage % 3];
    p.combo++; p.comboT = 2.2;
    if (p.combo >= 3) game.ui.showCombo(p.combo);
    audio.swing();
    this._arcHit(game, CONFIG.player.lightDmg[p.atkStage % 3] * this._dmgMul() * this._diffMul(game), 3.2, 75, false);
    // vampiric finisher
    if (p.combo % 3 === 0 && p.skillsUnlocked.has('vampiric')) {
      p.hp = Math.min(p.maxHP, p.hp + 6);
      this.fx.heal(p.pos.clone().add(new THREE.Vector3(0, 1.5, 0)));
      game.ui.damageNumber(p.pos, '+6', 'heal');
    }
    return true;
  }
  tryHeavy(game) {
    const p = this.p;
    if (p.atkTimer > 0.15 || p.dodgeT > 0 || !p.alive) return false;
    if (p.st < CONFIG.player.heavyCost * 0.5) { game.ui.toast('Too exhausted!'); return false; }
    p.st = Math.max(0, p.st - CONFIG.player.heavyCost);
    p.atkTimer = 0.68; p.combo++; p.comboT = 2.2;
    if (p.combo >= 3) game.ui.showCombo(p.combo);
    audio.heavySwing();
    setTimeout(() => {
      if (!p.alive) return;
      this._arcHit(game, CONFIG.player.heavyDmg * this._dmgMul() * this._diffMul(game), 3.6, 100, true);
      this.fx.dust(p.pos.clone().add(new THREE.Vector3(Math.sin(p.yaw) * 2, 0.3, Math.cos(p.yaw) * 2)));
    }, 220);
    return true;
  }
  trySkill(game, id) {
    const p = this.p;
    if (!p.hasSkill(id)) { game.ui.toast(id === 'sunder' ? 'Reach Lv 2 to unlock Sunder Strike.' : 'Reach Lv 4 to unlock Heaven\'s Wrath.'); return false; }
    const sk = CONFIG.skills.find((s) => s.id === id);
    if ((id === 'sunder' && p.skillCD.sunder > 0) || (id === 'wrath' && p.skillCD.wrath > 0)) { game.ui.toast('Skill recharging...'); return false; }
    if (p.en < sk.cost) { game.ui.toast('Not enough energy!'); return false; }
    p.en -= sk.cost;
    audio.skill();
    if (id === 'sunder') {
      p.skillCD.sunder = sk.cd; p.atkTimer = Math.max(p.atkTimer, 0.5);
      const c = p.pos.clone();
      this.fx.magic(c.clone().add(new THREE.Vector3(0, 1, 0)), 0xffc94a);
      this.fx.burst(c.clone().add(new THREE.Vector3(0, 0.5, 0)), 30, { color: 0xffc94a, spread: 8, up: 3, life: 0.6 });
      this._arcHit(game, sk.dmg * this._dmgMul(), 5.2, 140, true);
    } else {
      p.skillCD.wrath = sk.cd; p.atkTimer = Math.max(p.atkTimer, 0.5);
      const tgt = p.lockTarget && p.lockTarget.alive ? p.lockTarget : game.nearestEnemy(22);
      const at = tgt ? tgt.pos.clone() : p.pos.clone().add(new THREE.Vector3(Math.sin(p.yaw) * 6, 0, Math.cos(p.yaw) * 6));
      this.fx.magic(at.clone().add(new THREE.Vector3(0, 2, 0)), 0x88ccff);
      this.fx.burst(at.clone().add(new THREE.Vector3(0, 1, 0)), 40, { color: 0x88ccff, spread: 4, up: 10, life: 0.8 });
      audio.thunder();
      game.weather.flash = 0.7;
      this._pointHit(game, at, 6, sk.dmg * this._dmgMul());
    }
    return true;
  }
  _arcHit(game, dmg, range, arcDeg, heavy) {
    const p = this.p;
    const fwd = p.yaw;
    let hitAny = false;
    for (const e of game.enemies) {
      if (!e.alive) continue;
      const dx = e.pos.x - p.pos.x, dz = e.pos.z - p.pos.z;
      const d = Math.hypot(dx, dz);
      if (d > range + (e.type === 'boss' ? 0.8 : 0)) continue;
      let ang = Math.atan2(dx, dz) - fwd;
      while (ang > Math.PI) ang -= Math.PI * 2;
      while (ang < -Math.PI) ang += Math.PI * 2;
      if (Math.abs(ang) > (arcDeg * Math.PI / 180) / 2 && d > 1.2) continue;
      const crit = Math.random() < (heavy ? 0.3 : 0.12);
      const final = Math.round(dmg * (crit ? 1.8 : 1) * (0.9 + Math.random() * 0.2));
      e.takeDamage(final, p.pos, this.fx);
      game.ui.damageNumber(e.pos, final, crit ? 'crit' : '');
      if (crit) audio.crit(); else audio.hit();
      this.fx.spark(e.pos.clone().add(new THREE.Vector3(0, 1.4, 0)));
      hitAny = true;
      game.onEnemyHit(e, final);
    }
    if (!hitAny) return;
    // hit-stop feel
    game.hitstop = Math.max(game.hitstop, heavy ? 0.09 : 0.045);
  }
  _pointHit(game, at, radius, dmg) {
    for (const e of game.enemies) {
      if (!e.alive) continue;
      if (Math.hypot(e.pos.x - at.x, e.pos.z - at.z) < radius) {
        const final = Math.round(dmg * (0.9 + Math.random() * 0.2));
        e.takeDamage(final, game.player.pos, this.fx);
        game.ui.damageNumber(e.pos, final, 'crit');
        game.onEnemyHit(e, final);
      }
    }
  }
  updateLock(game, input) {
    const p = this.p;
    if (input.consumeAction('lock')) {
      if (p.lockTarget && p.lockTarget.alive) { p.lockTarget = null; game.ui.toast('Lock released'); }
      else {
        const e = game.nearestEnemy(30);
        if (e) { p.lockTarget = e; game.ui.toast('🎯 Locked: ' + e.name); audio.ui(); }
      }
    }
    if (p.lockTarget && (!p.lockTarget.alive || Math.hypot(p.lockTarget.pos.x - p.pos.x, p.lockTarget.pos.z - p.pos.z) > 34)) {
      p.lockTarget = null;
    }
  }
}
