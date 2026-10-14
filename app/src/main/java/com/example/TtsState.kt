package com.example

import android.speech.tts.Voice

data class TtsVoiceItem(
    val name: String,
    val displayName: String,
    val locale: String,
    val gender: SpeakerGender = SpeakerGender.UNKNOWN,
    val isNetworkRequired: Boolean = false,
    val rawVoice: Voice? = null
)

enum class GenderVoiceMode(
    val title: String,
    val hindiTitle: String,
    val icon: String,
    val description: String
) {
    AUTO_MULTI_SPEAKER(
        "Auto Multi-Speaker",
        "Auto Multi-Speaker (Diarization)",
        "🎭",
        "Automatically adapts pitch & voice for Male, Female, and Narrator from subtitle tags and dialogue cues"
    ),
    FORCE_FEMALE(
        "Female Voice",
        "Female Voice",
        "👩",
        "Clear, high-pitch feminine voice tone (1.28x) optimized for female dialogue and speech"
    ),
    FORCE_MALE(
        "Male Voice",
        "Male Voice",
        "👨",
        "Deep, rich masculine baritone voice tone (0.85x) optimized for male dialogue and speech"
    ),
    FORCE_CHILD(
        "Child Voice",
        "Child Voice",
        "🧒",
        "Bright, youthful high-pitch voice tone (1.48x) optimized for kids and animation dialogue"
    ),
    FORCE_NARRATOR(
        "Deep Narrator",
        "Deep Narrator",
        "🎙️",
        "Authoritative, steady storytelling tone (0.92x) for documentaries and voiceover"
    ),
    SINGLE_DEEP_VOICE(
        "Offline Deep Voice (Single)",
        "Offline Deep Voice (Single Voice)",
        "🔊",
        "100% Offline single deep baritone voice (0.72x pitch) for all subtitles without multi-voice switching"
    )
}

enum class SpeechState {
    INITIALIZING,
    READY,
    SPEAKING,
    STOPPED,
    ERROR
}

enum class AppTab(val title: String) {
    LOCAL_FOLDERS("Folders"),
    ALL_VIDEOS("Videos"),
    VIDEO_PLAYER("Player"),
    SUBTITLE_TTS_READER("Dubbing & Subtitles"),
    DUBBING_PROJECTS("Projects")
}

enum class DecoderMode(val code: String, val displayName: String, val description: String) {
    HW("HW", "HW Decoder (Hardware)", "High-performance hardware acceleration"),
    HW_PLUS("HW+", "HW+ Decoder (Hardware Plus)", "Advanced multi-threaded hardware decoding"),
    SW("SW", "SW Decoder (Software)", "Software compatibility fallback mode")
}

enum class MxRepeatMode(val label: String, val shortLabel: String) {
    REPEAT_OFF("Repeat Off", "Off"),
    REPEAT_ALL("Repeat All", "All"),
    REPEAT_ONE("Repeat 1", "1"),
    SHUFFLE("Shuffle Play", "Shuf")
}

enum class AspectRatioMode(val label: String, val shortName: String) {
    FIT("Fit to Screen (16:9)", "FIT"),
    FILL("Crop / Fill", "CROP"),
    STRETCH("Stretch to Screen", "STRETCH"),
    ORIGINAL("100% Original", "100%"),
    CUSTOM_ZOOM("Custom Zoom", "ZOOM")
}

enum class SubtitleTheme(val label: String, val textColorHex: Long, val strokeColorHex: Long) {
    MX_YELLOW("Cinema Yellow", 0xFFFFEB3B, 0xFF000000),
    PURE_WHITE("Classic White", 0xFFFFFFFF, 0xFF000000),
    NEON_CYAN("Neon Cyan", 0xFF00E5FF, 0xFF000000),
    LIME_GREEN("Lime Green", 0xFF76FF03, 0xFF000000),
    AMBER_GOLD("Amber Gold", 0xFFFFB300, 0xFF000000),
    VIBRANT_ORANGE("Warm Orange", 0xFFFF7043, 0xFF000000),
    ROSE_PINK("Rose Pink", 0xFFFF4081, 0xFF000000),
    SKY_BLUE("Sky Blue", 0xFF40C4FF, 0xFF000000)
}

enum class SubtitleAlignment(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

enum class AppThemeMode(
    val code: String,
    val displayName: String,
    val hindiName: String,
    val description: String
) {
    DARK("DARK", "Dark Mode (Cinema/OLED)", "Dark Mode", "Deep black OLED background, easy on the eyes"),
    LIGHT("LIGHT", "Light Mode (Daylight)", "Light Mode", "Bright and crisp theme, ideal for daytime viewing"),
    SYSTEM("SYSTEM", "System Default", "System Default", "Follows system theme preference")
}

enum class AudioTrackMode(
    val displayName: String,
    val shortName: String,
    val description: String
) {
    HINDI_TTS_DUB(
        "Voice Track (TTS)",
        "Voice Track",
        "Original video audio muted, crystal-clear voice dialogue"
    ),
    ORIGINAL_VIDEO(
        "Original Video Audio Track",
        "Original",
        "Original video audio active"
    ),
    DUAL_AUDIO(
        "Dual Audio Track (Video + Dub)",
        "Dual Audio",
        "Original video soundtrack mixed with Hindi voice dubbing"
    )
}

data class VideoSoundTrackItem(
    val id: String,
    val trackGroupIndex: Int = 0,
    val trackFormatIndex: Int = 0,
    val title: String,
    val language: String? = null,
    val mimeType: String? = null,
    val channelCount: Int = 2,
    val sampleRate: Int = 44100,
    val bitrate: Int = 0,
    val isSelected: Boolean = false
)

enum class AudioPresetMode(
    val title: String,
    val subtitle: String,
    val isOriginalMuted: Boolean,
    val isAutoDubbing: Boolean,
    val isBgmOn: Boolean = false
) {
    PURE_HINDI_ONLY(
        "Pure Dialogue Only",
        "Original video audio muted, clean speech output only",
        isOriginalMuted = true,
        isAutoDubbing = true,
        isBgmOn = false
    ),
    ORIGINAL_PLUS_DUB(
        "Original Video + Voice (Mixed)",
        "Original video audio layered with voice dialogue",
        isOriginalMuted = false,
        isAutoDubbing = true,
        isBgmOn = false
    ),
    ORIGINAL_ONLY(
        "Original Video Sound Only",
        "Original video audio only",
        isOriginalMuted = false,
        isAutoDubbing = false,
        isBgmOn = false
    )
}

enum class VideoOrientationLock(
    val title: String,
    val subtitle: String
) {
    AUTO("Auto-Rotate", "Rotates freely with device motion (Sensor)"),
    LANDSCAPE("Lock Landscape", "Locked horizontally in widescreen mode"),
    PORTRAIT("Lock Portrait", "Locked vertically in upright mode")
}

data class TtsUiState(
    val text: String = "Hello! Welcome to the AI voice studio. Enter any text or load an SRT subtitle file.",
    val voices: List<TtsVoiceItem> = emptyList(),
    val selectedVoice: TtsVoiceItem? = null,
    val speedProgress: Int = 50,
    val pitchProgress: Int = 50,
    val speechState: SpeechState = SpeechState.READY,
    val statusMessage: String = "TTS Ready",
    val isHindiSupported: Boolean = true,
    val isSpokenLangSupported: Boolean = true,
    val errorMessage: String? = null,
    val currentTab: AppTab = AppTab.VIDEO_PLAYER,
    val videoUri: String? = "https://test-videos.co.uk/vids/sintel/mp4/h264/720/Sintel_720_10s_5MB.mp4",
    val videoTitle: String = "Office Meeting & Presentation",
    val isVideoPlaying: Boolean = false,
    val isVideoBuffering: Boolean = false,
    val videoPositionMs: Long = 0L,
    val videoDurationMs: Long = 32_000L,
    val pendingSeekMs: Long? = null,
    val activeAudioTrack: AudioTrackMode = AudioTrackMode.HINDI_TTS_DUB,
    val videoVolume: Float = 0.5f,
    val isVideoMuted: Boolean = true,
    val dubbingVolume: Float = 1.0f,
    val isDubbingMuted: Boolean = false,
    val masterVolume: Float = 1.0f,
    val isMasterMuted: Boolean = false,
    val isOriginalSolo: Boolean = false,
    val isDubbingSolo: Boolean = false,
    val crossfadeBalance: Float = 0.5f,
    val showVolumeMixerDialog: Boolean = false,
    val videoSoundTracks: List<VideoSoundTrackItem> = emptyList(),
    val selectedVideoSoundTrackId: String? = null,
    val showVideoSoundTrackDialog: Boolean = false,
    val soundTrackSyncDelayMs: Long = 0L,
    val isVocalClarityBoostEnabled: Boolean = false,
    val brightnessLevel: Float = 0.7f,
    val playbackSpeed: Float = 1.0f,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val videoZoomScale: Float = 1.0f,
    val videoPanOffsetX: Float = 0.0f,
    val videoPanOffsetY: Float = 0.0f,
    val showZoomControlsDialog: Boolean = false,
    val isFullscreen: Boolean = false,
    val orientationLock: VideoOrientationLock = VideoOrientationLock.AUTO,
    val showOrientationDialog: Boolean = false,
    val isScreenLocked: Boolean = false,
    val isLoopModeEnabled: Boolean = false,
    val repeatMode: MxRepeatMode = MxRepeatMode.REPEAT_OFF,
    val decoderMode: DecoderMode = DecoderMode.HW,
    val showRemainingTime: Boolean = false,
    val volumeBoostPercent: Int = 100,
    val isAutoDubbingEnabled: Boolean = true,
    val isSameToSameTimeDelivery: Boolean = true,
    val subtitleOffsetMs: Long = 0L,
    val dialogueLeadTimeMs: Long = 1000L,
    val isPerfectTimingEnabled: Boolean = true,
    val isDynamicRateAutoFitEnabled: Boolean = true,
    val showPerfectTimingDialog: Boolean = false,
    val showDecoderDialog: Boolean = false,
    val showVideoInfoDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSeconds: Long? = null,
    val sleepTimerTargetEpochMs: Long? = null,
    val isPipActive: Boolean = false,
    val isBgmEnabled: Boolean = false,
    val bgmVolume: Float = 0.0f,
    val bgmStyle: BgmStyle = BgmStyle.CINEMATIC_AMBIENT,
    val isAutoDuckingEnabled: Boolean = false,
    val bgmDuckingRatio: Float = 0.30f,
    val bgmMusicUri: String? = null,
    val bgmMusicFileName: String? = null,
    val bgmMusicDurationMs: Long = 0L,
    val bgmMusicFormat: String? = null,
    val isBgmLooping: Boolean = false,
    val isBgmFilePlaying: Boolean = false,
    val showBgmTrackMixerDialog: Boolean = false,
    val audioPresetMode: AudioPresetMode = AudioPresetMode.PURE_HINDI_ONLY,
    val subtitleFontSize: Int = 18,
    val subtitleTheme: SubtitleTheme = SubtitleTheme.MX_YELLOW,
    val subtitleBackgroundOpacity: Float = 0.75f,
    val subtitleAlignment: SubtitleAlignment = SubtitleAlignment.CENTER,
    val showSubtitlesOnVideo: Boolean = true,
    val showSubtitleAppearanceDialog: Boolean = false,
    val rawSrtText: String = "",
    val currentSrtFileName: String? = null,
    val detectedSubtitleFormat: SubtitleFormat = SubtitleFormat.SRT,
    val parsedSubtitles: List<SrtItem> = emptyList(),
    val speechTextCache: Map<Int, String> = emptyMap(),
    val activeSubtitle: SrtItem? = null,
    val lastSpokenSubtitleIndex: Int = -1,
    val isOfflineSubtitleMode: Boolean = true,
    val preferOfflineVoice: Boolean = true,
    val showOfflineSubtitleReaderDialog: Boolean = false,
    val isSubtitleReaderPlaying: Boolean = false,
    val readingSubtitleIndex: Int = -1,
    val subtitleReaderDelaySeconds: Float = 0.5f,
    val isSubtitleReaderContinuous: Boolean = true,
    val isOfflineHindiVoiceInSubtitles: Boolean = true,
    val isSingleDeepVoiceOnly: Boolean = true,
    val isFastSubtitleSpeechEnabled: Boolean = true,
    val fastSubtitleSpeechMultiplier: Float = 1.0f,
    val showAddSubtitleDialog: Boolean = false,
    val detectedSpeakers: List<SpeakerProfile> = emptyList(),
    val selectedSpeaker: SpeakerProfile? = null,
    val speakerPersonas: List<HindiVoicePersona> = VoiceManager.defaultPersonas,
    val selectedVoicePersona: HindiVoicePersona? = VoiceManager.defaultPersonas.firstOrNull(),
    val showVoiceProfilesDialog: Boolean = false,
    val previewingPersonaId: String? = null,
    val syncMetrics: SyncMetrics = SyncMetrics(),
    val isAiDiarizationEnabled: Boolean = false,
    val showSpeakerDialog: Boolean = false,
    val genderVoiceMode: GenderVoiceMode = GenderVoiceMode.FORCE_NARRATOR,
    val showGenderDialog: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.HINDI,
    val showLanguageSelectorDialog: Boolean = false,
    val srtLanguageDetection: SrtLanguageDetection? = null,
    val showLanguageWarningBanner: Boolean = false,
    val showLanguageAnalysisDialog: Boolean = false,
    val appThemeMode: AppThemeMode = AppThemeMode.DARK,
    val showThemeDialog: Boolean = false,
    val currentProjectId: Long? = null,
    val currentProjectTitle: String = "Office Meeting Dubbing Project",
    val savedProjects: List<DubbingProject> = emptyList(),
    val showProjectsDialog: Boolean = false,
    val showSaveProjectDialog: Boolean = false,
    val lastRemovedProject: DubbingProject? = null,
    val projectRestoredNotice: String? = null,
    val savedSrtFiles: List<SrtFileEntity> = emptyList(),
    val activeSrtFileId: Long? = null,
    val showSrtLibraryDialog: Boolean = false,
    val showSubtitleExportDialog: Boolean = false,
    val srtHistoryList: List<SrtHistoryEntity> = emptyList(),
    val showSrtHistoryDialog: Boolean = false,
    val srtHistoryFilter: SrtHistorySource? = null,
    val showSubtitleSyncDialog: Boolean = false,
    val isTranslatingSrt: Boolean = false,
    val translationProgressMessage: String = "",
    val translationError: String? = null,
    val autoTranslateNonHindiOnLoad: Boolean = true,
    val originalSrtBackup: String? = null,
    val isTranslatedWithGemini: Boolean = false,
    val customGeminiApiKey: String = "",
    val showApiKeyDialog: Boolean = false,
    val showTranslationConfirmationDialog: Boolean = false,
    val showAutoTranslatePromptDialog: Boolean = false,
    val enableAutoTranslatePromptBeforePlayback: Boolean = true,
    val hasDismissedAutoTranslatePromptForCurrentSrt: Boolean = false,
    val autoStartPlaybackAfterTranslation: Boolean = true,
    val showFriendlyHelpDialog: Boolean = false,
    val showGestureOverlay: Boolean = false,
    val isWelcomeBannerDismissed: Boolean = false,
    val deviceVideos: List<MxVideoItem> = emptyList(),
    val isScanningVideos: Boolean = false,
    val hasVideoPermission: Boolean = false,
    val videoScanError: String? = null,
    val includeSampleDemos: Boolean = false,
    val showVideoExportDialog: Boolean = false,
    val isExportingVideo: Boolean = false,
    val videoExportProgress: Float = 0f,
    val videoExportStatus: String = "",
    val exportedVideoFilePath: String? = null,
    val exportedSrtFilePath: String? = null,
    val videoExportError: String? = null,
    val isRenderingExportPreview: Boolean = false,
    val exportPreviewVideoPath: String? = null,
    val exportPreviewDurationSeconds: Int = 6,
    val isExportPreviewPlaying: Boolean = false,
    val exportPreviewStatus: String = "",
    val selectedBrowserFolder: String? = null,
    val searchQuery: String = "",
    val isFolderGridView: Boolean = false,
    val isSearchingVideos: Boolean = false,
    val showAudioBoostDialog: Boolean = false,
    val showSpeedMenu: Boolean = false,
    val showSubtitleSyncSlider: Boolean = false
)

val TtsUiState.hasAnyOpenDialog: Boolean
    get() = showAddSubtitleDialog ||
            showVideoExportDialog ||
            showSrtHistoryDialog ||
            showThemeDialog ||
            showFriendlyHelpDialog ||
            showAudioBoostDialog ||
            showGestureOverlay ||
            showProjectsDialog ||
            showSaveProjectDialog ||
            showSubtitleSyncDialog ||
            showVolumeMixerDialog ||
            showVoiceProfilesDialog ||
            showSpeakerDialog ||
            showGenderDialog ||
            showLanguageAnalysisDialog ||
            showSubtitleAppearanceDialog ||
            showAutoTranslatePromptDialog ||
            showPerfectTimingDialog ||
            showDecoderDialog ||
            showVideoInfoDialog ||
            showSleepTimerDialog ||
            showSubtitleExportDialog ||
            showZoomControlsDialog ||
            showVideoSoundTrackDialog ||
            showOfflineSubtitleReaderDialog ||
            showBgmTrackMixerDialog ||
            showLanguageSelectorDialog ||
            showSrtLibraryDialog ||
            showApiKeyDialog ||
            showTranslationConfirmationDialog ||
            showOrientationDialog
