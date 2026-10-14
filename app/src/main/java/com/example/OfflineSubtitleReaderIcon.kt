package com.example

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OfflineSubtitleReaderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Color(0xFF10B981),
    badgeTint: Color = Color(0xFF047857),
    isReading: Boolean = false,
    contentDescription: String? = "Offline Subtitles Reader"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ReaderIconPulse")
    val pulseScale by if (isReading) {
        infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "readerPulseScale"
        )
    } else {
        rememberUpdatedState(1.0f)
    }

    Box(
        modifier = modifier
            .size(size)
            .scale(pulseScale),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.RecordVoiceOver,
            contentDescription = contentDescription,
            tint = if (isReading) Color(0xFF10B981) else tint,
            modifier = Modifier.size(size)
        )
    }
}

@Composable
fun OfflineSubtitleReaderHeaderIcon(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    isReading: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        color = Color(0xFF10B981).copy(alpha = 0.16f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            OfflineSubtitleReaderIcon(
                size = size * 0.65f,
                tint = Color(0xFF10B981),
                badgeTint = Color(0xFF047857),
                isReading = isReading
            )
        }
    }
}

@Composable
fun OfflineSubtitleReaderPill(
    isReading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isReading) Color(0xFF10B981).copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isReading) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier.testTag("offline_subtitle_reader_pill_btn")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OfflineSubtitleReaderIcon(
                size = 17.dp,
                tint = if (isReading) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                badgeTint = if (isReading) Color(0xFF10B981) else Color(0xFF047857),
                isReading = isReading
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isReading) "Reading..." else if (compact) "Reader" else "Subtitle Reader",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isReading) Color(0xFF047857) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
