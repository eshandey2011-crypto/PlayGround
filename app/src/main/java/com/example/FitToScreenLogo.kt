package com.example

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FitToScreenLogo(
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    activeColor: Color = Color(0xFF38BDF8),
    isFitActive: Boolean = true,
    showEmblem: Boolean = true
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (isFitActive) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "fitLogoAnim"
    )
    val currentTint by animateColorAsState(
        targetValue = if (isFitActive) activeColor else tint,
        animationSpec = tween(durationMillis = 280),
        label = "fitLogoColor"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = (w * 0.08f).coerceIn(1.5f, 5f)
        val cornerLen = w * 0.22f

        val screenPaddingX = w * 0.06f
        val screenPaddingY = h * 0.12f
        val screenWidth = w - (screenPaddingX * 2f)
        val screenHeight = h - (screenPaddingY * 2f)

        drawRoundRect(
            color = currentTint.copy(alpha = 0.35f + (animatedProgress * 0.35f)),
            topLeft = Offset(screenPaddingX, screenPaddingY),
            size = Size(screenWidth, screenHeight),
            cornerRadius = CornerRadius(w * 0.10f, w * 0.10f),
            style = Stroke(width = strokeW * 0.75f)
        )

        val bracketColor = currentTint
        val bracketStroke = strokeW * 1.1f

        val tlPath = Path().apply {
            moveTo(screenPaddingX, screenPaddingY + cornerLen)
            lineTo(screenPaddingX, screenPaddingY)
            lineTo(screenPaddingX + cornerLen, screenPaddingY)
        }
        drawPath(tlPath, bracketColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val trPath = Path().apply {
            moveTo(screenPaddingX + screenWidth - cornerLen, screenPaddingY)
            lineTo(screenPaddingX + screenWidth, screenPaddingY)
            lineTo(screenPaddingX + screenWidth, screenPaddingY + cornerLen)
        }
        drawPath(trPath, bracketColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val blPath = Path().apply {
            moveTo(screenPaddingX, screenPaddingY + screenHeight - cornerLen)
            lineTo(screenPaddingX, screenPaddingY + screenHeight)
            lineTo(screenPaddingX + cornerLen, screenPaddingY + screenHeight)
        }
        drawPath(blPath, bracketColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val brPath = Path().apply {
            moveTo(screenPaddingX + screenWidth - cornerLen, screenPaddingY + screenHeight)
            lineTo(screenPaddingX + screenWidth, screenPaddingY + screenHeight)
            lineTo(screenPaddingX + screenWidth, screenPaddingY + screenHeight - cornerLen)
        }
        drawPath(brPath, bracketColor, style = Stroke(width = bracketStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (showEmblem) {
            val cx = w * 0.5f
            val cy = h * 0.5f
            val innerW = screenWidth * (0.42f + (animatedProgress * 0.16f))
            val innerH = screenHeight * (0.42f + (animatedProgress * 0.16f))

            drawRoundRect(
                color = currentTint.copy(alpha = 0.5f + (animatedProgress * 0.5f)),
                topLeft = Offset(cx - (innerW * 0.5f), cy - (innerH * 0.5f)),
                size = Size(innerW, innerH),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                style = if (isFitActive) Fill else Stroke(width = strokeW * 0.6f)
            )

            if (w >= 36f) {
                val arrowDist = innerW * 0.32f
                val arrowSize = strokeW * 1.4f
                drawFitArrow(cx - arrowDist, cy - arrowDist, -1f, -1f, arrowSize, bracketColor)
                drawFitArrow(cx + arrowDist, cy - arrowDist, 1f, -1f, arrowSize, bracketColor)
                drawFitArrow(cx - arrowDist, cy + arrowDist, -1f, 1f, arrowSize, bracketColor)
                drawFitArrow(cx + arrowDist, cy + arrowDist, 1f, 1f, arrowSize, bracketColor)
            }
        }
    }
}

private fun DrawScope.drawFitArrow(x: Float, y: Float, dirX: Float, dirY: Float, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y - (dirY * size))
        lineTo(x, y)
        lineTo(x - (dirX * size), y)
    }
    drawPath(path, color, style = Stroke(width = size * 0.65f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
fun FitToScreenButton(
    uiState: TtsUiState,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val isFit = uiState.aspectRatioMode == AspectRatioMode.FIT && uiState.videoZoomScale == 1.0f
    val isZoomed = uiState.videoZoomScale > 1.05f

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFit) Color(0xFF0284C7).copy(alpha = 0.25f)
                else if (isZoomed) Color(0xFFF59E0B).copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                width = 1.dp,
                color = if (isFit) Color(0xFF38BDF8).copy(alpha = 0.7f)
                else if (isZoomed) Color(0xFFFBBF24).copy(alpha = 0.7f)
                else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .testTag("fit_to_screen_logo_btn"),
        contentAlignment = Alignment.Center
    ) {
        FitToScreenLogo(
            modifier = Modifier.size(size * 0.62f),
            tint = Color.White,
            activeColor = if (isZoomed) Color(0xFFFBBF24) else Color(0xFF38BDF8),
            isFitActive = isFit
        )
    }
}

@Composable
fun ZoomFloatingHudPill(
    zoomScale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onFitToScreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = (zoomScale * 100).toInt()
    Surface(
        color = Color(0xDD0F172A),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
        modifier = modifier.padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ZoomIn,
                contentDescription = "Zoom",
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${percent}%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                color = Color(0xFF0284C7),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable(onClick = onFitToScreen)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    FitToScreenLogo(
                        modifier = Modifier.size(14.dp),
                        tint = Color.White,
                        activeColor = Color.White,
                        isFitActive = true,
                        showEmblem = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Fit to Screen",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FitToScreenZoomDialog(
    uiState: TtsUiState,
    onDismiss: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onResetFitToScreen: () -> Unit,
    onModeSelect: (AspectRatioMode) -> Unit
) {
    val currentPercent = (uiState.videoZoomScale * 100).toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FitToScreenLogo(
                    modifier = Modifier.size(28.dp),
                    tint = Color(0xFF38BDF8),
                    activeColor = Color(0xFF38BDF8),
                    isFitActive = true
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Zoom & Fit to Screen", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Pinch to zoom (100% - 500%) or fit to screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Current Zoom",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${currentPercent}%",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (uiState.videoZoomScale > 1.05f) Color(0xFF0284C7) else MaterialTheme.colorScheme.primary
                            )
                        }
                        FilledTonalButton(
                            onClick = { onResetFitToScreen() },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF0284C7),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            FitToScreenLogo(
                                modifier = Modifier.size(16.dp),
                                tint = Color.White,
                                activeColor = Color.White,
                                isFitActive = true,
                                showEmblem = false
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fit (100%)", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { onZoomChange((uiState.videoZoomScale - 0.25f).coerceAtLeast(1.0f)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                    Slider(
                        value = uiState.videoZoomScale,
                        onValueChange = { onZoomChange(it) },
                        valueRange = 1.0f..5.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF0284C7),
                            activeTrackColor = Color(0xFF38BDF8)
                        )
                    )
                    IconButton(
                        onClick = { onZoomChange((uiState.videoZoomScale + 0.25f).coerceAtMost(5.0f)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Quick Zoom Presets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        1.0f to "100% Fit",
                        1.25f to "125%",
                        1.50f to "150%",
                        2.0f to "200%",
                        3.0f to "300%"
                    )
                    presets.forEach { (scale, label) ->
                        val isSelected = kotlin.math.abs(uiState.videoZoomScale - scale) < 0.05f
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (scale == 1.0f) onResetFitToScreen() else onZoomChange(scale)
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Screen Fit Modes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                val modes = listOf(
                    AspectRatioMode.FIT to "Fit to Screen (16:9)",
                    AspectRatioMode.FILL to "Crop / Fill Screen",
                    AspectRatioMode.STRETCH to "Stretch Full Screen",
                    AspectRatioMode.ORIGINAL to "100% Original Ratio"
                )
                modes.forEach { (mode, title) ->
                    val isSelected = uiState.aspectRatioMode == mode && uiState.videoZoomScale == 1.0f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.15f) else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onModeSelect(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0284C7) else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Text("ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0284C7))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}
