package com.example

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.ui.theme.MxBluePrimary
import com.example.ui.theme.MxBlueSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@androidx.compose.material3.ExperimentalMaterial3Api
@OptIn(UnstableApi::class)
@Composable
fun MxVideoPlayerScreen(
    uiState: TtsUiState,
    ttsViewModel: TtsViewModel,
    onGetExoPlayer: (Context, DecoderMode) -> ExoPlayer,
    isLandscape: Boolean,
    onPickSrt: () -> Unit = {},
    onExportVideo: () -> Unit = {},
    onBackToFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFullPlayerMode = isLandscape || uiState.isFullscreen
    var selectedBelowTab by remember { mutableStateOf(0) } // 0: Folder Playlist, 1: Subtitle Cues, 2: Audio Dubbing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // --- Video Container Box ---
        MxPlayerSurfaceBox(
            uiState = uiState,
            ttsViewModel = ttsViewModel,
            onGetExoPlayer = onGetExoPlayer,
            onPickSrt = onPickSrt,
            onExportVideo = onExportVideo,
            onBackToFolders = onBackToFolders,
            modifier = Modifier
                .fillMaxWidth()
                .weight(if (isFullPlayerMode) 1f else 0.42f)
        )

        // --- Portrait Content Below Video (Playlist, Subtitle Script, Audio Dubbing) ---
        if (!isFullPlayerMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Secondary Tab Header
                PrimaryTabRow(
                    selectedTabIndex = selectedBelowTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MxBluePrimary
                ) {
                    Tab(
                        selected = selectedBelowTab == 0,
                        onClick = { selectedBelowTab = 0 },
                        text = { Text("Playlist", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedBelowTab == 1,
                        onClick = { selectedBelowTab = 1 },
                        text = { Text("Subtitles", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.Subtitles, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedBelowTab == 2,
                        onClick = { selectedBelowTab = 2 },
                        text = { Text("AI Dubbing", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                        icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                when (selectedBelowTab) {
                    0 -> {
                        // Videos in current folder
                        val folderVideos = uiState.deviceVideos.filter {
                            it.folderName == uiState.deviceVideos.firstOrNull { v -> v.uriString == uiState.videoUri }?.folderName
                        }.ifEmpty { uiState.deviceVideos }

                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("player_folder_playlist")
                        ) {
                            items(folderVideos) { video ->
                                val isCurrent = video.uriString == uiState.videoUri
                                Surface(
                                    onClick = { ttsViewModel.selectDeviceVideo(video) },
                                    color = if (isCurrent) MxBluePrimary.copy(alpha = 0.12f) else Color.Transparent,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                            .fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrent) MxBluePrimary else Color(0xFF1E293B),
                                            modifier = Modifier.size(width = 68.dp, height = 44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (isCurrent && uiState.isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = video.title,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = if (isCurrent) MxBluePrimary else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${video.durationText} • ${video.resolution}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MxBluePrimary
                                            ) {
                                                Text(
                                                    text = "PLAYING",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Subtitle dialogue cue list
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${uiState.parsedSubtitles.size} dialogue cues",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    TextButton(
                                        onClick = onPickSrt,
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("player_add_subtitle_btn")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add", fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = { ttsViewModel.translateCurrentSrtToHindi() },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Translate Hindi", fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = { ttsViewModel.showSubtitleAppearanceDialog(true) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Style, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Style", fontSize = 11.sp)
                                    }
                                    TextButton(
                                        onClick = { ttsViewModel.showOfflineSubtitleReaderDialog(true) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("player_reader_options_btn")
                                    ) {
                                        Icon(
                                            Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (uiState.isSubtitleReaderPlaying) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            if (uiState.isSubtitleReaderPlaying) "Reading..." else "Reader",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            LazyColumn(
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(uiState.parsedSubtitles) { cue ->
                                    val isActive = uiState.activeSubtitle == cue
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, if (isActive) MxBluePrimary else Color.Transparent),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                ttsViewModel.seekTo(cue.startTimeMs)
                                                ttsViewModel.speakSubtitleCue(cue)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${formatVideoTimestamp(cue.startTimeMs)} - ${formatVideoTimestamp(cue.endTimeMs)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isActive) MxBluePrimary else MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = cue.text,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            IconButton(
                                                onClick = { ttsViewModel.speakSubtitleCue(cue) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = "Speak",
                                                    tint = MxBluePrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // AI Dubbing controls & Voice Personas
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "Voice Persona & Narration",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(uiState.speakerPersonas) { persona ->
                                    val isSelected = uiState.selectedVoicePersona?.id == persona.id
                                    Surface(
                                        onClick = { ttsViewModel.selectVoicePersona(persona) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MxBluePrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                                        border = BorderStroke(1.dp, if (isSelected) MxBluePrimary else Color.Transparent)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (persona.gender == SpeakerGender.FEMALE) "👩" else "👨",
                                                fontSize = 16.sp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = persona.displayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (isSelected) MxBluePrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = persona.gender.name,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Volume sliders
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Original Audio Volume", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${(uiState.videoVolume * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = uiState.videoVolume,
                                onValueChange = { ttsViewModel.setVideoVolume(it) },
                                colors = SliderDefaults.colors(thumbColor = MxBluePrimary, activeTrackColor = MxBluePrimary)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hindi Voice Dubbing Volume", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text("${(uiState.dubbingVolume * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = uiState.dubbingVolume,
                                onValueChange = { ttsViewModel.setDubbingVolume(it) },
                                colors = SliderDefaults.colors(thumbColor = Color(0xFF10B981), activeTrackColor = Color(0xFF10B981))
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { ttsViewModel.testDubbingVoice() },
                                colors = ButtonDefaults.buttonColors(containerColor = MxBluePrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test Hindi Voice Output", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun MxPlayerSurfaceBox(
    uiState: TtsUiState,
    ttsViewModel: TtsViewModel,
    onGetExoPlayer: (Context, DecoderMode) -> ExoPlayer,
    onPickSrt: () -> Unit = {},
    onExportVideo: () -> Unit = {},
    onBackToFolders: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showControls by remember { mutableStateOf(true) }
    var showLockedFeedback by remember { mutableStateOf(false) }
    var currentHudType by remember { mutableStateOf(GestureHudType.NONE) }
    var currentSeekDeltaMs by remember { mutableStateOf(0L) }
    var targetSeekPositionMs by remember { mutableStateOf(0L) }

    // Auto-hide player controls overlay after 4 seconds of inactivity
    LaunchedEffect(showControls, uiState.isVideoPlaying, uiState.isScreenLocked) {
        if (showControls && uiState.isVideoPlaying && !uiState.isScreenLocked) {
            delay(4000L)
            showControls = false
        }
    }

    // Dismiss screen locked badge feedback
    LaunchedEffect(showLockedFeedback) {
        if (showLockedFeedback) {
            delay(1800L)
            showLockedFeedback = false
        }
    }

    val gestureListener = remember(uiState.isScreenLocked, uiState.videoPositionMs, uiState.videoDurationMs) {
        object : PlayerGestureListener {
            override fun onSingleTap() {
                if (uiState.isScreenLocked) {
                    showLockedFeedback = true
                } else {
                    showControls = !showControls
                }
            }

            override fun onDoubleTapSeek(seekDeltaMs: Long, isForward: Boolean) {
                if (uiState.isScreenLocked) return
                val target = if (isForward) {
                    (uiState.videoPositionMs + seekDeltaMs).coerceAtMost(uiState.videoDurationMs)
                } else {
                    (uiState.videoPositionMs - seekDeltaMs).coerceAtLeast(0L)
                }
                ttsViewModel.seekTo(target)
            }

            override fun onDoubleTapPlayPause() {
                if (!uiState.isScreenLocked) {
                    ttsViewModel.togglePlayPause()
                }
            }

            override fun onBrightnessAdjust(delta: Float) {
                if (uiState.isScreenLocked) return
                val newLevel = (uiState.brightnessLevel + delta).coerceIn(0.05f, 1.0f)
                ttsViewModel.setBrightnessLevel(newLevel)
            }

            override fun onVolumeAdjust(delta: Float) {
                if (uiState.isScreenLocked) return
                val newVol = (uiState.videoVolume + delta).coerceIn(0f, 1.0f)
                ttsViewModel.setVideoVolume(newVol)
            }

            override fun onSeekGestureStart(currentPositionMs: Long) {
                targetSeekPositionMs = currentPositionMs
            }

            override fun onSeekGestureProgress(seekDeltaMs: Long, targetPositionMs: Long) {
                currentSeekDeltaMs = seekDeltaMs
                targetSeekPositionMs = targetPositionMs
            }

            override fun onSeekGestureCommit(targetPositionMs: Long) {
                ttsViewModel.seekTo(targetPositionMs)
            }

            override fun onGestureHudChanged(hudType: GestureHudType) {
                currentHudType = hudType
            }

            override fun onLockedScreenTap() {
                showLockedFeedback = true
            }
        }
    }

    val gestureController = rememberPlayerGestureController(listener = gestureListener)

    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        // Core ExoPlayer View
        ExoPlayerSurfaceView(
            uiState = uiState,
            ttsViewModel = ttsViewModel,
            onGetExoPlayer = onGetExoPlayer,
            modifier = Modifier.fillMaxSize()
        )

        // Subtitles on Video Display
        if (uiState.showSubtitlesOnVideo && uiState.activeSubtitle != null) {
            val cue = uiState.activeSubtitle!!
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = if (showControls) 64.dp else 24.dp),
                contentAlignment = when (uiState.subtitleAlignment) {
                    SubtitleAlignment.LEFT -> Alignment.BottomStart
                    SubtitleAlignment.CENTER -> Alignment.BottomCenter
                    SubtitleAlignment.RIGHT -> Alignment.BottomEnd
                }
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = uiState.subtitleBackgroundOpacity),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val analysis = remember(cue.text) { SpeakerDetector.analyzeLine(cue.text) }
                        if (analysis.detectedTag != null) {
                            Text(
                                text = "[${analysis.speaker.name}]",
                                color = Color(analysis.speaker.avatarColorHex),
                                fontSize = (uiState.subtitleFontSize - 4).coerceAtLeast(10).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = cue.text,
                            color = Color(uiState.subtitleTheme.textColorHex),
                            fontSize = uiState.subtitleFontSize.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = when (uiState.subtitleAlignment) {
                                SubtitleAlignment.LEFT -> TextAlign.Start
                                SubtitleAlignment.CENTER -> TextAlign.Center
                                SubtitleAlignment.RIGHT -> TextAlign.End
                            }
                        )
                    }
                }
            }
        }

        // Gesture Detection and HUDs Layer
        TouchGestureControllerOverlay(
            controller = gestureController,
            isScreenLocked = uiState.isScreenLocked,
            currentPositionMs = uiState.videoPositionMs,
            durationMs = uiState.videoDurationMs,
            targetPositionMs = targetSeekPositionMs,
            brightnessLevel = uiState.brightnessLevel,
            volumeLevel = uiState.videoVolume,
            gestureHudType = currentHudType,
            seekDeltaMs = currentSeekDeltaMs,
            modifier = Modifier.fillMaxSize()
        )

        // On-screen Add Subtitle button (visible until subtitles are loaded)
        if (uiState.parsedSubtitles.isEmpty() && showControls && !uiState.isScreenLocked) {
            Surface(
                onClick = onPickSrt,
                shape = RoundedCornerShape(24.dp),
                color = MxBluePrimary.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag("player_screen_add_subtitle_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add Subtitles (.srt)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Screen Lock Button (left edge)
        if (showControls || uiState.isScreenLocked) {            FilledIconButton(
                onClick = { ttsViewModel.toggleScreenLock() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (uiState.isScreenLocked) Color(0xFFEF4444).copy(alpha = 0.85f)
                    else Color.Black.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(44.dp)
                    .testTag("screen_lock_btn")
            ) {
                Icon(
                    imageVector = if (uiState.isScreenLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Screen Lock",
                    tint = Color.White
                )
            }
        }

        // Screen Locked Notice
        if (showLockedFeedback) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.88f),
                border = BorderStroke(1.5.dp, Color(0xFFEF4444)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Screen is Locked (Tap lock icon to unlock)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // --- Top Controls Bar (SRT Player styled) ---
        if (showControls && !uiState.isScreenLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackToFolders,
                        modifier = Modifier.testTag("player_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Text(
                        text = uiState.videoTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp)
                    )

                    // HW / HW+ / SW Decoder Mode Chip
                    Surface(
                        onClick = { ttsViewModel.showDecoderDialog(true) },
                        shape = RoundedCornerShape(6.dp),
                        color = MxBluePrimary.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MxBlueSecondary),
                        modifier = Modifier.testTag("decoder_chip_btn")
                    ) {
                        Text(
                            text = uiState.decoderMode.code,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Audio Track Selector
                    IconButton(
                        onClick = { ttsViewModel.showVolumeMixerDialog(true) },
                        modifier = Modifier.testTag("player_audio_track_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Audio Track",
                            tint = if (uiState.isAutoDubbingEnabled) Color(0xFF10B981) else Color.White
                        )
                    }

                    // Subtitle Track Button
                    IconButton(
                        onClick = { ttsViewModel.showSubtitleAppearanceDialog(true) },
                        modifier = Modifier.testTag("player_subtitle_track_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Subtitles",
                            tint = if (uiState.showSubtitlesOnVideo) MxBlueSecondary else Color.White.copy(alpha = 0.5f)
                        )
                    }

                    // Offline / Online voice toggle (screen icon)
                    IconButton(
                        onClick = { ttsViewModel.togglePreferOfflineVoice() },
                        modifier = Modifier.testTag("voice_mode_toggle_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.preferOfflineVoice) Icons.Default.CloudOff else Icons.Default.Cloud,
                            contentDescription = if (uiState.preferOfflineVoice) "Offline voice ON (tap for Online)" else "Online voice ON (tap for Offline)",
                            tint = if (uiState.preferOfflineVoice) Color(0xFF10B981) else Color(0xFFFBBF24)
                        )
                    }

                    // Aspect Ratio Cycle Button
                    Surface(
                        onClick = { ttsViewModel.cycleAspectRatio() },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.testTag("aspect_ratio_cycle_btn")
                    ) {
                        Text(
                            text = uiState.aspectRatioMode.shortName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    // Player 3-dots Menu
                    var showPlayerMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showPlayerMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showPlayerMenu,
                            onDismissRequest = { showPlayerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Subtitles (.srt)") },
                                onClick = {
                                    showPlayerMenu = false
                                    onPickSrt()
                                },
                                leadingIcon = { Icon(Icons.Default.Subtitles, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Video + ${uiState.appLanguage.nativeName} Audio") },
                                onClick = {
                                    showPlayerMenu = false
                                    onExportVideo()
                                },
                                leadingIcon = { Icon(Icons.Default.Movie, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.preferOfflineVoice) "Switch to Online Voice" else "Switch to Offline Voice") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.togglePreferOfflineVoice()
                                },
                                leadingIcon = { Icon(if (uiState.preferOfflineVoice) Icons.Default.Cloud else Icons.Default.CloudOff, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Video Properties") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showVideoInfoDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("200% Volume Boost") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showAudioBoostDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Sleep Timer") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showSleepTimerDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Screen Orientation") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showOrientationDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.ScreenRotation, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Playback Speed (${uiState.playbackSpeed}x)") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showSpeedMenu(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Subtitle Sync Offset") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showSubtitleSyncSlider(true)
                                },
                                leadingIcon = { Icon(Icons.Default.SyncAlt, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Subtitle Sync (Full)") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showSubtitleSyncDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Subtitle History") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showSrtHistoryDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Save Subtitles (.srt)") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.saveLoadedSubtitlesToDownloads()
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Subtitles") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.shareLoadedSubtitles()
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.isLoopModeEnabled) "Loop: ON" else "Loop: OFF") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.toggleLoopMode()
                                },
                                leadingIcon = { Icon(Icons.Default.Repeat, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Audio Tracks & Sound") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showVideoSoundTrackDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Audiotrack, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Perfect Dialogue Timing") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showPerfectTimingDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Voice Gender Mode") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showGenderDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Zoom & Fit to Screen") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showZoomControlsDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.CropFree, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Subtitle Reader Options") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showOfflineSubtitleReaderDialog(true)
                                },
                                leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Gesture Guide & Help") },
                                onClick = {
                                    showPlayerMenu = false
                                    ttsViewModel.showGestureVisualizer(true)
                                },
                                leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Controls Bar (SRT Player styled) ---
        if (showControls && !uiState.isScreenLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Time & Scrubber Slider Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatVideoTimestamp(uiState.videoPositionMs),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Slider(
                            value = if (uiState.videoDurationMs > 0) {
                                (uiState.videoPositionMs.toFloat() / uiState.videoDurationMs).coerceIn(0f, 1f)
                            } else 0f,
                            onValueChange = { frac ->
                                ttsViewModel.seekTo((frac * uiState.videoDurationMs).toLong())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("mx_player_scrubber"),
                            colors = SliderDefaults.colors(
                                thumbColor = MxBlueSecondary,
                                activeTrackColor = MxBluePrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                        Text(
                            text = formatVideoTimestamp(uiState.videoDurationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    // Controls Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Orientation toggle
                        IconButton(onClick = { ttsViewModel.showOrientationDialog(true) }) {
                            Icon(
                                imageVector = getOrientationIcon(uiState.orientationLock),
                                contentDescription = "Orientation",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Fullscreen toggle (portrait -> widescreen lock, tap again to exit)
                        IconButton(
                            onClick = { ttsViewModel.toggleFullscreen() },
                            modifier = Modifier.testTag("fullscreen_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (uiState.isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = if (uiState.isFullscreen) MxBlueSecondary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Prev Video
                        IconButton(
                            onClick = { ttsViewModel.playPrevVideo() },
                            modifier = Modifier.testTag("prev_video_btn")
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                        }

                        // Rewind 10s
                        IconButton(
                            onClick = { ttsViewModel.seekTo((uiState.videoPositionMs - 10000L).coerceAtLeast(0L)) },
                            modifier = Modifier.testTag("rewind_10s_btn")
                        ) {
                            Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                        }

                        // Large Play/Pause
                        FilledIconButton(
                            onClick = { ttsViewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(50.dp)
                                .testTag("main_play_pause_btn"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MxBluePrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (uiState.isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Forward 10s
                        IconButton(
                            onClick = { ttsViewModel.seekTo(uiState.videoPositionMs + 10000L) },
                            modifier = Modifier.testTag("forward_10s_btn")
                        ) {
                            Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                        }

                        // Next Video
                        IconButton(
                            onClick = { ttsViewModel.playNextVideo() },
                            modifier = Modifier.testTag("next_video_btn")
                        ) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                        }

                        // Repeat Mode Button
                        Surface(
                            onClick = { ttsViewModel.cycleRepeatMode() },
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.testTag("repeat_mode_btn")
                        ) {
                            Text(
                                text = uiState.repeatMode.shortLabel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerSurfaceView(
    uiState: TtsUiState,
    ttsViewModel: TtsViewModel,
    onGetExoPlayer: (Context, DecoderMode) -> ExoPlayer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Rebuild the player whenever the decoder mode changes (HW / HW+ / SW).
    val exoPlayer = remember(uiState.decoderMode) {
        onGetExoPlayer(context, uiState.decoderMode)
    }

    // Synchronize media URI (also reloads after a decoder-mode rebuild,
    // resuming at the saved position with the previous play state).
    LaunchedEffect(uiState.videoUri, exoPlayer) {
        val uri = uiState.videoUri
        if (!uri.isNullOrBlank()) {
            val resumeMs = uiState.videoPositionMs
            val autoPlay = uiState.isVideoPlaying
            exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.parse(uri)))
            exoPlayer.prepare()
            if (resumeMs > 0L) {
                try {
                    exoPlayer.seekTo(resumeMs)
                } catch (_: Exception) {
                }
            }
            exoPlayer.playWhenReady = autoPlay
        }
    }

    // Bridge Compose seek requests (slider / gestures / cue taps) to ExoPlayer.
    LaunchedEffect(uiState.pendingSeekMs) {
        val target = uiState.pendingSeekMs ?: return@LaunchedEffect
        try {
            exoPlayer.seekTo(target)
        } catch (_: Exception) {
        }
        ttsViewModel.consumePendingSeek()
    }

    // Adopt the real player clock: fixes drift between the simulated
    // ViewModel clock and actual playback, and picks up true duration.
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            delay(500L)
            try {
                val dur = exoPlayer.duration
                if (dur > 0L) {
                    ttsViewModel.syncExoProgress(exoPlayer.currentPosition, dur)
                }
            } catch (_: Exception) {
            }
        }
    }

    // Synchronize play/pause
    LaunchedEffect(uiState.isVideoPlaying) {
        if (uiState.isVideoPlaying) {
            exoPlayer.play()
        } else {
            exoPlayer.pause()
        }
    }

    // Synchronize volume
    LaunchedEffect(uiState.videoVolume, uiState.isVideoMuted) {
        exoPlayer.volume = if (uiState.isVideoMuted) 0f else uiState.videoVolume
    }

    // Synchronize playback speed
    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
    }

    // Synchronize loop mode
    LaunchedEffect(uiState.isLoopModeEnabled, uiState.repeatMode) {
        exoPlayer.repeatMode = when (uiState.repeatMode) {
            MxRepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            MxRepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            MxRepeatMode.REPEAT_OFF -> if (uiState.isLoopModeEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            MxRepeatMode.SHUFFLE -> Player.REPEAT_MODE_ALL
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false
                resizeMode = when (uiState.aspectRatioMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.CUSTOM_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
                layoutParams = android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { view ->
            view.resizeMode = when (uiState.aspectRatioMode) {
                AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioMode.CUSTOM_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            // Zoom & fit dialog drives real magnification on the surface.
            val zoom = uiState.videoZoomScale.coerceIn(1.0f, 5.0f)
            view.scaleX = zoom
            view.scaleY = zoom
            // Backup screen-on hold at view level (window flag is primary).
            view.keepScreenOn = uiState.isVideoPlaying
        },
        modifier = modifier.testTag("exo_player_view")
    )
}
