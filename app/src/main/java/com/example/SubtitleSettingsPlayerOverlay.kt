package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SubtitleSettingsPlayerOverlay(
    visible: Boolean,
    fontSize: Int,
    theme: SubtitleTheme,
    backgroundOpacity: Float,
    alignment: SubtitleAlignment,
    showSubtitlesOnVideo: Boolean,
    sampleSubtitleText: String = "नमस्ते, यह सबटाइटल प्रिव्यू है।",
    isSameToSameTimeDelivery: Boolean = true,
    isOfflineReaderPlaying: Boolean = false,
    isOfflineHindiVoiceInSubtitles: Boolean = true,
    isSingleDeepVoiceOnly: Boolean = false,
    onFontSizeChange: (Int) -> Unit,
    onThemeChange: (SubtitleTheme) -> Unit,
    onBackgroundOpacityChange: (Float) -> Unit,
    onAlignmentChange: (SubtitleAlignment) -> Unit,
    onToggleShowSubtitles: () -> Unit,
    onToggleSameToSameTimeDelivery: (Boolean) -> Unit = {},
    onToggleOfflineReader: () -> Unit = {},
    onToggleOfflineHindiVoice: (Boolean) -> Unit = {},
    onToggleSingleDeepVoice: (Boolean) -> Unit = {},
    onResetDefaults: () -> Unit,
    onOpenFullSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally { it / 2 },
        exit = fadeOut() + slideOutHorizontally { it / 2 },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 280.dp, max = 340.dp)
                .wrapContentHeight()
                .padding(12.dp)
                .testTag("subtitle_settings_player_overlay"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xEB121620),
            border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Subtitles,
                                    contentDescription = "Subtitle Settings",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Subtitle Readability",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Font • Color • Opacity",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onResetDefaults,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("overlay_reset_appearance_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Appearance Defaults",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("overlay_close_subtitle_settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Subtitle Settings",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = backgroundOpacity),
                    border = BorderStroke(
                        1.dp,
                        Color(theme.textColorHex).copy(alpha = (backgroundOpacity * 0.7f).coerceAtLeast(0.2f))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = when (alignment) {
                            SubtitleAlignment.LEFT -> Alignment.CenterStart
                            SubtitleAlignment.CENTER -> Alignment.Center
                            SubtitleAlignment.RIGHT -> Alignment.CenterEnd
                        }
                    ) {
                        Text(
                            text = if (showSubtitlesOnVideo) sampleSubtitleText else "(Subtitles Hidden)",
                            color = if (showSubtitlesOnVideo) Color(theme.textColorHex) else Color.Gray,
                            fontSize = (fontSize - 2).coerceAtLeast(11).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = when (alignment) {
                                SubtitleAlignment.LEFT -> TextAlign.Start
                                SubtitleAlignment.CENTER -> TextAlign.Center
                                SubtitleAlignment.RIGHT -> TextAlign.End
                            },
                            style = TextStyle(
                                shadow = if (backgroundOpacity < 0.3f) Shadow(
                                    color = Color.Black,
                                    offset = Offset(1.5f, 1.5f),
                                    blurRadius = 3f
                                ) else null
                            ),
                            maxLines = 1
                        )
                    }
                }

                HorizontalDivider(color = Color(0x3338BDF8))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Font Size",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${fontSize}sp",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            14 to "Small",
                            18 to "Med",
                            22 to "Large",
                            26 to "X-Large"
                        ).forEach { (size, label) ->
                            val isSelected = fontSize == size
                            Surface(
                                onClick = { onFontSizeChange(size) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0x33FFFFFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("overlay_font_size_preset_$size")
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onFontSizeChange((fontSize - 1).coerceAtLeast(12)) },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("overlay_decrease_font_size_btn")
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Smaller", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = { onFontSizeChange(it.toInt()) },
                            valueRange = 12f..36f,
                            steps = 23,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("overlay_font_size_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00E5FF),
                                activeTrackColor = Color(0xFF00E5FF),
                                inactiveTrackColor = Color(0x44FFFFFF)
                            )
                        )
                        IconButton(
                            onClick = { onFontSizeChange((fontSize + 1).coerceAtMost(36)) },
                            modifier = Modifier
                                .size(24.dp)
                                .testTag("overlay_increase_font_size_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Larger", tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Text Color",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = theme.label,
                            color = Color(theme.textColorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val themes = SubtitleTheme.values()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            themes.take(4).forEach { thm ->
                                SubtitleColorChip(
                                    theme = thm,
                                    isSelected = theme == thm,
                                    onClick = { onThemeChange(thm) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            themes.drop(4).take(4).forEach { thm ->
                                SubtitleColorChip(
                                    theme = thm,
                                    isSelected = theme == thm,
                                    onClick = { onThemeChange(thm) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val opacityPercent = (backgroundOpacity * 100).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = null,
                                tint = Color(0xFFA855F7),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Background Opacity",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFA855F7).copy(alpha = 0.25f)
                        ) {
                            Text(
                                text = "$opacityPercent%",
                                color = Color(0xFFC084FC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0.0f to "0%",
                            0.40f to "40%",
                            0.75f to "75%",
                            1.0f to "100%"
                        ).forEach { (presetVal, label) ->
                            val isSelected = kotlin.math.abs(backgroundOpacity - presetVal) < 0.06f
                            Surface(
                                onClick = { onBackgroundOpacityChange(presetVal) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFFA855F7) else Color(0x22FFFFFF),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFFA855F7) else Color(0x33FFFFFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("overlay_opacity_preset_${(presetVal * 100).toInt()}")
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Slider(
                        value = backgroundOpacity,
                        onValueChange = { onBackgroundOpacityChange(it) },
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overlay_opacity_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFA855F7),
                            activeTrackColor = Color(0xFFA855F7),
                            inactiveTrackColor = Color(0x44FFFFFF)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Align:",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                        SubtitleAlignment.values().forEach { align ->
                            val isSelected = alignment == align
                            Surface(
                                onClick = { onAlignmentChange(align) },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0x22FFFFFF),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (align) {
                                            SubtitleAlignment.LEFT -> Icons.AutoMirrored.Filled.FormatAlignLeft
                                            SubtitleAlignment.CENTER -> Icons.Default.FormatAlignCenter
                                            SubtitleAlignment.RIGHT -> Icons.AutoMirrored.Filled.FormatAlignRight
                                        },
                                        contentDescription = align.label,
                                        tint = if (isSelected) Color.Black else Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = onToggleShowSubtitles,
                        shape = RoundedCornerShape(6.dp),
                        color = if (showSubtitlesOnVideo) Color(0x3310B981) else Color(0x33EF4444),
                        border = BorderStroke(
                            1.dp,
                            if (showSubtitlesOnVideo) Color(0xFF10B981) else Color(0xFFEF4444)
                        ),
                        modifier = Modifier.testTag("overlay_toggle_subtitles_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (showSubtitlesOnVideo) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = if (showSubtitlesOnVideo) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = if (showSubtitlesOnVideo) "Shown" else "Hidden",
                                color = if (showSubtitlesOnVideo) Color(0xFF10B981) else Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSameToSameTimeDelivery) Color(0x3310B981) else Color(0x1AFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        if (isSameToSameTimeDelivery) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (isSameToSameTimeDelivery) Color(0xFF34D399) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Same Time Audio Delivery",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isSameToSameTimeDelivery) "1:1 Subtitle Audio Sync (0ms)" else "Standard pre-roll timing",
                                    color = if (isSameToSameTimeDelivery) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Switch(
                            checked = isSameToSameTimeDelivery,
                            onCheckedChange = onToggleSameToSameTimeDelivery,
                            modifier = Modifier.testTag("overlay_same_to_same_switch")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOfflineReaderPlaying) Color(0x3310B981) else Color(0x1AFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        if (isOfflineReaderPlaying) Color(0xFF10B981).copy(alpha = 0.8f) else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Offline Subtitle Voice Reader",
                                tint = if (isOfflineReaderPlaying) Color(0xFF10B981) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Offline Subtitles Reader Voice",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isOfflineReaderPlaying) "Reading Subtitles Live (Offline TTS)" else "Voice Reader Inactive",
                                    color = if (isOfflineReaderPlaying) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Switch(
                            checked = isOfflineReaderPlaying,
                            onCheckedChange = { onToggleOfflineReader() },
                            modifier = Modifier.testTag("overlay_offline_reader_switch")
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSingleDeepVoiceOnly) Color(0x3300E5FF) else Color(0x1AFFFFFF),
                    border = BorderStroke(
                        1.dp,
                        if (isSingleDeepVoiceOnly) Color(0xFF00E5FF).copy(alpha = 0.8f) else Color(0x33FFFFFF)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Text(text = "🎙️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "Offline Deep Voice (Single)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isSingleDeepVoiceOnly) "Single Deep Voice (0.72x Baritone)" else "Standard Multi-Speaker",
                                    color = if (isSingleDeepVoiceOnly) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                    fontSize = 9.sp
                                )
                            }
                        }
                        Switch(
                            checked = isSingleDeepVoiceOnly,
                            onCheckedChange = onToggleSingleDeepVoice,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF00E5FF).copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.testTag("overlay_single_deep_voice_switch")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onOpenFullSettings,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "More Options...",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleColorChip(
    theme: SubtitleTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = Color(theme.textColorHex)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = Color(0x22FFFFFF),
        border = BorderStroke(
            if (isSelected) 2.dp else 1.dp,
            if (isSelected) Color(0xFF00E5FF) else Color(0x33FFFFFF)
        ),
        modifier = modifier.testTag("overlay_color_chip_${theme.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(chipColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = theme.label.split(" ").lastOrNull() ?: theme.label,
                fontSize = 9.sp,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}
