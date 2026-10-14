package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MxYellowSubtitle
import java.util.Locale

fun formatVideoTimestamp(ms: Long, forceHours: Boolean = false): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0 || forceHours) {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Composable
fun VideoPlayerHorizontalSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    isScrubbing: Boolean,
    scrubFraction: Float,
    onScrubStart: () -> Unit,
    onScrubProgress: (Float) -> Unit,
    onScrubFinished: (Long) -> Unit,
    showRemainingTime: Boolean,
    onToggleRemainingTime: () -> Unit,
    modifier: Modifier = Modifier,
    activeSubtitleText: String? = null,
    currentSubtitleIndex: Int? = null,
    subtitleStartTimeMs: Long? = null,
    subtitleEndTimeMs: Long? = null,
    showSubtitles: Boolean = true,
    onToggleSubtitles: (() -> Unit)? = null,
    onOpenSubtitleSettings: (() -> Unit)? = null,
    playbackSpeed: Float = 1.0f,
    onToggleSpeed: (() -> Unit)? = null,
    onOpenSpeedMenu: (() -> Unit)? = null,
    onSeekRelative: ((Long) -> Unit)? = null,
    onToggleFullscreen: (() -> Unit)? = null,
    isFullscreen: Boolean = false,
    isDarkOverlay: Boolean = true
) {
    val effectiveMs = if (isScrubbing && durationMs > 0) {
        (scrubFraction * durationMs).toLong()
    } else {
        currentPositionMs
    }

    val fraction = if (isScrubbing) {
        scrubFraction.coerceIn(0f, 1f)
    } else if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val forceHours = durationMs >= 3600000L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_player_horizontal_seek_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!activeSubtitleText.isNullOrBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isScrubbing) Color(0xFF00E5FF).copy(alpha = 0.8f) else Color(0xFF00E5FF).copy(alpha = 0.35f)),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .testTag("on_screen_time_subtitle_hud")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (subtitleStartTimeMs != null && subtitleEndTimeMs != null) {
                                "${formatVideoTimestamp(subtitleStartTimeMs)} - ${formatVideoTimestamp(subtitleEndTimeMs)}"
                            } else {
                                formatVideoTimestamp(effectiveMs, forceHours)
                            },
                            color = Color(0xFF00E5FF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentSubtitleIndex != null) {
                            Text(
                                text = "#$currentSubtitleIndex",
                                color = MxYellowSubtitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                            modifier = Modifier.clickable { onOpenSpeedMenu?.invoke() ?: onToggleSpeed?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "${playbackSpeed}x Speed",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeSubtitleText,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatVideoTimestamp(effectiveMs, forceHours),
                color = if (isScrubbing) Color(0xFF00E5FF) else if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("video_seek_current_time")
            )

            Slider(
                value = fraction,
                onValueChange = { frac ->
                    onScrubStart()
                    onScrubProgress(frac)
                },
                onValueChangeFinished = {
                    val targetMs = (fraction * durationMs).toLong()
                    onScrubFinished(targetMs)
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("video_player_timeline_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF),
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = if (isDarkOverlay) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant
                )
            )

            val durationText = if (showRemainingTime) {
                val remainingMs = (durationMs - effectiveMs).coerceAtLeast(0L)
                "-${formatVideoTimestamp(remainingMs, forceHours)}"
            } else {
                formatVideoTimestamp(durationMs, forceHours)
            }

            Text(
                text = durationText,
                color = if (showRemainingTime) Color(0xFF00E5FF) else if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onToggleRemainingTime()
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .testTag("video_seek_duration_toggle")
            )

            if (onToggleSubtitles != null || onOpenSubtitleSettings != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = { onToggleSubtitles?.invoke() },
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("seek_bar_subtitles_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Subtitles on/off",
                        tint = if (showSubtitles) MxYellowSubtitle else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (onToggleSpeed != null || onOpenSpeedMenu != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    onClick = { onToggleSpeed?.invoke() ?: onOpenSpeedMenu?.invoke() },
                    shape = RoundedCornerShape(6.dp),
                    color = if (playbackSpeed != 1.0f) Color(0xFF00E5FF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (playbackSpeed != 1.0f) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.testTag("seek_bar_speed_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Playback Speed",
                            tint = if (playbackSpeed != 1.0f) Color(0xFF00E5FF) else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${playbackSpeed}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (playbackSpeed != 1.0f) Color(0xFF00E5FF) else Color.White
                        )
                    }
                }
            }

            if (onToggleFullscreen != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("seek_bar_fullscreen_btn")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                        tint = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayerMiniProgressBar(
    currentPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    onBarClick: (() -> Unit)? = null
) {
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(Color.White.copy(alpha = 0.2f))
            .then(
                if (onBarClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBarClick() }
                } else Modifier
            )
            .testTag("video_player_mini_progress_bar")
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(Color(0xFF00E5FF))
        )
    }
}
