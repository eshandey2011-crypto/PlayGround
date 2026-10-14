package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun PlayerSubtitleDelayQuickPill(
    currentOffsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = true
) {
    val accentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
    val containerBg = if (isDarkOverlay) Color(0xFF0F172A).copy(alpha = 0.88f) else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (currentOffsetMs != 0L) {
        if (isDarkOverlay) Color(0xFF00E5FF).copy(alpha = 0.65f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
    } else {
        if (isDarkOverlay) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.testTag("player_delay_quick_stepper")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
        ) {
            Surface(
                onClick = { onAdjustOffset(-100L) },
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkOverlay) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(width = 46.dp, height = 26.dp)
                    .testTag("delay_stepper_minus_100")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "-100ms",
                        color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onToggleExpanded() }
                    .padding(horizontal = 4.dp, vertical = 3.dp)
                    .testTag("delay_stepper_display")
            ) {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = "Subtitle Delay",
                    tint = if (currentOffsetMs != 0L) accentColor else if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (currentOffsetMs != 0L) {
                        "${if (currentOffsetMs > 0) "+" else ""}${currentOffsetMs}ms"
                    } else {
                        "0ms"
                    },
                    color = if (currentOffsetMs != 0L) accentColor else if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "•",
                    color = Color(0xFF10B981),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(3.dp))

            Surface(
                onClick = { onAdjustOffset(100L) },
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkOverlay) Color.White.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .size(width = 46.dp, height = 26.dp)
                    .testTag("delay_stepper_plus_100")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+100ms",
                        color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (currentOffsetMs != 0L) {
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(
                    onClick = onResetOffset,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delay_stepper_reset")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Subtitle Delay to 0ms",
                        tint = if (isDarkOverlay) Color(0xFFFFB74D) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerSubtitleDelayOverlayControl(
    uiState: TtsUiState,
    onOffsetChange: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onClose: () -> Unit,
    onOpenFullDialog: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = true,
    onOpenPerfectTimingDialog: () -> Unit = {},
    onCalibratePerfectTiming: () -> Unit = {}
) {
    val currentOffset = uiState.subtitleOffsetMs
    val containerColor = if (isDarkOverlay) {
        Color(0xFF0F172A).copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
    val accentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkOverlay) {
        Color(0xFF00E5FF).copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isDarkOverlay) 8.dp else 3.dp,
        modifier = modifier.testTag("player_subtitle_delay_overlay_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SRT Subtitle Delay",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.20f),
                        border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Real-Time • Offline",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF34D399),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            currentOffset == 0L -> if (isDarkOverlay) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            currentOffset > 0L -> Color(0xFF0086FF).copy(alpha = 0.25f)
                            else -> Color(0xFFFF9800).copy(alpha = 0.25f)
                        }
                    ) {
                        Text(
                            text = "${if (currentOffset > 0) "+" else ""}${currentOffset} ms",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = when {
                                currentOffset == 0L -> if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
                                currentOffset > 0L -> if (isDarkOverlay) Color(0xFF00E5FF) else Color(0xFF0086FF)
                                else -> if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                            },
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("player_sync_offset_text_badge")
                        )
                    }

                    if (currentOffset != 0L) {
                        Surface(
                            onClick = onResetOffset,
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("player_sync_reset_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Offset",
                                    tint = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "0ms",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenFullDialog,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("player_sync_open_full_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Open Full Sync Screen",
                            tint = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("player_sync_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sync Slider",
                            tint = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDarkOverlay) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF00E5FF).copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onAdjustOffset(-100L) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("player_overlay_step_minus_100")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("-100ms", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = when {
                                currentOffset == 0L -> "Exact Sync (0ms)"
                                currentOffset > 0L -> "Subtitles ${currentOffset}ms earlier"
                                else -> "Subtitles ${abs(currentOffset)}ms later"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                currentOffset == 0L -> if (isDarkOverlay) Color(0xFF10B981) else Color(0xFF2E7D32)
                                currentOffset > 0L -> if (isDarkOverlay) Color(0xFF00E5FF) else Color(0xFF0086FF)
                                else -> if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                            }
                        )
                        Text(
                            text = "Real-time • 100ms steps",
                            fontSize = 9.sp,
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline
                        )
                    }

                    Button(
                        onClick = { onAdjustOffset(100L) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("player_overlay_step_plus_100")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+100ms", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Slider(
                value = currentOffset.toFloat().coerceIn(-5000f, 5000f),
                onValueChange = { newOffsetMs ->
                    onOffsetChange(newOffsetMs.toLong())
                },
                valueRange = -5000f..5000f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .testTag("player_subtitle_sync_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = if (isDarkOverlay) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "-5s",
                    fontSize = 10.sp,
                    color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "100% Offline Timing",
                    fontSize = 9.sp,
                    color = if (isDarkOverlay) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                )
                Text(
                    text = "+5s",
                    fontSize = 10.sp,
                    color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = { onAdjustOffset(-500L) },
                    modifier = Modifier.weight(1f).testTag("player_sync_step_minus_500"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("-500", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustOffset(-100L) },
                    modifier = Modifier.weight(1f).testTag("player_sync_step_minus_100"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("-100", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustOffset(-20L) },
                    modifier = Modifier.weight(1f).testTag("player_sync_step_minus_20"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("-20", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustOffset(20L) },
                    modifier = Modifier.weight(1f).testTag("player_sync_step_plus_20"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("+20", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustOffset(100L) },
                    modifier = Modifier.weight(1f).testTag("player_sync_step_plus_100"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("+100", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustOffset(500L) },
                    modifier = Modifier.weight(1f).testTag("player_sync_step_plus_500"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("+500", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            val activeSub = uiState.activeSubtitle
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkOverlay) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Dialogue: ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = if (activeSub != null) {
                            "\"${activeSub.text}\""
                        } else {
                            "(No dialogue at current video position)"
                        },
                        fontSize = 10.sp,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun PlayerSubtitleSyncSliderCard(
    uiState: TtsUiState,
    onOffsetChange: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onClose: () -> Unit,
    onOpenFullDialog: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = false,
    onOpenPerfectTimingDialog: () -> Unit = {},
    onCalibratePerfectTiming: () -> Unit = {},
    onToggleSameToSameTime: (Boolean) -> Unit = {},
    onLockSameToSameSync: () -> Unit = {}
) {
    SubtitleSyncSettingsPanel(
        uiState = uiState,
        onOffsetChange = onOffsetChange,
        onAdjustOffset = onAdjustOffset,
        onResetOffset = onResetOffset,
        onAlignToCurrentPosition = onCalibratePerfectTiming,
        onToggleSameToSameTime = onToggleSameToSameTime,
        onLockSameToSameSync = onLockSameToSameSync,
        onClose = onClose,
        modifier = modifier,
        isDarkOverlay = isDarkOverlay,
        onOpenFullDialog = onOpenFullDialog
    )
}
