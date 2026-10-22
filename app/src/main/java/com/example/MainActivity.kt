package com.example

import android.content.Context
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.theme.MxBluePrimary
import com.example.ui.theme.MxBlueSecondary
import com.example.ui.theme.MyApplicationTheme
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var exoDecoderMode: DecoderMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen(
                    onGetExoPlayer = { ctx, mode -> getOrBuildPlayer(ctx, mode) },
                    onReleaseExoPlayer = { releaseExoPlayer() },
                    onSetOrientation = { lock ->
                        // MX-style behavior: AUTO follows the motion sensor even when the
                        // system rotation is locked; explicit modes use sensor variants so
                        // both landscape orientations keep working.
                        requestedOrientation = when (lock) {
                            VideoOrientationLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            VideoOrientationLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            VideoOrientationLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                        }
                    }
                )
            }
        }
    }

    /**
     * Builds (or reuses) the ExoPlayer for the requested decoder mode.
     * HW/HW+ use hardware codecs; SW forces software codecs
     * (OMX.google.* / c2.android.*) so broken-HW videos still play.
     */
    private fun getOrBuildPlayer(context: Context, mode: DecoderMode): ExoPlayer {
        val current = exoPlayer
        if (current != null && exoDecoderMode == mode) return current
        current?.release()
        return buildExoPlayer(context, mode).also { player ->
            exoPlayer = player
            exoDecoderMode = mode
        }
    }

    private fun buildExoPlayer(context: Context, mode: DecoderMode): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            when (mode) {
                DecoderMode.HW -> {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                }
                DecoderMode.HW_PLUS -> {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    setEnableDecoderFallback(true)
                }
                DecoderMode.SW -> {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                    setEnableDecoderFallback(true)
                    setMediaCodecSelector(SoftwarePreferredSelector)
                }
            }
        }
        return ExoPlayer.Builder(context, renderersFactory).build().also { player ->
            player.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    private fun initExoPlayer(context: Context): ExoPlayer {
        return getOrBuildPlayer(context, DecoderMode.HW)
    }

    private fun releaseExoPlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseExoPlayer()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    ttsViewModel: TtsViewModel = viewModel(),
    onGetExoPlayer: (Context, DecoderMode) -> ExoPlayer,
    onReleaseExoPlayer: () -> Unit,
    onSetOrientation: (VideoOrientationLock) -> Unit
) {
    val context = LocalContext.current
    val uiState by ttsViewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Subtitle load result/error feedback (statusMessage is not rendered anywhere).
    LaunchedEffect(uiState.subtitleToast) {
        uiState.subtitleToast?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            ttsViewModel.consumeSubtitleToast()
        }
    }

    // Returning from the system TTS installer: re-scan installed voices so the
    // newly downloaded Hindi offline voice is picked up immediately.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                ttsViewModel.refreshVoiceData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showMenuDropdown by remember { mutableStateOf(false) }
    var selectedVideoForInfo by remember { mutableStateOf<MxVideoItem?>(null) }

    // Media permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            ttsViewModel.scanDeviceVideos()
        }
    }

    // Subtitle File Picker (.srt, .vtt, .txt, .ass)
    val srtPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            // Reading/parsing happens inside the ViewModel off the main thread;
            // 4 MB+ subtitle files previously froze the UI here.
            val fileName = it.lastPathSegment ?: "imported.srt"
            Toast.makeText(context, "Loading subtitle: $fileName", Toast.LENGTH_SHORT).show()
            ttsViewModel.loadSrtFromUri(it, fileName)
        }
    }

    // Video File Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val title = it.lastPathSegment ?: "Local Video"
            ttsViewModel.selectDeviceVideo(
                MxVideoItem(
                    id = "picked_${System.currentTimeMillis()}",
                    title = title,
                    folderName = "Imported",
                    uriString = it.toString(),
                    durationMs = 60_000L
                )
            )
        }
    }

    // Handle orientation lock
    LaunchedEffect(uiState.orientationLock) {
        onSetOrientation(uiState.orientationLock)
    }

    // True fullscreen: hide status + navigation bars in fullscreen / landscape
    // player so video fills the whole screen; restore them otherwise.
    val activity = context as? Activity
    LaunchedEffect(uiState.isFullscreen, isLandscape, uiState.currentTab) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val immersive = uiState.isFullscreen ||
            (isLandscape && uiState.currentTab == AppTab.VIDEO_PLAYER)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Keep screen ON while video is playing so the display never sleeps
    // mid-playback; cleared on pause/stop to save battery.
    LaunchedEffect(uiState.isVideoPlaying) {
        val window = activity?.window ?: return@LaunchedEffect
        if (uiState.isVideoPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val isFullPlayerMode = (isLandscape || uiState.isFullscreen) && uiState.currentTab == AppTab.VIDEO_PLAYER

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            if (!isFullPlayerMode) {
                TopAppBar(
                    title = {
                        if (uiState.isSearchingVideos) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = { ttsViewModel.setSearchQuery(it) },
                                placeholder = { Text("Search folders and videos...", fontSize = 14.sp) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("search_input")
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MxBluePrimary,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "SRT Video Player",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Multi-Language Dub Studio",
                                        fontSize = 10.sp,
                                        color = MxBluePrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        // Search Toggle
                        IconButton(
                            onClick = { ttsViewModel.toggleSearchingVideos() },
                            modifier = Modifier.testTag("toggle_search_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.isSearchingVideos) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search"
                            )
                        }

                        // Import Subtitle
                        IconButton(
                            onClick = { srtPickerLauncher.launch("*/*") },
                            modifier = Modifier.testTag("import_srt_btn")
                        ) {
                            Icon(Icons.Default.Subtitles, contentDescription = "Import Subtitles")
                        }

                        // Overflow Menu
                        IconButton(
                            onClick = { showMenuDropdown = true },
                            modifier = Modifier.testTag("overflow_menu_btn")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }

                        DropdownMenu(
                            expanded = showMenuDropdown,
                            onDismissRequest = { showMenuDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("HW / SW Decoder Mode") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.showDecoderDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Memory, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Pick Local Video File") },
                                onClick = {
                                    showMenuDropdown = false
                                    videoPickerLauncher.launch("video/*")
                                },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("200% Audio Boost") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.showAudioBoostDialog(true)
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sleep Timer") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.showSleepTimerDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Theme Settings") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.showThemeDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("App Language") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.showLanguageSelectorDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Gemini API Key") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.setShowApiKeyDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Gestures & User Guide") },
                                onClick = {
                                    showMenuDropdown = false
                                    ttsViewModel.showFriendlyHelpDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            if (!isFullPlayerMode) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("mx_bottom_navigation")
                ) {
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.LOCAL_FOLDERS,
                        onClick = { ttsViewModel.setAppTab(AppTab.LOCAL_FOLDERS) },
                        icon = { Icon(Icons.Default.Folder, contentDescription = "Folders") },
                        label = { Text("Folders") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MxBluePrimary,
                            selectedTextColor = MxBluePrimary,
                            indicatorColor = MxBluePrimary.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.ALL_VIDEOS,
                        onClick = { ttsViewModel.setAppTab(AppTab.ALL_VIDEOS) },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = "Videos") },
                        label = { Text("Videos") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MxBluePrimary,
                            selectedTextColor = MxBluePrimary,
                            indicatorColor = MxBluePrimary.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.VIDEO_PLAYER,
                        onClick = { ttsViewModel.setAppTab(AppTab.VIDEO_PLAYER) },
                        icon = { Icon(Icons.Default.PlayCircle, contentDescription = "Player") },
                        label = { Text("Player") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MxBluePrimary,
                            selectedTextColor = MxBluePrimary,
                            indicatorColor = MxBluePrimary.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.SUBTITLE_TTS_READER,
                        onClick = { ttsViewModel.setAppTab(AppTab.SUBTITLE_TTS_READER) },
                        icon = { Icon(Icons.Default.Subtitles, contentDescription = "Subtitles") },
                        label = { Text("Dubbing") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MxBluePrimary,
                            selectedTextColor = MxBluePrimary,
                            indicatorColor = MxBluePrimary.copy(alpha = 0.15f)
                        )
                    )
                    NavigationBarItem(
                        selected = uiState.currentTab == AppTab.DUBBING_PROJECTS,
                        onClick = { ttsViewModel.setAppTab(AppTab.DUBBING_PROJECTS) },
                        icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "Projects") },
                        label = { Text("Projects") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MxBluePrimary,
                            selectedTextColor = MxBluePrimary,
                            indicatorColor = MxBluePrimary.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState.currentTab) {
                AppTab.LOCAL_FOLDERS -> {
                    MxFolderBrowserScreen(
                        uiState = uiState,
                        onSelectVideo = { video ->
                            ttsViewModel.selectDeviceVideo(video)
                        },
                        onSelectFolder = { folder ->
                            ttsViewModel.setSelectedBrowserFolder(folder)
                        },
                        onToggleGridView = {
                            ttsViewModel.toggleFolderGridView()
                        },
                        onShowVideoInfo = { video ->
                            selectedVideoForInfo = video
                            ttsViewModel.showVideoInfoDialog(true)
                        },
                        onRefreshVideos = {
                            val perms = LocalMediaScanner.getRequiredPermissions()
                            permissionLauncher.launch(perms)
                            ttsViewModel.scanDeviceVideos()
                        }
                    )
                }

                AppTab.ALL_VIDEOS -> {
                    MxAllVideosScreen(
                        uiState = uiState,
                        onSelectVideo = { video ->
                            ttsViewModel.selectDeviceVideo(video)
                        },
                        onShowVideoInfo = { video ->
                            selectedVideoForInfo = video
                            ttsViewModel.showVideoInfoDialog(true)
                        }
                    )
                }

                AppTab.VIDEO_PLAYER -> {
                    MxVideoPlayerScreen(
                        uiState = uiState,
                        ttsViewModel = ttsViewModel,
                        onGetExoPlayer = onGetExoPlayer,
                        isLandscape = isLandscape,
                        onPickSrt = { srtPickerLauncher.launch("*/*") },
                        onExportVideo = { ttsViewModel.showVideoExportDialog(true) },
                        onBackToFolders = {
                            ttsViewModel.setAppTab(AppTab.LOCAL_FOLDERS)
                        }
                    )
                }

                AppTab.SUBTITLE_TTS_READER -> {
                    SubtitleDubbingStudioScreen(
                        uiState = uiState,
                        ttsViewModel = ttsViewModel,
                        onPickSrt = { srtPickerLauncher.launch("*/*") }
                    )
                }

                AppTab.DUBBING_PROJECTS -> {
                    ProjectsOverviewScreen(
                        uiState = uiState,
                        ttsViewModel = ttsViewModel
                    )
                }
            }
        }
    }

    // --- All SRT Player Dialogs ---

    if (uiState.showLanguageSelectorDialog) {
        MultiLanguageSelectorDialog(
            currentLanguage = uiState.appLanguage,
            onSelectLanguage = { lang ->
                ttsViewModel.setAppLanguage(lang)
            },
            onDismiss = { ttsViewModel.showLanguageSelectorDialog(false) }
        )
    }

    if (uiState.showDecoderDialog) {
        MxDecoderDialog(
            currentMode = uiState.decoderMode,
            onSelectMode = { mode ->
                ttsViewModel.setDecoderMode(mode)
            },
            onDismiss = { ttsViewModel.showDecoderDialog(false) }
        )
    }

    if (uiState.showVideoExportDialog) {
        VideoExportDialog(
            uiState = uiState,
            onStartExport = { ttsViewModel.startVideoExport() },
            onCancelExport = { ttsViewModel.cancelVideoExport() },
            onShareVideo = { ttsViewModel.shareExportedVideo() },
            onDismiss = { ttsViewModel.dismissExportDialog() }
        )
    }

    if (uiState.showVideoInfoDialog) {
        MxVideoInfoDialog(
            uiState = uiState,
            onDismiss = { ttsViewModel.showVideoInfoDialog(false) }
        )
    }

    if (uiState.showSleepTimerDialog) {
        MxSleepTimerDialog(
            currentMinutes = uiState.sleepTimerMinutes,
            remainingSeconds = uiState.sleepTimerRemainingSeconds,
            onSetTimer = { mins ->
                ttsViewModel.setSleepTimerMinutes(mins)
            },
            onDismiss = { ttsViewModel.showSleepTimerDialog(false) }
        )
    }

    if (uiState.showAudioBoostDialog) {
        MxAudioBoostDialog(
            boostPercent = uiState.volumeBoostPercent,
            onBoostChanged = { boost ->
                ttsViewModel.setVolumeBoostPercent(boost)
            },
            onDismiss = { ttsViewModel.showAudioBoostDialog(false) }
        )
    }

    if (uiState.showThemeDialog) {
        MxThemeSettingsDialog(
            currentThemeMode = uiState.appThemeMode,
            onSelectThemeMode = { theme ->
                ttsViewModel.setAppThemeMode(theme)
                ttsViewModel.showThemeDialog(false)
            },
            onDismiss = { ttsViewModel.showThemeDialog(false) }
        )
    }

    if (uiState.showFriendlyHelpDialog) {
        MxFriendlyHelpDialog(
            onShowGestures = {
                ttsViewModel.showFriendlyHelpDialog(false)
                ttsViewModel.showGestureVisualizer(true)
            },
            onDismiss = { ttsViewModel.showFriendlyHelpDialog(false) }
        )
    }

    if (uiState.showGestureOverlay) {
        MxGestureVisualizerOverlay(
            onDismiss = { ttsViewModel.showGestureVisualizer(false) }
        )
    }

    if (uiState.showOrientationDialog) {
        OrientationLockDialog(
            currentMode = uiState.orientationLock,
            onSelectMode = { lock ->
                ttsViewModel.setVideoOrientationLock(lock)
                ttsViewModel.showOrientationDialog(false)
            },
            onDismiss = { ttsViewModel.showOrientationDialog(false) }
        )
    }

    if (uiState.showVideoSoundTrackDialog) {
        VideoSoundTrackDialog(
            uiState = uiState,
            onSelectAudioTrackMode = { ttsViewModel.setActiveAudioTrackMode(it) },
            onSelectSoundTrackItem = { ttsViewModel.selectVideoSoundTrack(it) },
            onVolumeChanged = { ttsViewModel.setVideoVolume(it) },
            onToggleMute = { ttsViewModel.toggleVideoMute() },
            onSyncDelayChanged = { ttsViewModel.setSoundTrackSyncDelayMs(it) },
            onToggleVocalClarity = { ttsViewModel.toggleVocalClarityBoost() },
            onOpenVoiceProfiles = { ttsViewModel.showVoiceProfilesDialog(true) },
            onTestSoundTrack = { ttsViewModel.testSoundTrack() },
            onDismiss = { ttsViewModel.showVideoSoundTrackDialog(false) }
        )
    }

    if (uiState.showPerfectTimingDialog) {
        PerfectDialogueTimingDialog(
            uiState = uiState,
            onLeadTimeChange = { ttsViewModel.setDialogueLeadTimeMs(it) },
            onTogglePerfectTiming = { ttsViewModel.togglePerfectTiming(it) },
            onToggleDynamicRate = { ttsViewModel.toggleDynamicRateAutoFit(it) },
            onCalibrateTiming = { ttsViewModel.calibrateDialogueTiming() },
            onTestSpokenTiming = { ttsViewModel.speakText(it) },
            onDismiss = { ttsViewModel.showPerfectTimingDialog(false) }
        )
    }

    if (uiState.showGenderDialog) {
        MxGenderVoiceDialog(
            currentMode = uiState.genderVoiceMode,
            onSelectMode = { ttsViewModel.setGenderVoiceMode(it) },
            onDismiss = { ttsViewModel.showGenderDialog(false) }
        )
    }

    if (uiState.showZoomControlsDialog) {
        FitToScreenZoomDialog(
            uiState = uiState,
            onDismiss = { ttsViewModel.showZoomControlsDialog(false) },
            onZoomChange = { ttsViewModel.setVideoZoomScale(it) },
            onResetFitToScreen = { ttsViewModel.resetVideoZoom() },
            onModeSelect = { ttsViewModel.setAspectRatioMode(it) }
        )
    }

    if (uiState.showOfflineSubtitleReaderDialog) {
        SubtitleReaderOptionsDialog(
            uiState = uiState,
            onSelectLanguage = { ttsViewModel.setAppLanguage(it) },
            onToggleOfflineVoice = { ttsViewModel.togglePreferOfflineVoice() },
            onToggleContinuous = { ttsViewModel.toggleSubtitleReaderContinuous() },
            onDelayChange = { ttsViewModel.setSubtitleReaderDelay(it) },
            onStartReader = { ttsViewModel.startSubtitleReader() },
            onStopReader = { ttsViewModel.stopSubtitleReader() },
            onTestVoice = { ttsViewModel.testVoiceOutput() },
            onInstallHindiVoice = { ttsViewModel.showHindiVoiceSetupDialog(true) },
            onOpenVoiceProfiles = {
                ttsViewModel.showOfflineSubtitleReaderDialog(false)
                ttsViewModel.showVoiceProfilesDialog(true)
            },
            onDismiss = { ttsViewModel.showOfflineSubtitleReaderDialog(false) }
        )
    }

    if (uiState.showHindiVoiceSetupDialog) {
        HindiVoiceSetupDialog(
            uiState = uiState,
            onInstallVoiceData = { ttsViewModel.openTtsInstallData() },
            onOpenTtsSettings = { ttsViewModel.openTtsSettings() },
            onOpenPlayStore = { ttsViewModel.openGoogleTtsStore() },
            onRefresh = { ttsViewModel.refreshVoiceDataWithFeedback() },
            onDismiss = { ttsViewModel.showHindiVoiceSetupDialog(false) }
        )
    }

    if (uiState.showSpeedMenu) {
        AlertDialog(
            onDismissRequest = { ttsViewModel.showSpeedMenu(false) },
            confirmButton = {},
            containerColor = Color.Transparent,
            text = {
                PlayerSpeedControlMenu(
                    currentSpeed = uiState.playbackSpeed,
                    onSpeedSelected = { speed ->
                        ttsViewModel.setPlaybackSpeed(speed)
                        ttsViewModel.showSpeedMenu(false)
                    },
                    onAdjustSpeed = { delta ->
                        ttsViewModel.setPlaybackSpeed((uiState.playbackSpeed + delta).coerceIn(0.25f, 4.0f))
                    },
                    onClose = { ttsViewModel.showSpeedMenu(false) },
                    isDarkOverlay = true
                )
            }
        )
    }

    if (uiState.showSubtitleSyncSlider) {
        AlertDialog(
            onDismissRequest = { ttsViewModel.showSubtitleSyncSlider(false) },
            confirmButton = {},
            containerColor = Color.Transparent,
            text = {
                PlayerSubtitleDelayOverlayControl(
                    uiState = uiState,
                    onOffsetChange = { ttsViewModel.setSubtitleOffset(it) },
                    onAdjustOffset = { ttsViewModel.adjustSubtitleOffset(it) },
                    onResetOffset = { ttsViewModel.setSubtitleOffset(0L) },
                    onClose = { ttsViewModel.showSubtitleSyncSlider(false) },
                    onOpenFullDialog = {
                        ttsViewModel.showSubtitleSyncSlider(false)
                        ttsViewModel.showSubtitleSyncDialog(true)
                    }
                )
            }
        )
    }

    if (uiState.showSubtitleSyncDialog) {
        AlertDialog(
            onDismissRequest = { ttsViewModel.showSubtitleSyncDialog(false) },
            confirmButton = {},
            text = {
                SubtitleSyncSettingsPanel(
                    uiState = uiState,
                    viewModel = ttsViewModel,
                    onClose = { ttsViewModel.showSubtitleSyncDialog(false) }
                )
            }
        )
    }

    if (uiState.showSrtHistoryDialog) {
        SrtHistoryDialog(
            entries = uiState.srtHistoryList,
            onLoadEntry = { ttsViewModel.loadHistoryEntry(it) },
            onDeleteEntry = { ttsViewModel.deleteHistoryEntry(it) },
            onClearAll = { ttsViewModel.clearSrtHistory() },
            onDismiss = { ttsViewModel.showSrtHistoryDialog(false) }
        )
    }

    if (uiState.showVolumeMixerDialog) {
        VolumeMixerDialog(
            uiState = uiState,
            viewModel = ttsViewModel,
            onDismissRequest = { ttsViewModel.showVolumeMixerDialog(false) }
        )
    }

    if (uiState.showSubtitleAppearanceDialog) {
        SubtitleAppearanceDialog(
            fontSize = uiState.subtitleFontSize,
            theme = uiState.subtitleTheme,
            backgroundOpacity = uiState.subtitleBackgroundOpacity,
            alignment = uiState.subtitleAlignment,
            showSubtitlesOnVideo = uiState.showSubtitlesOnVideo,
            isTranslatedToHindi = uiState.isTranslatedWithGemini,
            currentSrtFileName = uiState.currentSrtFileName,
            isTranslatingSrt = uiState.isTranslatingSrt,
            subtitleFormat = uiState.detectedSubtitleFormat,
            onFontSizeChange = { ttsViewModel.setSubtitleFontSize(it) },
            onThemeChange = { ttsViewModel.setSubtitleTheme(it) },
            onBackgroundOpacityChange = { ttsViewModel.setSubtitleBackgroundOpacity(it) },
            onAlignmentChange = { ttsViewModel.setSubtitleAlignment(it) },
            onToggleShowSubtitles = { ttsViewModel.toggleShowSubtitlesOnVideo() },
            onResetDefaults = { ttsViewModel.resetSubtitleAppearanceDefaults() },
            onImportSrt = {
                ttsViewModel.showSubtitleAppearanceDialog(false)
                srtPickerLauncher.launch("*/*")
            },
            onDismiss = { ttsViewModel.showSubtitleAppearanceDialog(false) }
        )
    }

    if (uiState.showAutoTranslatePromptDialog) {
        AutoTranslatePromptDialog(
            uiState = uiState,
            onTranslateWithGemini = { dontAsk ->
                if (dontAsk) ttsViewModel.setAutoTranslateNonHindiOnLoad(true)
                ttsViewModel.translateCurrentSrtToHindi()
            },
            onTranslateOffline = { dontAsk ->
                if (dontAsk) ttsViewModel.setAutoTranslateNonHindiOnLoad(true)
                ttsViewModel.translateCurrentSrtToHindi()
            },
            onPlayOriginal = { dontAsk ->
                if (dontAsk) ttsViewModel.setAutoTranslateNonHindiOnLoad(false)
            },
            onDismiss = {}
        )
    }

    if (uiState.showProjectsDialog) {
        DubbingProjectsDialog(
            savedProjects = uiState.savedProjects,
            currentProjectId = uiState.currentProjectId,
            lastRemovedProject = uiState.lastRemovedProject,
            onDismiss = { ttsViewModel.showProjectsDialog(false) },
            onLoadProject = { ttsViewModel.loadProject(it) },
            onDeleteProject = { ttsViewModel.deleteProject(it) },
            onRestoreLastProject = { ttsViewModel.restoreLastRemovedProject() },
            onRestoreSampleProjects = { ttsViewModel.restoreSampleProjects() },
            onOpenSaveDialog = { ttsViewModel.showSaveProjectDialog(true) }
        )
    }

    if (uiState.showSaveProjectDialog) {
        SaveDubbingProjectDialog(
            initialTitle = uiState.currentProjectTitle,
            videoTitle = uiState.videoTitle,
            subtitleCount = uiState.parsedSubtitles.size,
            onDismiss = { ttsViewModel.showSaveProjectDialog(false) },
            onConfirmSave = { title -> ttsViewModel.saveCurrentProject(title) }
        )
    }

    if (uiState.showApiKeyDialog) {
        GeminiApiKeyDialog(
            uiState = uiState,
            viewModel = ttsViewModel,
            onDismissRequest = { ttsViewModel.setShowApiKeyDialog(false) }
        )
    }

    if (uiState.showVoiceProfilesDialog) {
        HindiVoiceProfileBottomSheet(
            selectedPersona = uiState.selectedVoicePersona,
            availablePersonas = uiState.speakerPersonas,
            availableVoices = uiState.voices,
            selectedVoice = uiState.selectedVoice,
            previewingPersonaId = uiState.previewingPersonaId,
            speedProgress = uiState.speedProgress,
            pitchProgress = uiState.pitchProgress,
            onSpeedChanged = { ttsViewModel.setSpeechSpeed(it) },
            onPitchChanged = { ttsViewModel.setSpeechPitch(it) },
            onSelectPersona = { ttsViewModel.selectVoicePersona(it) },
            onSelectSystemVoice = { ttsViewModel.setVoice(it) },
            onPreviewVoice = { persona ->
                persona?.let { ttsViewModel.previewPersona(it) }
            },
            onPreviewSystemVoice = { voice ->
                ttsViewModel.testVoiceSample(voice)
            },
            onStopPreview = { ttsViewModel.stopSpeaking() },
            onDismiss = { ttsViewModel.showVoiceProfilesDialog(false) }
        )
    }
}

@Composable
fun SubtitleDubbingStudioScreen(
    uiState: TtsUiState,
    ttsViewModel: TtsViewModel,
    onPickSrt: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = uiState.currentSrtFileName ?: "No Subtitle Loaded",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.parsedSubtitles.size} dialogue lines • ${uiState.detectedSubtitleFormat.name}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onPickSrt,
                        colors = ButtonDefaults.buttonColors(containerColor = MxBluePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Import .SRT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { ttsViewModel.translateCurrentSrtToHindi() },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Translate", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { ttsViewModel.showSubtitleAppearanceDialog(true) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Appearance", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { ttsViewModel.showVoiceProfilesDialog(true) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Voice", fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { ttsViewModel.showOfflineSubtitleReaderDialog(true) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dubbing_reader_options_btn")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (uiState.isSubtitleReaderPlaying) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (uiState.isSubtitleReaderPlaying) "Reading... (Options)" else "Reader Options (${uiState.appLanguage.nativeName})",
                            fontSize = 11.sp
                        )
                    }
                    if (uiState.isSubtitleReaderPlaying) {
                        OutlinedButton(
                            onClick = { ttsViewModel.stopSubtitleReader() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Stop Reader", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Subtitle Dialogue Cues (${uiState.parsedSubtitles.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(uiState.parsedSubtitles) { cue ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatVideoTimestamp(cue.startTimeMs)} --> ${formatVideoTimestamp(cue.endTimeMs)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MxBluePrimary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(text = cue.text, fontSize = 13.sp)
                        }
                        IconButton(onClick = { ttsViewModel.speakSubtitleCue(cue) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak", tint = MxBluePrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectsOverviewScreen(
    uiState: TtsUiState,
    ttsViewModel: TtsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dubbing Projects",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "${uiState.savedProjects.size} saved projects",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { ttsViewModel.showSaveProjectDialog(true) },
                colors = ButtonDefaults.buttonColors(containerColor = MxBluePrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Current", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (uiState.savedProjects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = MxBluePrimary.copy(alpha = 0.5f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No saved dubbing projects yet", fontWeight = FontWeight.Bold)
                    Text("Save your active video and subtitle settings for instant reload", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = { ttsViewModel.restoreSampleProjects() }) {
                        Text("Add Sample Project")
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.savedProjects) { project ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = project.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${project.videoTitle ?: "Video"} • ${project.subtitleCount} subtitles",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { ttsViewModel.loadProject(project) }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Load", tint = MxBluePrimary)
                            }
                            IconButton(onClick = { ttsViewModel.deleteProject(project) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }
        }
    }
}
