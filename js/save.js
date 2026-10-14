// localStorage save/load with versioning + checkpoint respawn.
const KEY = 'shadow-warrior-save-v1';
const SETTINGS_KEY = 'shadow-warrior-settings-v1';

export const SaveSystem = {
  save(game) {
    try {
      const p = game.player;
      const data = {
        v: 1, time: Date.now(),
        difficulty: game.difficulty,
        px: +p.group.position.x.toFixed(2), pz: +p.group.position.z.toFixed(2),
        level: p.level, xp: p.xp, hp: Math.ceil(p.hp),
        gold: p.gold, weapon: p.weaponId,
        inv: p.inv, questIdx: game.quests.mainIndex,
        side: game.quests.sideState(), openedChests: [...game.world.openedChests],
        gatheredHerbs: game.world.gatheredHerbs, playTime: game.playTime,
        skillsUnlocked: [...p.skillsUnlocked],
      };
      localStorage.setItem(KEY, JSON.stringify(data));
      return true;
    } catch { return false; }
  },
  load() {
    try { const s = localStorage.getItem(KEY); return s ? JSON.parse(s) : null; }
    catch { return null; }
  },
  has() { try { return !!localStorage.getItem(KEY); } catch { return false; } },
  clear() { try { localStorage.removeItem(KEY); } catch {} },
  saveSettings(s) { try { localStorage.setItem(SETTINGS_KEY, JSON.stringify(s)); } catch {} },
  loadSettings() { try { const s = localStorage.getItem(SETTINGS_KEY); return s ? JSON.parse(s) : null; } catch { return null; } },
};
