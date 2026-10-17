package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlayerSpeedControlMenu(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onAdjustSpeed: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isDarkOverlay: Boolean = false
) {
    val containerColor = if (isDarkOverlay) {
        Color(0xFF14171F).copy(alpha = 0.94f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
    val accentColor = if (isDarkOverlay) Color(0xFF00E5FF) else MaterialTheme.colorScheme.primary
    val borderColor = if (isDarkOverlay) {
        Color(0xFF00E5FF).copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    }

    val formattedSpeed = if (currentSpeed == currentSpeed.toInt().toFloat()) {
        "${currentSpeed.toInt()}.0x"
    } else {
        String.format(Locale.US, "%.2fx", currentSpeed).trimEnd('0').let {
            if (it.endsWith(".")) "${it}0x" else "${it}x"
        }
    }
    val speedLabel = when {
        currentSpeed < 0.95f -> "Slow Motion"
        currentSpeed in 0.95f..1.05f -> "Normal Speed"
        currentSpeed <= 1.45f -> "Fast"
        else -> "Very Fast (2x Max)"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (isDarkOverlay) 6.dp else 2.dp,
        modifier = modifier.testTag("player_speed_control_menu")
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
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Playback Speed Icon",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Playback Speed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            currentSpeed in 0.98f..1.02f -> if (isDarkOverlay) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant
                            currentSpeed > 1.02f -> Color(0xFF0086FF).copy(alpha = 0.22f)
                            else -> Color(0xFFFF9800).copy(alpha = 0.22f)
                        }
                    ) {
                        Text(
                            text = "$formattedSpeed ($speedLabel)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = when {
                                currentSpeed in 0.98f..1.02f -> if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSurface
                                currentSpeed > 1.02f -> if (isDarkOverlay) Color(0xFF00E5FF) else Color(0xFF0086FF)
                                else -> if (isDarkOverlay) Color(0xFFFFB74D) else Color(0xFFE65100)
                            },
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("player_speed_badge")
                        )
                    }

                    if (currentSpeed !in 0.98f..1.02f) {
                        Surface(
                            onClick = { onSpeedSelected(1.0f) },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDarkOverlay) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("player_speed_reset_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RestartAlt,
                                    contentDescription = "Reset Speed to 1.0x",
                                    tint = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "1.0x",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDarkOverlay) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(26.dp)
                            .testTag("player_speed_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Speed Menu",
                            tint = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Slider(
                value = currentSpeed.coerceIn(0.5f, 2.0f),
                onValueChange = { rawValue ->
                    val snapped = (rawValue * 20f).roundToInt() / 20f
                    onSpeedSelected(snapped.coerceIn(0.5f, 2.0f))
                },
                valueRange = 0.5f..2.0f,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_speed_slider"),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SlowMotionVideo,
                        contentDescription = null,
                        tint = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "0.5x (Min)",
                        fontSize = 10.sp,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "1.0x (Normal)",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (currentSpeed in 0.98f..1.02f) accentColor else if (isDarkOverlay) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "2.0x (Max)",
                        fontSize = 10.sp,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = if (isDarkOverlay) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            val presetSpeeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                presetSpeeds.forEach { speed ->
                    val isSelected = (currentSpeed - speed).let { it in -0.02f..0.02f }
                    val label = when (speed) {
                        0.5f -> "0.5x"
                        0.75f -> "0.75x"
                        1.0f -> "1.0x"
                        1.25f -> "1.25x"
                        1.5f -> "1.5x"
                        1.75f -> "1.75x"
                        2.0f -> "2.0x"
                        else -> "${speed}x"
                    }
                    if (isSelected) {
                        Button(
                            onClick = { onSpeedSelected(speed) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_speed_chip_${label.replace(".", "_")}"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = if (isDarkOverlay) Color.Black else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSpeedSelected(speed) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("player_speed_chip_${label.replace(".", "_")}"),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 2.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isDarkOverlay) Color.White.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDarkOverlay) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onAdjustSpeed(-0.25f) },
                    enabled = currentSpeed > 0.5f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_speed_step_minus_25"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("-0.25x", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustSpeed(-0.1f) },
                    enabled = currentSpeed > 0.5f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_speed_step_minus_10"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("-0.1x", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustSpeed(0.1f) },
                    enabled = currentSpeed < 2.0f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_speed_step_plus_10"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("+0.1x", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onAdjustSpeed(0.25f) },
                    enabled = currentSpeed < 2.0f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("player_speed_step_plus_25"),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                ) {
                    Text("+0.25x", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isDarkOverlay) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Pitch corrected • Subtitles remain synchronized",
                        fontSize = 10.sp,
                        color = if (isDarkOverlay) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Range: 0.5x - 2.0x",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
        }
    }
}
