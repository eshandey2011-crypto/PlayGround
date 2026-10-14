package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class VoiceCategoryFilter(val label: String, val icon: String) {
    NARRATOR("Narrator Voice", "🎙️")
}

private enum class SelectedVoiceType {
    PERSONA,
    SYSTEM_VOICE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HindiVoiceProfileBottomSheet(
    selectedPersona: HindiVoicePersona?,
    availablePersonas: List<HindiVoicePersona> = VoiceManager.defaultPersonas,
    availableVoices: List<TtsVoiceItem> = emptyList(),
    selectedVoice: TtsVoiceItem? = null,
    previewingPersonaId: String? = null,
    speedProgress: Int = 50,
    pitchProgress: Int = 50,
    onSpeedChanged: (Int) -> Unit = {},
    onPitchChanged: (Int) -> Unit = {},
    onSelectPersona: (HindiVoicePersona?) -> Unit,
    onSelectSystemVoice: (TtsVoiceItem) -> Unit = {},
    onPreviewVoice: (HindiVoicePersona?) -> Unit,
    onPreviewSystemVoice: (TtsVoiceItem) -> Unit = {},
    onStopPreview: () -> Unit,
    onTestVoice: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var selectedType by remember {
        mutableStateOf(SelectedVoiceType.PERSONA)
    }
    var tempSelectedPersona by remember { mutableStateOf(VoiceManager.defaultPersonas.first()) }
    var tempSelectedVoice by remember { mutableStateOf<TtsVoiceItem?>(null) }
    var showTuningControls by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = {
            onStopPreview()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        },
        modifier = Modifier.testTag("hindi_voice_selection_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Narrator Voice Only",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudOff,
                                    contentDescription = "Offline Voice Synthesis",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Offline Narrator",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Deep authoritative narration for Hindi subtitles (other voices removed)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = {
                        onStopPreview()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("dismiss_voice_selection_dialog_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Sheet",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val activeName = if (selectedType == SelectedVoiceType.PERSONA) {
                            tempSelectedPersona.displayName
                        } else {
                            tempSelectedVoice?.displayName ?: "System Hindi Voice"
                        }
                        Text(
                            text = "Selected Voice: $activeName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        val activeDetails = if (selectedType == SelectedVoiceType.PERSONA) {
                            "${tempSelectedPersona.gender.displayName} • Pitch: ${String.format(Locale.US, "%.2fx", tempSelectedPersona.basePitch)} • Speed: ${String.format(Locale.US, "%.2fx", tempSelectedPersona.baseSpeed)}"
                        } else {
                            "Hardware Synthetic Voice • ${tempSelectedVoice?.locale ?: "hi-IN"}"
                        }
                        Text(
                            text = activeDetails,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Surface(
                        onClick = { showTuningControls = !showTuningControls },
                        shape = RoundedCornerShape(8.dp),
                        color = if (showTuningControls) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, if (showTuningControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                        modifier = Modifier.testTag("toggle_voice_tuning_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Fine-Tune Voice",
                                tint = if (showTuningControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (showTuningControls) "Hide Tuning" else "Fine-Tune",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (showTuningControls) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showTuningControls,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Voice Modulation (Real-Time)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FilledTonalButton(
                                onClick = onTestVoice,
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag("test_custom_voice_btn")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Test Tuning", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Audiotrack, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pitch", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            val pitchVal = 0.5f + (pitchProgress / 50f) * 0.5f
                            Text("${String.format(Locale.US, "%.2f", pitchVal)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Slider(
                            value = pitchProgress.toFloat(),
                            onValueChange = { onPitchChanged(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Speed / Tempo", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            val speedVal = 0.5f + (speedProgress / 50f) * 0.5f
                            Text("${String.format(Locale.US, "%.2f", speedVal)}x", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                        }
                        Slider(
                            value = speedProgress.toFloat(),
                            onValueChange = { onSpeedChanged(it.toInt()) },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0D9488).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color(0xFF0D9488).copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Narrator voice exclusive • All other voices removed",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0D9488)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val narratorPersona = VoiceManager.defaultPersonas.first()
            val isAudioPlaying = previewingPersonaId == narratorPersona.id
            val metricsText = "Pitch: ${String.format(Locale.US, "%.2fx", narratorPersona.basePitch)} • Speed: ${String.format(Locale.US, "%.2fx", narratorPersona.baseSpeed)} • Offline Synthetic Baritone"

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                item(key = "persona_${narratorPersona.id}") {
                    VoiceProfileOptionCard(
                        title = narratorPersona.displayName,
                        subtitle = metricsText,
                        tag = "NARRATOR ONLY",
                        tagColor = Color(0xFF0D9488),
                        description = narratorPersona.description,
                        sampleQuote = narratorPersona.sampleText,
                        icon = Icons.Default.RecordVoiceOver,
                        iconBgColor = Color(0xFF0D9488),
                        isSelected = true,
                        isPlayingPreview = isAudioPlaying,
                        testTag = "voice_profile_${narratorPersona.id}",
                        onClick = {
                            selectedType = SelectedVoiceType.PERSONA
                            tempSelectedPersona = narratorPersona
                        },
                        onPreviewClick = {
                            if (isAudioPlaying) onStopPreview() else onPreviewVoice(narratorPersona)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onStopPreview()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        onStopPreview()
                        if (selectedType == SelectedVoiceType.PERSONA) {
                            onSelectPersona(tempSelectedPersona)
                        } else {
                            tempSelectedVoice?.let { onSelectSystemVoice(it) }
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1.5f)
                        .height(48.dp)
                        .testTag("apply_voice_selection_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apply Voice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun HindiVoiceProfileDialog(
    selectedPersona: HindiVoicePersona?,
    availablePersonas: List<HindiVoicePersona> = VoiceManager.defaultPersonas,
    availableVoices: List<TtsVoiceItem> = emptyList(),
    selectedVoice: TtsVoiceItem? = null,
    previewingPersonaId: String? = null,
    speedProgress: Int = 50,
    pitchProgress: Int = 50,
    onSpeedChanged: (Int) -> Unit = {},
    onPitchChanged: (Int) -> Unit = {},
    onSelectPersona: (HindiVoicePersona?) -> Unit,
    onSelectSystemVoice: (TtsVoiceItem) -> Unit = {},
    onPreviewVoice: (HindiVoicePersona?) -> Unit,
    onPreviewSystemVoice: (TtsVoiceItem) -> Unit = {},
    onStopPreview: () -> Unit,
    onTestVoice: () -> Unit = {},
    onDismiss: () -> Unit
) {
    HindiVoiceProfileBottomSheet(
        selectedPersona = selectedPersona,
        availablePersonas = availablePersonas,
        availableVoices = availableVoices,
        selectedVoice = selectedVoice,
        previewingPersonaId = previewingPersonaId,
        speedProgress = speedProgress,
        pitchProgress = pitchProgress,
        onSpeedChanged = onSpeedChanged,
        onPitchChanged = onPitchChanged,
        onSelectPersona = onSelectPersona,
        onSelectSystemVoice = onSelectSystemVoice,
        onPreviewVoice = onPreviewVoice,
        onPreviewSystemVoice = onPreviewSystemVoice,
        onStopPreview = onStopPreview,
        onTestVoice = onTestVoice,
        onDismiss = onDismiss
    )
}

@Composable
private fun VoiceProfileOptionCard(
    title: String,
    subtitle: String,
    tag: String,
    tagColor: Color,
    description: String,
    sampleQuote: String,
    icon: ImageVector,
    iconBgColor: Color,
    isSelected: Boolean,
    isPlayingPreview: Boolean,
    testTag: String,
    onClick: () -> Unit,
    onPreviewClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = iconBgColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconBgColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = tagColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = tagColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.95f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\"$sampleQuote\"",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onPreviewClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isPlayingPreview) Color(0xFFEF4444) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (isPlayingPreview) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("preview_${testTag}")
                    ) {
                        Icon(
                            imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingPreview) "Stop Voice Preview" else "Listen to Voice Preview",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlayingPreview) "Stop" else "Preview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
