package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/** Shared tone → color resolution for progress indicators. */
@Composable
private fun progressColor(tone: VBadgeTone): Color = when (tone) {
    VBadgeTone.Arctic -> VTheme.colors.tealDeep
    VBadgeTone.Accent -> VTheme.colors.accent
    VBadgeTone.Success -> VTheme.colors.successInk
    VBadgeTone.Warning -> VTheme.colors.warningInk
    VBadgeTone.Danger -> VTheme.colors.dangerInk
    VBadgeTone.Neutral -> VTheme.colors.ink3
}

/**
 * VProgressBar — a thin rounded track that fills to [value] (0..100), animated.
 * Translated from primitives.tsx → `VProgressBar`.
 */
@Composable
fun VProgressBar(
    value: Float,
    modifier: Modifier = Modifier,
    tone: VBadgeTone = VBadgeTone.Arctic,
    height: Dp = 6.dp,
) {
    val clamped = value.coerceIn(0f, 100f) / 100f
    val animated by animateFloatAsState(targetValue = clamped, animationSpec = tween(600))
    val fill = progressColor(tone)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(VTheme.colors.cream),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(fill),
        )
    }
}

/**
 * VProgressRing — a circular gauge drawn on Canvas; sweeps clockwise from 12 o'clock.
 * Translated from primitives.tsx → `VProgressRing`.
 */
@Composable
fun VProgressRing(
    value: Float,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    strokeWidth: Dp = 8.dp,
    tone: VBadgeTone = VBadgeTone.Arctic,
    label: String? = null,
) {
    val clamped = value.coerceIn(0f, 100f)
    val animated by animateFloatAsState(targetValue = clamped, animationSpec = tween(700))
    val fill = progressColor(tone)
    val track = VTheme.colors.cream

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = strokeWidth.toPx()
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
            drawArc(
                color = fill,
                startAngle = -90f,
                sweepAngle = animated * 3.6f,
                useCenter = false,
                style = Stroke(width = sw, cap = StrokeCap.Round),
            )
        }
        if (label != null) {
            Text(text = label, style = VTheme.type.dataSm.colored(VTheme.colors.ink))
        }
    }
}
