package com.littlebridge.enrollplus.ui.v2.screens.school

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * PewsPreview — a **label-free schematic teaser** for the PEWS (Predictive Early Warning System)
 * entry card on the admin home.
 *
 * RA-S10 (TIER 0, honesty / LAW 6): the previous implementation rendered **fabricated, named
 * "at-risk" students** ("Aman Verma 9-B 78", "Ishita Roy 10-A 64", …) and invented risk counts
 * ("180 students scored", Low 142 / Watch 27 / High 11) plus a fabricated "SUGGESTED INTERVENTION"
 * sentence directly on the live admin home — an admin could mistake fiction for signal and act on a
 * student who does not exist.
 *
 * The *real* at-risk cohort is served by `GET /api/v1/student-cohort` and rendered on the People tab
 * (opened via `onOpenPews`), so this preview must never invent data. It is now a purely illustrative
 * chrome — abstract risk-band bars, shimmer placeholder rows, no names, no numbers — that reads
 * unmistakably as "feature preview". All real numbers live one tap away on the cohort screen.
 */
@Composable
fun PewsPreview(modifier: Modifier = Modifier) {
    val c = VTheme.colors

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Risk-band schematic (no counts) ──────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.cream).padding(12.dp),
        ) {
            Text(
                "RISK BAND",
                style = VTheme.type.label.colored(c.ink3).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.08.em),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskBandTile("Low", Color(0xFFA8E6CF), Color(0xFF155E3A), Modifier.weight(1f))
                RiskBandTile("Watch", Color(0xFFFFD4A3), Color(0xFF7A3F00), Modifier.weight(1f))
                RiskBandTile("High", Color(0xFFFFADA8), Color(0xFF7A1C18), Modifier.weight(1f))
            }
        }

        // ── Highest-priority placeholder rows (no names, no scores) ──────────────
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(VIcons.AlertTriangle, contentDescription = null, tint = Color(0xFF7A1C18), modifier = Modifier.size(13.dp))
                Text(
                    "HIGHEST PRIORITY",
                    style = VTheme.type.label.colored(c.ink2).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.04.em),
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { PlaceholderRow() }
            }
        }

        // ── Suggested intervention (generic, dashed teal box) ────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.teal.copy(alpha = 0.10f))
                .dashedBorder(c.tealDeep.copy(alpha = 0.30f), 1.dp, 10.dp)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(VIcons.Sparkles, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(11.dp))
                Text(
                    "SUGGESTED INTERVENTION",
                    style = VTheme.type.label.colored(c.tealDeep).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Open the cohort to see this week's at-risk students and recommended actions.",
                style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 12.sp, lineHeight = 18.sp),
            )
        }
    }
}

/** A band tile showing only its colour + label — no count, since the real number lives on the cohort screen. */
@Composable
private fun RiskBandTile(label: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Abstract "value" bar instead of an invented number.
        Box(Modifier.width(22.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(fg.copy(alpha = 0.55f)))
        Spacer(Modifier.height(8.dp))
        Text(label, style = VTheme.type.caption.colored(fg).copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** A neutral placeholder row — avatar dot + two grey bars — that reads as "preview", never as a real student. */
@Composable
private fun PlaceholderRow() {
    val c = VTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.cream).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.08f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth(0.55f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.ink.copy(alpha = 0.10f)))
            Box(Modifier.fillMaxWidth(0.8f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.ink.copy(alpha = 0.06f)))
        }
    }
}

/** 1px dashed rounded border (the React `border: 1px dashed rgba(0,106,96,0.3)` style). */
private fun Modifier.dashedBorder(color: Color, strokeWidth: Dp, cornerRadius: Dp): Modifier =
    this.drawBehind {
        val sw = strokeWidth.toPx()
        val r = cornerRadius.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(sw * 3f, sw * 3f), 0f)
        drawRoundRect(
            color = color,
            topLeft = Offset(sw / 2f, sw / 2f),
            size = Size(size.width - sw, size.height - sw),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = sw, pathEffect = dash),
        )
    }
