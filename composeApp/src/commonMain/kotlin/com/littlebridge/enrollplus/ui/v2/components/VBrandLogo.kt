package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme

/**
 * VBrandLogo — the **single source of truth** for the VidyaSetu "Setu" (bridge) mark.
 *
 * This is the exact bridge geometry the auth surface renders: a white arc spanning two grounded
 * pillars over a deck, three suspension cables and a navy centre node — drawn on a 56-unit viewBox
 * so it scales crisply at any size. Splash, landing and the auth screens all draw from here so the
 * logo is byte-identical everywhere.
 *
 * Two presentations:
 *  • [VBrandLogo]      — the glass "cube" plate (frosted white-on-teal) carrying the mark, used on
 *                         the teal hero of Splash / auth surfaces.
 *  • [VBridgeMark]     — the bare stroked mark (no plate), for when the caller supplies the surface.
 */
@Composable
fun VBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    cornerRadius: Dp = 28.dp,
    plateAlpha: Float = 0.16f,
    borderAlpha: Float = 0.18f,
) {
    val navy = VTheme.colors.navy
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color.White.copy(alpha = plateAlpha))
            .border(1.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        VBridgeMark(Modifier.size(size * 0.625f), navyDot = navy)
    }
}

/**
 * VBridgeMark — the bare bridge logo, drawn directly (no plate) on a 56-unit viewBox:
 * white arc + deck + 3 cables, two white pillar caps and a navy centre node at (28,22).
 *
 * Pass [stroke] to recolor the strokes (defaults to white, for the teal hero); the centre node uses
 * [navyDot]. This component is the single source of the mark's geometry across every surface.
 */
@Composable
fun VBridgeMark(
    modifier: Modifier = Modifier,
    navyDot: Color = VTheme.colors.navy,
    stroke: Color = Color.White,
) {
    Canvas(modifier) { drawBridge(stroke = stroke, navyDot = navyDot) }
}

/** Shared draw routine — keeps the geometry in one place. */
private fun DrawScope.drawBridge(stroke: Color, navyDot: Color) {
    val s = size.width / 56f
    fun x(v: Float) = v * s
    fun y(v: Float) = v * s

    // arc  M12 32 Q28 12 44 32
    val arc = Path().apply {
        moveTo(x(12f), y(32f))
        quadraticTo(x(28f), y(12f), x(44f), y(32f))
    }
    drawPath(arc, color = stroke, style = Stroke(width = 3f * s, cap = StrokeCap.Round))

    // deck  M10 40 H46
    drawLine(stroke, Offset(x(10f), y(40f)), Offset(x(46f), y(40f)), strokeWidth = 3.5f * s, cap = StrokeCap.Round)

    // cables 18 / 28 / 38
    val cable = stroke.copy(alpha = 0.78f)
    drawLine(cable, Offset(x(18f), y(32f)), Offset(x(18f), y(40f)), strokeWidth = 1.6f * s, cap = StrokeCap.Round)
    drawLine(cable, Offset(x(28f), y(22f)), Offset(x(28f), y(40f)), strokeWidth = 1.6f * s, cap = StrokeCap.Round)
    drawLine(cable, Offset(x(38f), y(32f)), Offset(x(38f), y(40f)), strokeWidth = 1.6f * s, cap = StrokeCap.Round)

    // pillar caps + navy centre node
    drawCircle(stroke, radius = 2.6f * s, center = Offset(x(12f), y(32f)))
    drawCircle(stroke, radius = 2.6f * s, center = Offset(x(44f), y(32f)))
    drawCircle(navyDot, radius = 2.4f * s, center = Offset(x(28f), y(22f)))
}
