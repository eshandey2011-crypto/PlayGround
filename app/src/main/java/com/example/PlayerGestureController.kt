package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

enum class GestureHudType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
    DOUBLE_TAP_LEFT,
    DOUBLE_TAP_RIGHT,
    ZOOM
}

data class GestureConfig(
    val doubleTapSeekMs: Long = 10000L,
    val doubleTapLeftFraction: Float = 0.35f,
    val doubleTapRightFraction: Float = 0.65f,
    val swipeSlopPx: Float = 14f,
    val brightnessSensitivity: Float = 1.15f,
    val volumeSensitivity: Float = 1.15f,
    val maxHorizontalSeekSpanMs: Long = 90000L,
    /** Full-width swipe covers this fraction of the video (capped by max span). */
    val swipeSpanFractionOfDuration: Float = 0.25f,
    /** Swipes smaller than this are treated as tap wobble, not seeks. */
    val minSeekCommitMs: Long = 1000L,
    val hudDismissDelayMs: Long = 900L
)

/**
 * Maps a horizontal swipe fraction (-1..1 across screen width) to a seek
 * delta. Proportional to video length so a small drag on a short clip no
 * longer jumps "pura back", while long movies still scrub up to maxSpan.
 */
fun swipeSeekDeltaMs(
    fraction: Float,
    durationMs: Long,
    maxSpanMs: Long = 90000L,
    spanFraction: Float = 0.25f
): Long {
    if (durationMs <= 0) return 0L
    val span = minOf(maxSpanMs, (durationMs * spanFraction).toLong().coerceAtLeast(5000L))
    return (fraction * span).toLong()
}

interface PlayerGestureListener {
    fun onSingleTap()
    fun onDoubleTapSeek(seekDeltaMs: Long, isForward: Boolean)
    fun onDoubleTapPlayPause()
    fun onBrightnessAdjust(delta: Float)
    fun onVolumeAdjust(delta: Float)
    fun onSeekGestureStart(currentPositionMs: Long)
    fun onSeekGestureProgress(seekDeltaMs: Long, targetPositionMs: Long)
    fun onSeekGestureCommit(targetPositionMs: Long)
    fun onGestureHudChanged(hudType: GestureHudType)
    fun onLockedScreenTap()
}

class PlayerGestureController(
    private val config: GestureConfig = GestureConfig(),
    private val scope: CoroutineScope
) {
    var listener: PlayerGestureListener? = null
    private var dragStartOffset = Offset.Zero
    private var seekStartPositionMs = 0L
    private var currentDragMode = GestureHudType.NONE
    private var hudDismissJob: Job? = null

    fun handleTap(isScreenLocked: Boolean) {
        if (isScreenLocked) {
            listener?.onLockedScreenTap()
        } else {
            listener?.onSingleTap()
        }
    }

    fun handleDoubleTap(offset: Offset, viewSize: Size) {
        val width = viewSize.width
        if (width <= 0) return
        val normalizedX = offset.x / width
        when {
            normalizedX < config.doubleTapLeftFraction -> {
                val delta = -config.doubleTapSeekMs
                listener?.onDoubleTapSeek(delta, isForward = false)
                triggerHud(GestureHudType.DOUBLE_TAP_LEFT)
            }
            normalizedX > config.doubleTapRightFraction -> {
                val delta = config.doubleTapSeekMs
                listener?.onDoubleTapSeek(delta, isForward = true)
                triggerHud(GestureHudType.DOUBLE_TAP_RIGHT)
            }
            else -> {
                listener?.onDoubleTapPlayPause()
            }
        }
    }

    fun handleDragStart(offset: Offset, currentPositionMs: Long) {
        dragStartOffset = offset
        seekStartPositionMs = currentPositionMs
        currentDragMode = GestureHudType.NONE
        hudDismissJob?.cancel()
    }

    fun handleDrag(
        dragAmount: Offset,
        currentOffset: Offset,
        viewSize: Size,
        durationMs: Long
    ) {
        val totalDeltaX = currentOffset.x - dragStartOffset.x
        val totalDeltaY = currentOffset.y - dragStartOffset.y

        if (currentDragMode == GestureHudType.NONE) {
            val absX = abs(totalDeltaX)
            val absY = abs(totalDeltaY)
            if (absX > config.swipeSlopPx || absY > config.swipeSlopPx) {
                if (absX > absY * 1.15f) {
                    currentDragMode = GestureHudType.SEEK
                    listener?.onSeekGestureStart(seekStartPositionMs)
                    listener?.onGestureHudChanged(GestureHudType.SEEK)
                } else {
                    val isLeftHalf = dragStartOffset.x < (viewSize.width * 0.5f)
                    currentDragMode = if (isLeftHalf) GestureHudType.BRIGHTNESS else GestureHudType.VOLUME
                    listener?.onGestureHudChanged(currentDragMode)
                }
            }
        }

        when (currentDragMode) {
            GestureHudType.BRIGHTNESS -> {
                val height = if (viewSize.height > 0) viewSize.height else 600f
                val delta = (-dragAmount.y / (height * 0.65f)) * config.brightnessSensitivity
                listener?.onBrightnessAdjust(delta)
            }
            GestureHudType.VOLUME -> {
                val height = if (viewSize.height > 0) viewSize.height else 600f
                val delta = (-dragAmount.y / (height * 0.65f)) * config.volumeSensitivity
                listener?.onVolumeAdjust(delta)
            }
            GestureHudType.SEEK -> {
                if (durationMs > 0 && viewSize.width > 0) {
                    val fraction = totalDeltaX / viewSize.width
                    val seekDelta = swipeSeekDeltaMs(
                        fraction,
                        durationMs,
                        config.maxHorizontalSeekSpanMs,
                        config.swipeSpanFractionOfDuration
                    )
                    val target = (seekStartPositionMs + seekDelta).coerceIn(0L, durationMs)
                    listener?.onSeekGestureProgress(seekDelta, target)
                }
            }
            else -> {}
        }
    }

    fun handleDragEnd(targetPositionMs: Long) {
        if (currentDragMode == GestureHudType.SEEK) {
            listener?.onSeekGestureCommit(targetPositionMs)
        }
        scheduleHudDismiss()
        currentDragMode = GestureHudType.NONE
    }

    fun handleDragCancel() {
        scheduleHudDismiss(immediate = true)
        currentDragMode = GestureHudType.NONE
    }

    fun triggerHud(type: GestureHudType) {
        listener?.onGestureHudChanged(type)
        scheduleHudDismiss()
    }

    fun scheduleHudDismiss(immediate: Boolean = false) {
        hudDismissJob?.cancel()
        if (immediate) {
            listener?.onGestureHudChanged(GestureHudType.NONE)
            return
        }
        hudDismissJob = scope.launch {
            delay(config.hudDismissDelayMs)
            listener?.onGestureHudChanged(GestureHudType.NONE)
        }
    }
}

@Composable
fun rememberPlayerGestureController(
    config: GestureConfig = GestureConfig(),
    listener: PlayerGestureListener
): PlayerGestureController {
    val scope = rememberCoroutineScope()
    val controller = remember(config) {
        PlayerGestureController(config, scope)
    }
    controller.listener = listener
    return controller
}

fun Modifier.mxPlayerGestures(
    controller: PlayerGestureController,
    isScreenLocked: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    targetPositionMs: Long,
    onZoomGesture: (scaleMultiplier: Float, panDeltaX: Float, panDeltaY: Float) -> Unit = { _, _, _ -> },
    onPinchEnd: () -> Unit = {}
): Modifier = this
    .pointerInput(isScreenLocked) {
        if (!isScreenLocked) {
            awaitPointerEventScope {
                var isPinching = false
                var previousDistance = 0f
                var previousCentroid = Offset.Zero
                while (true) {
                    val event = awaitPointerEvent()
                    val downPointers = event.changes.filter { it.pressed }
                    if (downPointers.size >= 2) {
                        val p1 = downPointers[0].position
                        val p2 = downPointers[1].position
                        val distance = hypot(p1.x - p2.x, p1.y - p2.y)
                        val centroid = Offset((p1.x + p2.x) * 0.5f, (p1.y + p2.y) * 0.5f)
                        if (!isPinching) {
                            isPinching = true
                            previousDistance = distance
                            previousCentroid = centroid
                        } else {
                            if (previousDistance > 10f) {
                                val zoom = distance / previousDistance
                                val panX = centroid.x - previousCentroid.x
                                val panY = centroid.y - previousCentroid.y
                                downPointers.forEach { it.consume() }
                                onZoomGesture(zoom, panX, panY)
                            }
                            previousDistance = distance
                            previousCentroid = centroid
                        }
                    } else if (isPinching) {
                        isPinching = false
                        previousDistance = 0f
                        onPinchEnd()
                    }
                }
            }
        }
    }
    .pointerInput(isScreenLocked, durationMs) {
        detectTapGestures(
            onTap = {
                controller.handleTap(isScreenLocked)
            },
            onDoubleTap = { offset ->
                if (!isScreenLocked) {
                    controller.handleDoubleTap(offset, viewSize = Size(size.width.toFloat(), size.height.toFloat()))
                }
            }
        )
    }
    .pointerInput(isScreenLocked, durationMs) {
        if (!isScreenLocked) {
            detectDragGestures(
                onDragStart = { offset ->
                    controller.handleDragStart(offset, currentPositionMs)
                },
                onDragEnd = {
                    controller.handleDragEnd(targetPositionMs)
                },
                onDragCancel = {
                    controller.handleDragCancel()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    controller.handleDrag(
                        dragAmount = dragAmount,
                        currentOffset = change.position,
                        viewSize = Size(size.width.toFloat(), size.height.toFloat()),
                        durationMs = durationMs
                    )
                }
            )
        }
    }

@Composable
fun VerticalLevelBar(
    progress: Float,
    activeBrush: Brush,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(clamped)
                .align(Alignment.BottomCenter)
                .background(activeBrush)
        )
    }
}

@Composable
fun MxBrightnessHud(
    brightnessLevel: Float,
    modifier: Modifier = Modifier
) {
    val clamped = brightnessLevel.coerceIn(0.05f, 1.0f)
    val percent = (clamped * 100).toInt().coerceIn(0, 100)
    val icon = when {
        clamped > 0.66f -> Icons.Default.Brightness7
        clamped > 0.33f -> Icons.Default.Brightness5
        else -> Icons.Default.BrightnessLow
    }
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .testTag("mx_brightness_hud")
            .width(46.dp)
            .height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Brightness",
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(22.dp)
            )
            VerticalLevelBar(
                progress = clamped,
                activeBrush = Brush.verticalGradient(
                    listOf(Color(0xFFFFD54F), Color(0xFFFF8F00))
                ),
                modifier = Modifier
                    .width(6.dp)
                    .height(96.dp)
            )
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MxVolumeHud(
    volumeLevel: Float,
    modifier: Modifier = Modifier
) {
    val clamped = volumeLevel.coerceIn(0f, 1.0f)
    val percent = (clamped * 100).toInt().coerceIn(0, 100)
    val icon = when {
        clamped <= 0f -> Icons.AutoMirrored.Filled.VolumeMute
        clamped < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
        else -> Icons.AutoMirrored.Filled.VolumeUp
    }
    Surface(
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
        shadowElevation = 8.dp,
        modifier = modifier
            .testTag("mx_volume_hud")
            .width(46.dp)
            .height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Volume",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(22.dp)
            )
            VerticalLevelBar(
                progress = clamped,
                activeBrush = Brush.verticalGradient(
                    listOf(Color(0xFF80D8FF), Color(0xFF00B0FF))
                ),
                modifier = Modifier
                    .width(6.dp)
                    .height(96.dp)
            )
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MxSeekScrubHud(
    seekDeltaMs: Long,
    targetPositionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.88f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
        shadowElevation = 10.dp,
        modifier = modifier.testTag("mx_seek_hud")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (seekDeltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val sign = if (seekDeltaMs >= 0) "+" else ""
                Text(
                    text = "[ $sign${seekDeltaMs / 1000}s ]",
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${formatGestureTime(targetPositionMs)} / ${formatGestureTime(durationMs)}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (durationMs > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                val progress = (targetPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Color(0xFF00E5FF))
                    )
                }
            }
        }
    }
}

@Composable
fun MxDoubleTapSeekBadge(
    isForward: Boolean,
    deltaMs: Long = 10000L,
    modifier: Modifier = Modifier
) {
    val seconds = abs(deltaMs) / 1000
    val label = if (isForward) "+${seconds}s" else "-${seconds}s"
    val testTag = if (isForward) "mx_double_tap_right_hud" else "mx_double_tap_left_hud"

    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
        modifier = modifier
            .testTag(testTag)
            .size(68.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TouchGestureControllerOverlay(
    controller: PlayerGestureController,
    isScreenLocked: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    targetPositionMs: Long,
    brightnessLevel: Float,
    volumeLevel: Float,
    gestureHudType: GestureHudType,
    seekDeltaMs: Long,
    modifier: Modifier = Modifier,
    videoZoomScale: Float = 1.0f,
    onZoomGesture: ((scaleMultiplier: Float, panDeltaX: Float, panDeltaY: Float) -> Unit)? = null,
    onPinchEnd: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("touch_gesture_controller_overlay")
            .mxPlayerGestures(
                controller = controller,
                isScreenLocked = isScreenLocked,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                targetPositionMs = targetPositionMs,
                onZoomGesture = { scale, panX, panY ->
                    onZoomGesture?.invoke(scale, panX, panY)
                },
                onPinchEnd = {
                    onPinchEnd?.invoke()
                }
            )
    ) {
        AnimatedVisibility(
            visible = gestureHudType == GestureHudType.BRIGHTNESS,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp)
        ) {
            MxBrightnessHud(brightnessLevel = brightnessLevel)
        }

        AnimatedVisibility(
            visible = gestureHudType == GestureHudType.VOLUME,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            MxVolumeHud(volumeLevel = volumeLevel)
        }

        AnimatedVisibility(
            visible = gestureHudType == GestureHudType.SEEK,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            MxSeekScrubHud(
                seekDeltaMs = seekDeltaMs,
                targetPositionMs = targetPositionMs,
                durationMs = durationMs
            )
        }

        AnimatedVisibility(
            visible = gestureHudType == GestureHudType.DOUBLE_TAP_LEFT,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 36.dp)
        ) {
            MxDoubleTapSeekBadge(isForward = false)
        }

        AnimatedVisibility(
            visible = gestureHudType == GestureHudType.DOUBLE_TAP_RIGHT,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 36.dp)
        ) {
            MxDoubleTapSeekBadge(isForward = true)
        }
    }
}

fun formatGestureTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
