# PlayGround

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

## Windows app (.exe)

The SRT Video Player is also packaged as a portable Windows executable:

- Download: [`release/SRT-Video-Player-1.0.0-win-x64.exe`](release/SRT-Video-Player-1.0.0-win-x64.exe)
- Windows 10/11, 64-bit. No installer needed - double-click to run.
- The build is unsigned, so Windows SmartScreen may ask you to confirm the first launch.

Rebuild the executable on any platform with Node.js 20+:

```sh
cd desktop
npm install
npm run icon
npm run dist:win
```

The Electron shell loads the same static app from `src/` and writes the portable
executable to `release/`. The browser version still runs with `./start.sh`.
