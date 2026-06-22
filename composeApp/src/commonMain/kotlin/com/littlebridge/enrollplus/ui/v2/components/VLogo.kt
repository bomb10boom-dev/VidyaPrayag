package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme

/** Color intent for the [VLogo] mark. */
enum class VLogoTone { Ink, White, Teal, Navy }

/**
 * VLogo — the "Setu" (bridge) mark: a soft arc spanning two grounded pillars over a deck,
 * signalling the connection between school and home.
 *
 * Translated from primitives.tsx → `VLogo`. The original SVG (56×56 viewBox) is reproduced on a
 * Compose Canvas using a quadratic-bezier arc, a deck line, suspension cables and two pillar dots.
 */
@Composable
fun VLogo(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    withWord: Boolean = false,
    tone: VLogoTone = VLogoTone.Ink,
) {
    val c = VTheme.colors
    val ink = when (tone) {
        VLogoTone.White -> Color.White
        VLogoTone.Teal -> c.tealDeep
        VLogoTone.Navy -> c.navy
        VLogoTone.Ink -> c.ink
    }
    val accent = c.teal

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier) {
        Canvas(Modifier.size(size)) {
            // viewBox is 0..56; scale all coordinates to the actual canvas size.
            val s = this.size.width / 56f
            fun x(v: Float) = v * s
            fun y(v: Float) = v * s

            // outer rounded square plate (accent @ 12% opacity)
            drawRoundRect(
                color = accent.copy(alpha = 0.12f),
                topLeft = Offset(x(2f), y(2f)),
                size = Size(x(52f), y(52f)),
                cornerRadius = CornerRadius(x(14f), y(14f)),
            )

            // upper bridge arc: M12 32 Q28 12 44 32 (quadratic bezier)
            val arc = Path().apply {
                moveTo(x(12f), y(32f))
                quadraticTo(x(28f), y(12f), x(44f), y(32f))
            }
            drawPath(arc, color = accent, style = Stroke(width = 3f * s, cap = StrokeCap.Round))

            // lower deck: M10 40 H46
            drawLine(
                color = ink,
                start = Offset(x(10f), y(40f)),
                end = Offset(x(46f), y(40f)),
                strokeWidth = 3.2f * s,
                cap = StrokeCap.Round,
            )

            // suspension cables (70% opacity): vertical lines at 18, 28, 38
            val cable = ink.copy(alpha = 0.7f)
            drawLine(cable, Offset(x(18f), y(32f)), Offset(x(18f), y(40f)), strokeWidth = 1.4f * s, cap = StrokeCap.Round)
            drawLine(cable, Offset(x(28f), y(22f)), Offset(x(28f), y(40f)), strokeWidth = 1.4f * s, cap = StrokeCap.Round)
            drawLine(cable, Offset(x(38f), y(32f)), Offset(x(38f), y(40f)), strokeWidth = 1.4f * s, cap = StrokeCap.Round)

            // pillar caps
            drawCircle(color = ink, radius = 2.2f * s, center = Offset(x(12f), y(32f)))
            drawCircle(color = ink, radius = 2.2f * s, center = Offset(x(44f), y(32f)))
        }

        if (withWord) {
            Text(
                text = buildAnnotatedString {
                    append("Vidya")
                    withStyle(SpanStyle(color = accent)) { append("S") }
                    append("etu")
                },
                style = TextStyle(
                    color = ink,
                    fontSize = (size.value * 0.38f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                    fontFamily = VTheme.type.uiFamily,
                ),
            )
        }
    }
}
