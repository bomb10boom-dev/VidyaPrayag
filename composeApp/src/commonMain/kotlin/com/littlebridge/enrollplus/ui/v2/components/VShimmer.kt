package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme

/**
 * ShimmerBox — the single reusable loading-placeholder primitive (FEATURE 2).
 *
 * A rectangle (or any [shape]) filled with a horizontally sweeping gradient that animates
 * surface → outline → surface, giving the universal "content loading" shimmer. It is built
 * entirely from the frozen design language — the gradient stops are the existing
 * [VTheme.colors.card] (surface) and [VTheme.colors.border2] (outline) tokens, so it never
 * introduces a new colour (RULE-1). Pure Compose, no external library (FEATURE 2 requirement).
 *
 * Timing: a single [rememberInfiniteTransition] drives a 0→1 phase over 1200ms [LinearEasing],
 * which is mapped to the gradient's horizontal translation. The transition is keyed only to the
 * composable's lifetime, so there is no recomposition loop and no layout work per frame — only
 * the brush's start/end offset changes (RULE-2: no jank, no layout shift).
 *
 * The [shape] should match the shape of the real content the box stands in for, so the skeleton
 * reads as a faithful silhouette of the loaded layout.
 *
 * ```
 * ShimmerBox(height = 20.dp, shape = RoundedCornerShape(4.dp))            // a text line
 * ShimmerBox(width = 48.dp, height = 48.dp, shape = CircleShape)         // an avatar
 * ```
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    width: Dp = Dp.Unspecified,
    height: Dp = 20.dp,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    // Phase sweeps 0→1 every 1200ms; mapped below into the gradient translation so the
    // highlight band travels left→right across the box.
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-phase",
    )

    val c = VTheme.colors
    // surface → outline → surface: a soft highlight band riding over the surface colour.
    val brush = Brush.linearGradient(
        colors = listOf(c.card, c.border2, c.card),
        // Travel the band across a band ~2x wider than the box so the sweep is smooth and
        // the box is never fully one flat colour mid-cycle.
        start = Offset(x = phase * 1000f - 500f, y = 0f),
        end = Offset(x = phase * 1000f + 500f, y = 0f),
    )

    Box(
        modifier
            .then(if (width != Dp.Unspecified) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(shape)
            .background(brush),
    )
}
