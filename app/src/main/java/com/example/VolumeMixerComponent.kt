package com.example

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt

@Composable
fun VolumeMixerDialog(
    uiState: TtsUiState,
    viewModel: TtsViewModel,
    onDismissRequest: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("volume_mixer_dialog"),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            VolumeMixerContent(
                uiState = uiState,
                viewModel = viewModel,
                onClose = onDismissRequest
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeMixerBottomSheet(
    uiState: TtsUiState,
    viewModel: TtsViewModel,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(48.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        VolumeMixerContent(
            uiState = uiState,
            viewModel = viewModel,
            onClose = onDismissRequest,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
fun VolumeMixerContent(
    uiState: TtsUiState,
    viewModel: TtsViewModel,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Mixer",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Audio Track Mixer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0086FF).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0086FF).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "STUDIO PRO",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0086FF),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Independent control of original audio and voice narration levels",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CrossfadeBalanceSection(
                uiState = uiState,
                viewModel = viewModel
            )

            MixerPresetsSection(
                uiState = uiState,
                viewModel = viewModel
            )

            Text(
                text = "Independent Channel Strips",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 540.dp
                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            OriginalAudioChannelStrip(uiState = uiState, viewModel = viewModel)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            DubbingAudioChannelStrip(uiState = uiState, viewModel = viewModel)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OriginalAudioChannelStrip(uiState = uiState, viewModel = viewModel)
                        DubbingAudioChannelStrip(uiState = uiState, viewModel = viewModel)
                    }
                }
            }

            MasterChannelStrip(uiState = uiState, viewModel = viewModel)
            MiniAudioMonitorStrip(uiState = uiState, viewModel = viewModel)
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    viewModel.applyMixPreset(
                        videoVol = 0.15f,
                        dubVol = 1.0f,
                        bgmVol = 0.0f,
                        videoMuted = false,
                        dubMuted = false,
                        bgmEnabled = false,
                        presetName = "Default Broadcast Dub"
                    )
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Default Reset", fontSize = 12.sp)
            }
            Button(
                onClick = { onClose?.invoke() ?: viewModel.showVolumeMixerDialog(false) },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CrossfadeBalanceSection(
    uiState: TtsUiState,
    viewModel: TtsViewModel
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Crossfader Balance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val balanceText = when {
                    uiState.crossfadeBalance < 0.2f -> "Original Dominant (100%)"
                    uiState.crossfadeBalance in 0.4f..0.6f -> "Balanced 50:50 (Dual Balance)"
                    uiState.crossfadeBalance > 0.8f -> "Voice Dominant (100%)"
                    else -> "Custom Mix"
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = balanceText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (!uiState.isVideoMuted) Color(0xFF00C853) else Color(0xFFFF5252))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Original (${(uiState.videoVolume * 100).toInt()}%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!uiState.isVideoMuted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = "50/50",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Voice (${(uiState.dubbingVolume * 100).toInt()}%)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!uiState.isDubbingMuted) Color(0xFF0086FF) else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (!uiState.isDubbingMuted) Color(0xFF0086FF) else Color(0xFFFF5252))
                        )
                    }
                }
                Slider(
                    value = uiState.crossfadeBalance,
                    onValueChange = { viewModel.setCrossfadeBalance(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mixer_crossfader_slider")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        onClick = { viewModel.setCrossfadeBalance(0.5f) },
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Center Snap (50/50)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MixerPresetsSection(
    uiState: TtsUiState,
    viewModel: TtsViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Quick Dual-Track Presets",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isBroadcastSelected = !uiState.isVideoMuted && uiState.videoVolume in 0.1f..0.3f && !uiState.isDubbingMuted && uiState.dubbingVolume >= 0.9f
            MixPresetChip(
                icon = Icons.Default.Headphones,
                title = "Broadcast Dub",
                subtitle = "Dub 100% | Video 20%",
                isSelected = isBroadcastSelected,
                isRecommended = true,
                onClick = {
                    viewModel.applyMixPreset(
                        videoVol = 0.20f,
                        dubVol = 1.0f,
                        bgmVol = 0.0f,
                        videoMuted = false,
                        dubMuted = false,
                        bgmEnabled = false,
                        presetName = "Broadcast Dub"
                    )
                }
            )
            val isPureDubSelected = uiState.isVideoMuted && !uiState.isDubbingMuted && uiState.dubbingVolume >= 0.9f
            MixPresetChip(
                icon = Icons.Default.RecordVoiceOver,
                title = "Pure Voice",
                subtitle = "Voice 100% | Video Mute",
                isSelected = isPureDubSelected,
                isRecommended = false,
                onClick = {
                    viewModel.applyMixPreset(
                        videoVol = 0.0f,
                        dubVol = 1.0f,
                        bgmVol = 0.0f,
                        videoMuted = true,
                        dubMuted = false,
                        bgmEnabled = false,
                        presetName = "Pure Voice"
                    )
                }
            )
            val isDualSelected = !uiState.isVideoMuted && uiState.videoVolume in 0.4f..0.6f && !uiState.isDubbingMuted && uiState.dubbingVolume in 0.7f..0.9f
            MixPresetChip(
                icon = Icons.Default.Tune,
                title = "Balanced 50:50",
                subtitle = "Dub 80% | Video 50%",
                isSelected = isDualSelected,
                isRecommended = false,
                onClick = {
                    viewModel.applyMixPreset(
                        videoVol = 0.50f,
                        dubVol = 0.80f,
                        bgmVol = 0.0f,
                        videoMuted = false,
                        dubMuted = false,
                        bgmEnabled = false,
                        presetName = "Balanced 50:50"
                    )
                }
            )
            val isOrigLeadSelected = !uiState.isVideoMuted && uiState.videoVolume >= 0.9f && !uiState.isDubbingMuted && uiState.dubbingVolume in 0.1f..0.3f
            MixPresetChip(
                icon = Icons.Default.Movie,
                title = "Original Lead",
                subtitle = "Video 100% | Dub 20%",
                isSelected = isOrigLeadSelected,
                isRecommended = false,
                onClick = {
                    viewModel.applyMixPreset(
                        videoVol = 1.0f,
                        dubVol = 0.20f,
                        bgmVol = 0.0f,
                        videoMuted = false,
                        dubMuted = false,
                        bgmEnabled = false,
                        presetName = "Original Lead"
                    )
                }
            )
            val isOrigOnlySelected = !uiState.isVideoMuted && uiState.videoVolume >= 0.9f && (uiState.isDubbingMuted || !uiState.isAutoDubbingEnabled)
            MixPresetChip(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Original Only",
                subtitle = "Video 100% | Dub Mute",
                isSelected = isOrigOnlySelected,
                isRecommended = false,
                onClick = {
                    viewModel.applyMixPreset(
                        videoVol = 1.0f,
                        dubVol = 0.0f,
                        bgmVol = 0.0f,
                        videoMuted = false,
                        dubMuted = true,
                        bgmEnabled = false,
                        presetName = "Original Only"
                    )
                }
            )
        }
    }
}

@Composable
fun MixPresetChip(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isRecommended: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.widthIn(min = 135.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                if (isRecommended) {
                    Surface(
                        color = Color(0xFF0086FF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Recommended",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else
                    MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun OriginalAudioChannelStrip(
    uiState: TtsUiState,
    viewModel: TtsViewModel
) {
    val isMuted = uiState.isVideoMuted || uiState.isDubbingSolo || uiState.isMasterMuted
    val isSolo = uiState.isOriginalSolo

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSolo)
                Color(0xFF00C853).copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSolo) Color(0xFF00C853).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("channel_strip_original_audio")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00C853).copy(alpha = 0.15f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = Color(0xFF00C853),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Original Video Audio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Original Background Track",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isSolo -> Color(0xFF00C853).copy(alpha = 0.2f)
                        isMuted -> Color(0xFFFF5252).copy(alpha = 0.15f)
                        else -> Color(0xFF00C853).copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when {
                            isSolo -> "SOLO"
                            isMuted -> "MUTED"
                            else -> "LIVE"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSolo -> Color(0xFF00C853)
                            isMuted -> Color(0xFFFF5252)
                            else -> Color(0xFF00C853)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VisualVuMeter(
                    level = if (uiState.isVideoPlaying && !isMuted) uiState.videoVolume else 0f,
                    isActive = uiState.isVideoPlaying && !isMuted,
                    modifier = Modifier
                        .width(22.dp)
                        .height(84.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Volume: ${(uiState.videoVolume * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatDecibels(uiState.videoVolume, isMuted),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMuted) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = uiState.videoVolume,
                        onValueChange = { viewModel.setVideoVolume(it) },
                        valueRange = 0f..1f,
                        enabled = !isMuted || !uiState.isVideoMuted,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00C853),
                            activeTrackColor = Color(0xFF00C853),
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("original_audio_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        QuickLevelButton(label = "0%", onClick = { viewModel.setVideoVolume(0f) }, isSelected = uiState.videoVolume == 0f)
                        QuickLevelButton(label = "20%", onClick = { viewModel.setVideoVolume(0.2f) }, isSelected = (uiState.videoVolume * 100).roundToInt() == 20)
                        QuickLevelButton(label = "50%", onClick = { viewModel.setVideoVolume(0.5f) }, isSelected = (uiState.videoVolume * 100).roundToInt() == 50)
                        QuickLevelButton(label = "100%", onClick = { viewModel.setVideoVolume(1.0f) }, isSelected = uiState.videoVolume >= 0.99f)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MuteButton(
                    isMuted = uiState.isVideoMuted,
                    onClick = { viewModel.toggleVideoMute() },
                    modifier = Modifier.weight(1f)
                )
                SoloButton(
                    isSolo = uiState.isOriginalSolo,
                    onClick = { viewModel.toggleOriginalSolo() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DubbingAudioChannelStrip(
    uiState: TtsUiState,
    viewModel: TtsViewModel
) {
    val isMuted = uiState.isDubbingMuted || uiState.isOriginalSolo || uiState.isMasterMuted || !uiState.isAutoDubbingEnabled
    val isSolo = uiState.isDubbingSolo
    val isSpeaking = uiState.speechState == SpeechState.SPEAKING
    val activeSpeakerName = uiState.activeSubtitle?.speaker?.name ?: uiState.selectedVoice?.displayName ?: "Standard Hindi"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSolo)
                Color(0xFF0086FF).copy(alpha = 0.08f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSolo) Color(0xFF0086FF).copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("channel_strip_dubbing_audio")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0086FF).copy(alpha = 0.15f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = Color(0xFF0086FF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Voice Track",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "TTS Voice: $activeSpeakerName",
                            fontSize = 10.sp,
                            color = Color(0xFF0086FF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isSolo -> Color(0xFF0086FF).copy(alpha = 0.2f)
                        isMuted -> Color(0xFFFF5252).copy(alpha = 0.15f)
                        isSpeaking -> Color(0xFF00C853).copy(alpha = 0.2f)
                        else -> Color(0xFF0086FF).copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = when {
                            isSolo -> "SOLO"
                            isMuted -> "MUTED"
                            isSpeaking -> "ACTIVE"
                            else -> "READY"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSolo -> Color(0xFF0086FF)
                            isMuted -> Color(0xFFFF5252)
                            isSpeaking -> Color(0xFF00C853)
                            else -> Color(0xFF0086FF)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VisualVuMeter(
                    level = if (!isMuted && (isSpeaking || uiState.isVideoPlaying)) uiState.dubbingVolume else 0f,
                    isActive = !isMuted && (isSpeaking || uiState.isVideoPlaying),
                    isPulsing = isSpeaking,
                    modifier = Modifier
                        .width(22.dp)
                        .height(84.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Dubbing Level: ${(uiState.dubbingVolume * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatDecibels(uiState.dubbingVolume, isMuted),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMuted) Color(0xFFFF5252) else Color(0xFF0086FF)
                        )
                    }
                    Slider(
                        value = uiState.dubbingVolume,
                        onValueChange = { viewModel.setDubbingVolume(it) },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0086FF),
                            activeTrackColor = Color(0xFF0086FF),
                            inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dubbing_audio_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuickLevelButton(label = "0%", onClick = { viewModel.setDubbingVolume(0f) }, isSelected = uiState.dubbingVolume == 0f)
                        QuickLevelButton(label = "50%", onClick = { viewModel.setDubbingVolume(0.5f) }, isSelected = (uiState.dubbingVolume * 100).roundToInt() == 50)
                        QuickLevelButton(label = "80%", onClick = { viewModel.setDubbingVolume(0.8f) }, isSelected = (uiState.dubbingVolume * 100).roundToInt() == 80)
                        QuickLevelButton(label = "100%", onClick = { viewModel.setDubbingVolume(1.0f) }, isSelected = uiState.dubbingVolume >= 0.99f)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            onClick = { viewModel.testDubbingVoice() },
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0086FF).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0086FF).copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0086FF), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Test Voice", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0086FF))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MuteButton(
                    isMuted = uiState.isDubbingMuted,
                    onClick = { viewModel.toggleDubbingMute() },
                    modifier = Modifier.weight(1f)
                )
                SoloButton(
                    isSolo = uiState.isDubbingSolo,
                    onClick = { viewModel.toggleDubbingSolo() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MasterChannelStrip(
    uiState: TtsUiState,
    viewModel: TtsViewModel
) {
    val isMasterMuted = uiState.isMasterMuted

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Master Output",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Main Stereo Mix Bus",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Surface(
                    onClick = { viewModel.toggleMasterMute() },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isMasterMuted) Color(0xFFFF5252) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isMasterMuted) Color(0xFFFF5252) else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isMasterMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = if (isMasterMuted) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMasterMuted) "MUTED" else "MASTER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMasterMuted) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    VisualVuMeter(
                        level = if (uiState.isVideoPlaying && !isMasterMuted) uiState.masterVolume * 0.95f else 0f,
                        isActive = uiState.isVideoPlaying && !isMasterMuted,
                        modifier = Modifier.width(10.dp).height(54.dp)
                    )
                    VisualVuMeter(
                        level = if (uiState.isVideoPlaying && !isMasterMuted) uiState.masterVolume else 0f,
                        isActive = uiState.isVideoPlaying && !isMasterMuted,
                        modifier = Modifier.width(10.dp).height(54.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Master Volume: ${(uiState.masterVolume * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatDecibels(uiState.masterVolume, isMasterMuted),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMasterMuted) Color(0xFFFF5252) else MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = uiState.masterVolume,
                        onValueChange = { viewModel.setMasterVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun MiniAudioMonitorStrip(
    uiState: TtsUiState,
    viewModel: TtsViewModel
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        imageVector = if (uiState.isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    val activeSub = uiState.activeSubtitle
                    Text(
                        text = if (activeSub != null)
                            "Dialogue #${activeSub.index}: \"${activeSub.cleanSpokenText}\""
                        else
                            "Player Time: ${SrtItem.formatMs(uiState.videoPositionMs)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (uiState.isVideoPlaying) "Live audio mixing in progress..." else "Playback paused",
                        fontSize = 9.sp,
                        color = if (uiState.isVideoPlaying) Color(0xFF00C853) else MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun VisualVuMeter(
    level: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    isPulsing: Boolean = false
) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (isActive) level.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "vu_meter_level"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Canvas(modifier = modifier.clip(RoundedCornerShape(4.dp))) {
        val totalHeight = size.height
        val segmentCount = 14
        val gap = 2.dp.toPx()
        val segmentHeight = (totalHeight - (segmentCount - 1) * gap) / segmentCount
        val width = size.width
        val activeSegments = (animatedLevel * segmentCount).roundToInt()

        for (i in 0 until segmentCount) {
            val segmentIndexFromBottom = segmentCount - 1 - i
            val isLit = segmentIndexFromBottom < activeSegments
            val segmentColor = when {
                i < 2 -> if (isLit) Color(0xFFFF1744) else Color(0x33FF1744)
                i < 6 -> if (isLit) Color(0xFFFFC107) else Color(0x33FFC107)
                else -> if (isLit) Color(0xFF00E676) else Color(0x3300E676)
            }
            val yOffset = i * (segmentHeight + gap)
            drawRoundRect(
                color = if (isLit && isPulsing) segmentColor.copy(alpha = pulseAlpha) else segmentColor,
                topLeft = Offset(0f, yOffset),
                size = Size(width, segmentHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun QuickLevelButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MuteButton(
    isMuted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isMuted) Color(0xFFFF5252) else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMuted) Color(0xFFFF5252) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = if (isMuted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isMuted) "MUTED" else "MUTE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMuted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SoloButton(
    isSolo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSolo) Color(0xFFFFB300) else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSolo) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = null,
                tint = if (isSolo) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (isSolo) "SOLO (ACTIVE)" else "SOLO (S)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSolo) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDecibels(volume: Float, isMuted: Boolean): String {
    if (isMuted || volume <= 0.001f) return "-∞ dB"
    if (volume >= 0.99f) return "0.0 dB"
    val db = 20.0 * kotlin.math.log10(volume.toDouble())
    return String.format(java.util.Locale.US, "%.1f dB", db)
}
