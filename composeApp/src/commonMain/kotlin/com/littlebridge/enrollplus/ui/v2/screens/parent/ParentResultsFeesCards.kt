package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.domain.model.FeeData
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentMarkDto
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import kotlin.math.roundToInt

/**
 * Results card — the child's latest published result with a sparkline trend, faithful to
 * the website reference's results card. Every number is real (from /marks): the latest
 * scored exam, the delta vs the previous same-subject result, and the trend line.
 */
@Composable
fun ParentResultsCard(
    latestMark: ParentMarkDto?,
    previousMark: ParentMarkDto?,
    trend: List<Double>,
    onOpenAcademics: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors

    // Nothing published yet → a premium "awaiting results" state, never a collapsed card.
    if (latestMark == null || latestMark.marks == null) {
        VCard(modifier = modifier, padding = 14.dp, onClick = onOpenAcademics) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(VIcons.TrendingUp, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(14.dp))
                    Text(
                        "ACADEMICS",
                        style = VTheme.type.label.colored(c.ink3).copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    )
                }
                Icon(VIcons.ChevronRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.accent.copy(alpha = 0.07f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.ClipboardList, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "No results published yet",
                        style = VTheme.type.h3.colored(c.navyDeep).copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    )
                    Text(
                        "See the full progress report in Academics",
                        style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp),
                    )
                }
            }
        }
        return
    }

    val score = latestMark.marks!!.roundToInt()
    val max = latestMark.maxMarks
    val delta = if (previousMark?.marks != null) {
        (latestMark.marks!! - previousMark.marks!!).roundToInt()
    } else null

    VCard(modifier = modifier, padding = 14.dp, onClick = onOpenAcademics) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${latestMark.subject.uppercase()} · ${latestMark.examName.uppercase()}",
                    style = VTheme.type.label.colored(c.ink3).copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$score",
                        style = VTheme.type.h2.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold),
                    )
                    Text(
                        "/$max",
                        style = VTheme.type.bodyStrong.colored(c.ink3).copy(fontSize = 13.sp),
                    )
                    if (delta != null) {
                        Spacer(Modifier.width(6.dp))
                        val up = delta >= 0
                        // Semantic trend: an improvement reads success green, a regression danger
                        // red — the instant traffic-light read a parent expects on a score delta.
                        Text(
                            "${if (up) "+" else ""}$delta vs last",
                            style = VTheme.type.label.colored(if (up) c.successInk else c.dangerInk)
                                .copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        )
                    }
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("Published", style = VTheme.type.label.colored(c.accentDeep).copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp))
            }
        }

        if (trend.size >= 2) {
            Spacer(Modifier.height(10.dp))
            Sparkline(values = trend, modifier = Modifier.fillMaxWidth().height(30.dp))
        }
    }
}

/** A filled sparkline of normalised values (0..100), violet line + soft area fill. */
@Composable
private fun Sparkline(values: List<Double>, modifier: Modifier) {
    val c = VTheme.colors
    val line = c.accent
    val fill = Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.28f), c.accent.copy(alpha = 0f)))

    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val minV = values.min()
        val maxV = values.max()
        val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
        val stepX = w / (values.size - 1)
        val pad = 3f

        fun pointAt(i: Int): Offset {
            val x = stepX * i
            val norm = ((values[i] - minV) / range).toFloat()
            val y = h - pad - norm * (h - pad * 2)
            return Offset(x, y)
        }

        val linePath = Path()
        val areaPath = Path()
        val p0 = pointAt(0)
        linePath.moveTo(p0.x, p0.y)
        areaPath.moveTo(p0.x, h)
        areaPath.lineTo(p0.x, p0.y)
        for (i in 1 until values.size) {
            val p = pointAt(i)
            linePath.lineTo(p.x, p.y)
            areaPath.lineTo(p.x, p.y)
        }
        val last = pointAt(values.size - 1)
        areaPath.lineTo(last.x, h)
        areaPath.close()

        drawPath(areaPath, brush = fill)
        drawPath(linePath, color = line, style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(color = line, radius = 3f, center = last)
    }
}

/**
 * Fees card — the child's outstanding fees, faithful to the website reference's fees row.
 * Every number is real (from /fees scoped to the child): outstanding amount + overdue count.
 * Tapping opens the full Fees tab (host-wired).
 */
@Composable
fun ParentFeesCard(
    fees: FeeData?,
    onOpenFees: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors

    // No fees data yet → a premium placeholder row, never a vanished card.
    if (fees == null) {
        VCard(modifier = modifier, padding = 12.dp, onClick = onOpenFees) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.Wallet, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(17.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Fees",
                        style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(
                        "Open the ledger to view dues & payments",
                        style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp),
                    )
                }
                Icon(VIcons.ChevronRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(16.dp))
            }
        }
        return
    }

    val overdue = fees.overdueCount
    val outstanding = fees.outstandingFees

    VCard(modifier = modifier, padding = 12.dp, onClick = onOpenFees) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    // Semantic: overdue = warning amber, all-clear = success green (calm "you're
                    // good" signal) — reserves the brand violet for actions, not a paid state.
                    .background(if (overdue > 0) c.warning.copy(alpha = 0.5f) else c.success.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    VIcons.Wallet,
                    contentDescription = null,
                    tint = if (overdue > 0) c.warningInk else c.successInk,
                    modifier = Modifier.size(17.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "Fees",
                    style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    if (overdue > 0) "$overdue overdue · tap for the breakdown" else "All clear · tap for the ledger",
                    style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    outstanding,
                    style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    if (overdue > 0) "Outstanding" else "Nothing due",
                    // Semantic: an overdue balance reads warning amber, a clear ledger reads
                    // success green — the instant "all good / needs attention" traffic light.
                    style = VTheme.type.label.colored(if (overdue > 0) c.warningInk else c.successInk)
                        .copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                )
            }
        }
    }
}
