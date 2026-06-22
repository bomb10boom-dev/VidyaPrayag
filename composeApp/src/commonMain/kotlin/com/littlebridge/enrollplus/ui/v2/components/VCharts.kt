package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/** One slice of a [VDonut] or row of [VBars]. */
data class VChartDatum(val label: String, val value: Float, val color: Color = Color.Unspecified)

// ─────────────────────────────────────────────────────────────────────────────
// VDonut — animated multi-segment ring with a center slot
// ─────────────────────────────────────────────────────────────────────────────

/**
 * VDonut — a stroke-based donut chart. Segments sweep in clockwise from 12 o'clock and animate
 * their length on first composition. Translated from charts.tsx → `VDonut`.
 */
@Composable
fun VDonut(
    data: List<VChartDatum>,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    thickness: Dp = 18.dp,
    center: (@Composable () -> Unit)? = null,
) {
    val c = VTheme.colors
    val total = data.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(800), label = "donut")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = thickness.toPx()
            val inset = sw / 2f
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(inset, inset)

            // track
            drawArc(
                color = c.cream,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = sw),
            )
            // segments
            var startAngle = -90f
            data.forEach { d ->
                val sweep = (d.value / total) * 360f * progress
                val segColor = if (d.color == Color.Unspecified) c.teal else d.color
                drawArc(
                    color = segColor,
                    startAngle = startAngle, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = sw),
                )
                startAngle += (d.value / total) * 360f
            }
        }
        center?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VSparkline — area + line micro chart
// ─────────────────────────────────────────────────────────────────────────────

/**
 * VSparkline — a minimal filled area sparkline with a line on top and an end dot.
 * Translated from charts.tsx → `VSparkline`.
 */
@Composable
fun VSparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 36.dp,
    color: Color = Color.Unspecified,
) {
    val lineColor = if (color == Color.Unspecified) VTheme.colors.tealDeep else color
    if (values.size < 2) {
        Box(modifier.size(width, height))
        return
    }
    val animated by animateFloatAsState(targetValue = 1f, animationSpec = tween(1100), label = "spark")

    Canvas(modifier.size(width, height)) {
        val w = this.size.width
        val h = this.size.height
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it != 0f } ?: 1f
        val stepX = w / (values.size - 1)

        fun pointAt(i: Int): Offset {
            val x = i * stepX
            val y = h - ((values[i] - min) / span) * (h - 4f) - 2f
            return Offset(x, y)
        }

        val pts = values.indices.map { pointAt(it) }

        // area path
        val area = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            area,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0f)),
            ),
        )

        // line path (animated reveal by clipping the x extent)
        val revealX = w * animated
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                if (pts[i].x <= revealX) lineTo(pts[i].x, pts[i].y)
            }
        }
        drawPath(line, color = lineColor, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // end dot
        val last = pts.last()
        drawCircle(color = lineColor, radius = 3f, center = last)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VBars — vertical bars with the last bar highlighted
// ─────────────────────────────────────────────────────────────────────────────

/**
 * VBars — a compact vertical bar chart; the final bar is emphasized (teal-deep + value label).
 * Translated from charts.tsx → `VBars`.
 */
@Composable
fun VBars(
    data: List<VChartDatum>,
    modifier: Modifier = Modifier,
    height: Dp = 140.dp,
) {
    val c = VTheme.colors
    val max = (data.maxOfOrNull { it.value } ?: 1f).takeIf { it > 0f } ?: 1f
    val progress by animateFloatAsState(targetValue = 1f, animationSpec = tween(600), label = "bars")

    Row(
        modifier.height(height).fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        data.forEachIndexed { i, d ->
            val isLast = i == data.lastIndex
            val barColor = if (isLast) c.tealDeep else c.teal.copy(alpha = 0.45f)
            val frac = (d.value / max) * progress
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (isLast) {
                    Text(
                        d.value.toInt().toString(),
                        style = VTheme.type.dataSm.colored(c.tealDeep).copy(fontWeight = FontWeight.Bold),
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(frac.coerceIn(0.001f, 1f))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(barColor),
                )
                Text(
                    d.label,
                    style = VTheme.type.label.colored(c.ink3).copy(letterSpacing = TextUnit.Unspecified),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VLegendDot — a labeled color chip beside charts
// ─────────────────────────────────────────────────────────────────────────────

/** VLegendDot — color swatch + label + optional value. Translated from charts.tsx → `VLegendDot`. */
@Composable
fun VLegendDot(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val c = VTheme.colors
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = VTheme.type.caption.colored(c.ink2))
        if (value != null) {
            Text(value, style = VTheme.type.dataSm.colored(c.ink).copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
