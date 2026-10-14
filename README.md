# ⚔ Shadow Warrior: Rise of the Legend

A complete, playable **3D open-world action RPG** that runs on Android phones/tablets
(and any modern browser). No build step, no assets to download — just serve the folder
over HTTP and play. Three.js is vendored locally (`vendor/`), so the game works offline.

## ▶ Play

Serve statically (any static server works), e.g.:

```bash
python3 -m http.server 8080
# open http://localhost:8080
```

On Android: open the URL in Chrome → *Add to Home screen* — `manifest.json` makes it
install as a fullscreen landscape app. Touch controls (joystick + buttons) appear
automatically on touch devices.

`?auto=new` skips the title screen (used for automated smoke tests).

## 🗡 The game (start → finish, ~15 min)

1. **Ashes of Dawn** — meet Elder Maren in the village (follow the golden beacon).
2. **The Bandit Toll** — clear the eastern bandit camp (scouts, brutes, a hex shaman).
3. **Whispers in the Cave** — brave wolves, claim the Temple Key in the crystal cave.
4. **The Sunken Temple** — unseal the temple, defeat the Guardian Brute.
5. **Rise of the Legend** — climb to the summit, slay the **Shadow Warlord**
   (2-phase boss with slam AoEs) → victory screen, then free roam.

Plus 3 side quests (forest heirloom chest, wolf cull, moonherb gathering),
5 hidden treasure chests, shops (smith/weapons, healer/consumables), and mining-free gold economy.

## ✨ Systems

| Area | What's implemented |
|---|---|
| Player | Walk/run/sprint, jump, dodge-roll w/ i-frames, light×3 chain, heavy, block (directional), 2 unlockable specials, combos, hit-stop, screen shake |
| Stats | HP / stamina / energy, XP levels 1–6+, skill unlocks (Sunder, Wrath, Iron Will, Wind Foot, Vampiric Edge), 4 weapons, gold |
| Enemies | Wolves (chasers), scouts (circlers), brutes, ranged shamans (projectiles), 2-phase boss; patrol/wander/aggro AI, hit reactions, corpses |
| World | 420×420m terrain, village, bandit camp, crystal cave, pillar temple, summit arena, forest ruins, ponds; day/night cycle, clear/rain/storm/fog weather w/ lightning, quest beacon |
| NPCs | Elder / healer / smith with branching dialogue, shops, quest markers |
| UI | HP/st/en bars, level+XP, quest tracker w/ distance, circular minimap, boss bar, combo counter, damage numbers, toasts, dialogue box, inventory/skills/quests/stats/settings tabs, pause/death/victory screens |
| Android | Virtual joystick (push to sprint), attack/heavy/block/dodge/jump/skill/lock-on/interact buttons, drag-to-look camera, responsive + small-screen layouts, safe-area insets |
| Camera | Third-person follow, terrain + prop collision, lock-on tracking, sensitivity + shake toggles |
| Meta | Story/Normal/Hard difficulties, Low→Ultra graphics presets, localStorage save/continue + autosave, checkpoint respawn, procedural WebAudio music + SFX (no files) |
| Perf | Instanced trees/grass/rocks, pooled particles & projectiles, capped pixel ratio, distance-culled corpses, ~170 draw calls / ~70k tris (60 FPS territory on real mobile GPUs) |

## 🗂 Architecture (`js/`)

`config.js` (balance/content) · `utils.js` (terrain height field, pools, bus) ·
`save.js` · `audio.js` (procedural music/SFX) · `input.js` (keyboard+touch) ·
`particles.js` (pooled FX + weather) · `world.js` (terrain/sky/locations) ·
`entities.js` (player/enemies/boss/NPCs/projectiles) · `combat.js` ·
`quests.js` (story state machine) · `ui.js` · `game.js` (loop/camera/spawns) ·
`main.js` (boot). Single analytic `groundHeight(x,z)` shared by rendering,
physics, AI and camera — no raycasts in the hot loop.

## 🧪 Verified

Boot with zero console errors, scripted playthrough of the full quest chain
(elder → 4 camp kills → key → seal → guardian → boss → victory), combat damage,
XP/level-ups, gold, save/load, death → checkpoint respawn, day/night, storm,
and desktop + phone-viewport screenshots — all via headless Chromium
(CPU-rendered SwiftShader, ~8 FPS there; trivial load for real phone GPUs).

<!-- omgithub:readme:start -->
## 🚀 Build, play, and remix with OMGithub

**Created using [OMGithub.com](https://omgithub.com).**

[![OMGithub](https://img.shields.io/badge/OMGithub-Open%20project-orange?style=for-the-badge)](https://omgithub.com/eshandey2011-crypto/PlayGround)
[![GitHub](https://img.shields.io/badge/GitHub-Source-181717?logo=github&style=for-the-badge)](https://github.com/eshandey2011-crypto/PlayGround)

- 🎮 [Open the project](https://omgithub.com/eshandey2011-crypto/PlayGround).
- ✨ [Remix this project](https://omgithub.com/?remix=eshandey2011-crypto%2FPlayGround).
- 💻 [Explore the source](https://github.com/eshandey2011-crypto/PlayGround).
- 🛠️ [Check build runs](https://github.com/eshandey2011-crypto/PlayGround/actions).
- 🐛 [Report an issue](https://github.com/eshandey2011-crypto/PlayGround/issues).
- 👤 [Explore the creator's projects](https://omgithub.com/eshandey2011-crypto).
- 🌍 [Create with OMGithub](https://omgithub.com).
<!-- omgithub:readme:end -->
