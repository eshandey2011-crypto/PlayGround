package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleSyncSettingsPanel(
    uiState: TtsUiState,
    viewModel: TtsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = true,
    onOpenFullDialog: () -> Unit = {}
) {
    SubtitleSyncSettingsPanel(
        uiState = uiState,
        onOffsetChange = { viewModel.setSubtitleOffset(it) },
        onAdjustOffset = { viewModel.adjustSubtitleOffset(it) },
        onResetOffset = { viewModel.setSubtitleOffset(0L) },
        onAlignToCurrentPosition = { viewModel.alignSubtitleToCurrentPosition() },
        onToggleSameToSameTime = { viewModel.toggleSameToSameTimeDelivery(it) },
        onLockSameToSameSync = { viewModel.lockSameToSameSync() },
        onSeekTo = { viewModel.seekTo(it) },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onClose = onClose,
        modifier = modifier,
        isDarkOverlay = isDarkOverlay,
        onOpenFullDialog = onOpenFullDialog
    )
}

@Composable
fun SubtitleSyncSettingsPanel(
    uiState: TtsUiState,
    onOffsetChange: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = true,
    onOpenFullDialog: () -> Unit = {},
    onAlignToCurrentPosition: () -> Unit = {},
    onToggleSameToSameTime: (Boolean) -> Unit = {},
    onLockSameToSameSync: () -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    onTogglePlayPause: () -> Unit = {}
) {
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    var isWideRange by remember { mutableStateOf(false) }
    var showGuidanceTip by remember { mutableStateOf(false) }

    val currentOffset = uiState.subtitleOffsetMs
    val effectivePosition = (uiState.videoPositionMs + currentOffset).coerceAtLeast(0L)

    val activeSub = uiState.activeSubtitle ?: uiState.parsedSubtitles.firstOrNull {
        effectivePosition in it.startTimeMs..it.endTimeMs
    }
    val nextSub = if (activeSub == null) {
        uiState.parsedSubtitles.firstOrNull { it.startTimeMs > effectivePosition }
    } else {
        uiState.parsedSubtitles.firstOrNull { it.startTimeMs > activeSub.endTimeMs }
    }

    val containerBg = if (isDarkOverlay) {
        Color(0xFF0D1527).copy(alpha = 0.96f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
    val accentCyan = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
    val borderStrokeColor = if (isDarkOverlay) {
        if (currentOffset != 0L) Color(0xFF00E5FF).copy(alpha = 0.55f) else Color(0xFF334155).copy(alpha = 0.8f)
    } else {
        if (currentOffset != 0L) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
    }
    val maxRange = if (isWideRange) 10000f else 5000f

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerBg,
        border = BorderStroke(1.2.dp, borderStrokeColor),
        shadowElevation = if (isDarkOverlay) 12.dp else 4.dp,
        modifier = modifier
            .testTag("subtitle_sync_settings_panel")
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Subtitle Synchronization",
                                tint = accentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Subtitle Sync Settings",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.18f),
                                border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "LIVE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Millisecond offset tuning during video playback",
                            fontSize = 10.sp,
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            manualInputText = currentOffset.toString()
                            showManualInputDialog = true
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("sync_manual_input_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Enter Milliseconds Manually",
                            tint = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { showGuidanceTip = !showGuidanceTip },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Sync Guidance",
                            tint = if (showGuidanceTip) accentCyan else if (isDarkOverlay) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenFullDialog,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Open Full Screen Dialog",
                            tint = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("sync_panel_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sync Panel",
                            tint = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDarkOverlay) Color(0xFF131F37) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (currentOffset > 0) "+$currentOffset ms" else "$currentOffset ms",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    currentOffset == 0L -> if (isDarkOverlay) Color(0xFF34D399) else Color(0xFF2E7D32)
                                    currentOffset > 0L -> if (isDarkOverlay) Color(0xFF00E5FF) else Color(0xFF0288D1)
                                    else -> if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                                },
                                modifier = Modifier.testTag("sync_digital_ms_readout")
                            )
                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    currentOffset == 0L -> Color(0xFF10B981).copy(alpha = 0.20f)
                                    currentOffset > 0L -> Color(0xFF00E5FF).copy(alpha = 0.20f)
                                    else -> Color(0xFFFF9800).copy(alpha = 0.20f)
                                }
                            ) {
                                Text(
                                    text = when {
                                        currentOffset == 0L -> "Exact Sync (0ms)"
                                        currentOffset > 0L -> "Advance (+${currentOffset}ms)"
                                        else -> "Delay (${currentOffset}ms)"
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        currentOffset == 0L -> if (isDarkOverlay) Color(0xFF34D399) else Color(0xFF2E7D32)
                                        currentOffset > 0L -> if (isDarkOverlay) Color(0xFF00E5FF) else Color(0xFF0288D1)
                                        else -> if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (currentOffset != 0L) {
                            Surface(
                                onClick = { onResetOffset() },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.secondaryContainer,
                                border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.testTag("sync_offset_reset_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RestartAlt,
                                        contentDescription = "Reset to 0ms",
                                        tint = if (isDarkOverlay) Color(0xFFFFB74D) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Reset 0ms",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Video: ${SrtItem.formatMs(uiState.videoPositionMs)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Subtitle Clock: ${SrtItem.formatMs(effectivePosition)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = accentCyan
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (uiState.isSameToSameTimeDelivery) {
                    if (isDarkOverlay) Color(0xFF064E3B).copy(alpha = 0.45f) else Color(0xFFD1FAE5).copy(alpha = 0.7f)
                } else {
                    if (isDarkOverlay) Color(0xFF1E293B).copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
                border = BorderStroke(
                    1.dp,
                    if (uiState.isSameToSameTimeDelivery) {
                        Color(0xFF10B981).copy(alpha = 0.6f)
                    } else {
                        if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("same_to_same_time_delivery_card")
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (uiState.isSameToSameTimeDelivery) Color(0xFF34D399) else accentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Same-to-Same Time Delivery",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                                Text(
                                    text = "Audio speaks exactly when subtitle appears (1:1 sync)",
                                    fontSize = 10.sp,
                                    color = if (isDarkOverlay) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                onClick = { onLockSameToSameSync() },
                                shape = RoundedCornerShape(6.dp),
                                color = if (uiState.isSameToSameTimeDelivery && uiState.subtitleOffsetMs == 0L) {
                                    Color(0xFF10B981).copy(alpha = 0.3f)
                                } else {
                                    if (isDarkOverlay) Color.White.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer
                                }
                            ) {
                                Text(
                                    text = "Lock 0ms",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.isSameToSameTimeDelivery && uiState.subtitleOffsetMs == 0L) Color(0xFF34D399) else contentColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = uiState.isSameToSameTimeDelivery,
                                onCheckedChange = { onToggleSameToSameTime(it) },
                                modifier = Modifier.testTag("same_to_same_time_switch")
                            )
                        }
                    }
                    if (uiState.isSameToSameTimeDelivery) {
                        Text(
                            text = "1:1 Synchronized: Audio speech triggers at the exact same millisecond as visual subtitles and auto-scales speech duration so dialogue never overruns.",
                            fontSize = 9.5.sp,
                            color = if (isDarkOverlay) Color(0xFF34D399).copy(alpha = 0.95f) else Color(0xFF047857),
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showGuidanceTip,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF00E5FF).copy(alpha = 0.3f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = accentCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "How to Fix Subtitle Desync:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                        Text(
                            text = "Spoken audio heard BEFORE subtitle appears? Subtitle is late. Tap [+] to advance subtitles earlier.",
                            fontSize = 10.5.sp,
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Subtitle text appears BEFORE voice speaks? Subtitle is early. Tap [-] to delay subtitles later.",
                            fontSize = 10.5.sp,
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Offset Slider (Continuous)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        onClick = { isWideRange = !isWideRange },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isWideRange) accentCyan.copy(alpha = 0.2f) else if (isDarkOverlay) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = if (isWideRange) "Range: ±10s" else "Range: ±5s",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isWideRange) accentCyan else if (isDarkOverlay) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Slider(
                    value = currentOffset.toFloat().coerceIn(-maxRange, maxRange),
                    onValueChange = { newOffsetMs ->
                        onOffsetChange(newOffsetMs.toLong())
                    },
                    valueRange = -maxRange..maxRange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .testTag("sync_offset_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentCyan,
                        activeTrackColor = accentCyan,
                        inactiveTrackColor = if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isWideRange) "-10,000ms" else "-5,000ms",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "0 ms (Default)",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = if (isWideRange) "+10,000ms" else "+5,000ms",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAdjustOffset(-25L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_minus_25"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("-25ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedButton(
                        onClick = { onAdjustOffset(-50L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_minus_50"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("-50ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedButton(
                        onClick = { onAdjustOffset(50L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_plus_50"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("+50ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                    OutlinedButton(
                        onClick = { onAdjustOffset(25L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_plus_25"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("+25ms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { onAdjustOffset(-1000L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_minus_1000"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("-1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onAdjustOffset(-500L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_minus_500"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("-500ms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onAdjustOffset(-100L) },
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("sync_step_minus_100"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("-100ms", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { onAdjustOffset(100L) },
                        modifier = Modifier
                            .weight(1.1f)
                            .testTag("sync_step_plus_100"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("+100ms", fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Button(
                        onClick = { onAdjustOffset(500L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_plus_500"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("+500ms", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { onAdjustOffset(1000L) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_step_plus_1000"),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                    ) {
                        Text("+1s", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Presets:",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                )
                listOf(
                    0L to "0ms",
                    -200L to "-200ms",
                    200L to "+200ms",
                    -500L to "-500ms",
                    500L to "+500ms",
                    -1000L to "-1.0s",
                    1000L to "+1.0s",
                    -2000L to "-2.0s",
                    2000L to "+2.0s"
                ).forEach { (offsetVal, label) ->
                    val isSelected = currentOffset == offsetVal
                    Surface(
                        onClick = { onOffsetChange(offsetVal) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) {
                            accentCyan.copy(alpha = 0.25f)
                        } else if (isDarkOverlay) {
                            Color(0xFF1E293B)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = BorderStroke(
                            0.8.dp,
                            if (isSelected) accentCyan else if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) accentCyan else if (isDarkOverlay) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = { onAlignToCurrentPosition() },
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF00E5FF).copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sync_align_current_speech_btn")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = accentCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Snap to Current Audio Speech",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Text(
                                text = "Aligns nearest subtitle cue start to current playback cursor",
                                fontSize = 9.5.sp,
                                color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentCyan.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "Auto Align",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDarkOverlay) Color(0xFF0A101D) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = BorderStroke(0.8.dp, if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = if (activeSub != null) Color(0xFFFFD54F) else if (isDarkOverlay) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (activeSub != null) "Active Subtitle Cue (${SrtItem.formatMs(activeSub.startTimeMs)} - ${SrtItem.formatMs(activeSub.endTimeMs)})"
                                else "Upcoming Cue",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeSub != null) (if (isDarkOverlay) Color(0xFFFFD54F) else Color(0xFFF57F17)) else if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                            )
                        }
                        if (activeSub != null) {
                            Text(
                                text = "NOW PLAYING",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                    Text(
                        text = when {
                            activeSub != null -> "\"${activeSub.text}\""
                            nextSub != null -> "Next in ${((nextSub.startTimeMs - effectivePosition) / 1000f).coerceAtLeast(0f).let { String.format(java.util.Locale.US, "%.1fs", it) }}: \"${nextSub.text}\""
                            else -> "(No dialogue loaded or near current timeline position)"
                        },
                        fontSize = 11.sp,
                        fontWeight = if (activeSub != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (activeSub != null) contentColor else if (isDarkOverlay) Color.White.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outline,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val newPos = (uiState.videoPositionMs - 5000L).coerceAtLeast(0L)
                        onSeekTo(newPos)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("sync_panel_replay_5s_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay5,
                        contentDescription = "Replay 5 seconds",
                        modifier = Modifier.size(14.dp),
                        tint = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Re-listen -5s",
                        fontSize = 10.5.sp,
                        color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = { onTogglePlayPause() },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isVideoPlaying) (if (isDarkOverlay) Color(0xFF1E293B) else MaterialTheme.colorScheme.secondaryContainer)
                        else accentCyan,
                        contentColor = if (uiState.isVideoPlaying) (if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer)
                        else Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("sync_panel_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (uiState.isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isVideoPlaying) "Pause" else "Play",
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isVideoPlaying) "Pause" else "Play",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        val newPos = (uiState.videoPositionMs + 5000L).coerceAtMost(uiState.videoDurationMs)
                        onSeekTo(newPos)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, if (isDarkOverlay) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.testTag("sync_panel_forward_5s_btn")
                ) {
                    Text(
                        text = "+5s Forward",
                        fontSize = 10.5.sp,
                        color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 5 seconds",
                        modifier = Modifier.size(14.dp),
                        tint = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (showManualInputDialog) {
        val keyboardController = LocalSoftwareKeyboardController.current
        var inputError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showManualInputDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exact Timing Offset (ms)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter a precise offset in milliseconds. Use positive numbers to advance subtitles earlier, or negative numbers to delay them.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = {
                            manualInputText = it
                            inputError = null
                        },
                        label = { Text("Offset (ms)") },
                        placeholder = { Text("e.g. 250 or -400") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                            }
                        ),
                        isError = inputError != null,
                        supportingText = {
                            inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sync_manual_input_text_field")
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0L to "0ms", 250L to "+250ms", -250L to "-250ms", 500L to "+500ms").forEach { (v, lbl) ->
                            Surface(
                                onClick = { manualInputText = v.toString() },
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = lbl,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = manualInputText.trim().toLongOrNull()
                        if (parsed != null) {
                            onOffsetChange(parsed)
                            showManualInputDialog = false
                        } else {
                            inputError = "Please enter a valid integer number (e.g. 200 or -200)"
                        }
                    },
                    modifier = Modifier.testTag("sync_manual_input_apply_btn")
                ) {
                    Text("Apply Offset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualInputDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
