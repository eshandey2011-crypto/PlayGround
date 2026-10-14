// Quest state machine: 5 main missions forming a complete story + 3 side quests.
import { QUESTS } from './config.js';
import { LOC } from './world.js';
import { audio } from './audio.js';

export class Quests {
  constructor(game) {
    this.game = game;
    this.mainIndex = 0;             // index into main quests
    this.campKills = 0; this.wolfKills = 0;
    this.side = { s1: 'active', s2: 'active', s3: 'active' };
    this.s1done = false; this.s2done = false; this.s3herbs = 0; this.s3done = false;
    this.done = false;              // main story complete
    this.hasKey = false; this.sealOpen = false; this.guardianDead = false;
  }
  get current() { return QUESTS.filter((q) => q.kind === 'main')[this.mainIndex] || null; }
  sideState() { return { s1: this.s1done, s2: this.s2done, s3: this.s3done, herbs: this.s3herbs }; }

  beaconTarget() {
    const c = this.current;
    if (!c || this.done) return null;
    const map = {
      village: LOC.village, camp: LOC.camp, cave: LOC.cave,
      temple: LOC.temple, summit: LOC.summit, heirloom: LOC.heirloom,
    };
    const b = map[c.beacon];
    return b ? { x: b.x, z: b.z } : null;
  }

  objectiveText() {
    const c = this.current;
    if (this.done) return { title: '👑 Legend Risen — valley free!', obj: 'Explore, finish side quests, grow stronger.', dist: '' };
    let obj = c.obj;
    if (c.id === 'q2') obj = `Clear the bandit camp (${Math.min(4, this.campKills)}/4) east of the village`;
    const b = this.beaconTarget();
    const g = this.game;
    const dist = b ? `${Math.round(Math.hypot(g.player.pos.x - b.x, g.player.pos.z - b.z))}m` : '';
    return { title: `Main: ${c.name}`, obj, dist };
  }

  // Called after dialogue with elder / interactions / kills / pickups.
  advance(event, data = {}) {
    const g = this.game, ui = g.ui;
    const c = this.current;
    // Side quest progress (always live)
    if (event === 'kill') {
      if (data.zone === 'camp' && this.mainIndex <= 1) {
        this.campKills++;
        if (this.mainIndex === 1) {
          ui.toast(`Camp cleared: ${Math.min(4, this.campKills)}/4`);
          if (this.campKills === 4) this._completeMain('q2', 'The camp burns! Return to Elder Maren.');
        }
      }
      if (data.type === 'wolf') {
        this.wolfKills++;
        if (!this.s2done && this.wolfKills >= 5) {
          this.s2done = true; g.player.gold += 120; g.player.gainXP(120, ui);
          ui.toast('✅ Side quest complete: Wolf Cull! +120g +XP'); audio.quest();
        } else if (!this.s2done) ui.toast(`Wolves culled: ${this.wolfKills}/5`);
      }
      if (data.type === 'brute' && data.zone === 'temple' && this.mainIndex === 3 && !this.guardianDead) {
        this.guardianDead = true;
        this._completeMain('q4', 'Guardian felled! Climb to the summit — end this.');
        g.ui.showBossWarn('THE PATH TO THE SUMMIT IS OPEN');
      }
      if (data.type === 'boss') {
        this._completeStory();
      }
    }
    if (event === 'talk-elder') {
      if (this.mainIndex === 0) {
        g.player.inv.potion += 2; g.player.gold += 30;
        this.mainIndex = 1;
        ui.toast('📜 New quest: The Bandit Toll'); audio.quest();
      }
      if (this.mainIndex === 1 && this.campKills >= 4) {
        // completes q2 immediately (kills may predate the quest)
        this._completeMain('q2', 'The camp burns! The valley breathes again.');
        this.mainIndex = 2;
        ui.toast('📜 New quest: Whispers in the Cave'); audio.quest();
      }
    }
    if (event === 'got-key' && this.mainIndex === 2) {
      this.hasKey = true;
      this.mainIndex = 3;
      g.world.keyMesh.visible = false;
      ui.toast('🗝 Temple Key claimed!'); audio.quest();
      ui.toast('📜 New quest: The Sunken Temple');
    }
    if (event === 'open-seal' && this.mainIndex === 3 && this.hasKey) {
      this.sealOpen = true;
      g.world.seal.visible = false; g.world.sealRune.visible = false;
      ui.toast('The temple seal grinds open...'); audio.quest();
      g.spawnTempleGuardian();
    }
    if (event === 'herb') {
      if (!this.s3done) {
        this.s3herbs++;
        if (this.s3herbs >= 3) {
          this.s3done = true; g.player.inv.potion += 2; g.player.inv.elixir += 1; g.player.gainXP(90, ui);
          ui.toast('✅ Side quest complete: Herbs for the Healer! +tonics'); audio.quest();
        } else ui.toast(`Moonherbs: ${this.s3herbs}/3`);
      }
    }
    if (event === 'chest-heirloom' && !this.s1done) {
      this.s1done = true; g.player.gold += 150; g.player.gainXP(100, ui);
      ui.toast('✅ Side quest complete: Lost Heirloom! +150g'); audio.quest();
    }
    void data;
  }
  _completeMain(id, msg) {
    const ui = this.game.ui;
    this.game.player.gainXP(id === 'q2' ? 90 : 150, ui);
    this.game.player.gold += 60;
    ui.toast('✅ ' + msg + ' +XP +60g'); audio.quest();
    if (id === 'q2') { /* wait for elder talk to advance to q3 */ }
    if (id === 'q4') { this.mainIndex = 4; ui.toast('📜 Final quest: Rise of the Legend'); }
  }
  _completeStory() {
    this.done = true; this.mainIndex = 5;
    const g = this.game;
    g.player.gainXP(600, g.ui);
    g.player.gold += 500;
    g.onVictory();
  }
}
