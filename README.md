<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/24b813ba-4d16-4a27-a921-ec1815e26049

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

# SRT Video Player (Android)

Smart SRT Video Player — multi-language subtitle voice (Hindi + 10 languages), offline subtitle TTS narration, voice profiles, subtitle sync (slider + ms offset), copy subtitles, gestures (swipe seek/volume/brightness), speed control, orientation lock, gallery-style folder browser + local media scanner, Room history, video export with Hindi subtitle audio.

- Package: `com.aistudio.srtplayer.qxpvlt`, minSdk 24, target/compileSdk 36
- Player: Media3 ExoPlayer + Compose UI (`MainActivity.kt`, `MxVideoPlayerScreen.kt`)
- Subtitles: `SrtParser.kt`, `SrtModels.kt`, `OfflineSubtitleEngine.kt`, `SrtTimelineSyncService.kt`, `SyncEngine.kt`
- TTS/Dubbing: `TtsViewModel.kt`, `VoiceManager.kt`, `HindiVoiceProfileDialog.kt`, dubbing project Room DB
- AI translate: Gemini (`GeminiTranslator.kt`, needs `GEMINI_API_KEY` in `.env`)
- Build: `./gradlew assembleDebug` (SDK 36, JDK 17)

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
