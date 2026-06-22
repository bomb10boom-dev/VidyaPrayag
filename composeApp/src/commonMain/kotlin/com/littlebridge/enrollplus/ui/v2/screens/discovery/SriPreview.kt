package com.littlebridge.enrollplus.ui.v2.screens.discovery

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * SriPreview — `mockups.tsx → SRIPreview`. The preview slot inside the Discovery "School Reputation
 * Index" VComingSoon: a big navy score / 10, a "+0.3 YoY" pastel-mint chip, and 6 weighted signal
 * bars (label · teal-deep fill on cream track · numeric weight).
 */
@Composable
fun SriPreview(score: Float, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    val signals = listOf(
        "Academic outcomes" to 92,
        "Teacher retention" to 84,
        "Parent sentiment" to 78,
        "Safety & infra" to 88,
        "Co-curricular" to 71,
        "Attendance norms" to 86,
    )
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    buildAnnotatedString {
                        append(formatScore(score))
                        withStyle(SpanStyle(fontSize = 16.sp, color = c.ink3)) { append("/10") }
                    },
                    style = VTheme.type.dataLg.colored(c.navy).copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
                )
                Text("Above Lucknow median (7.4)", style = VTheme.type.label.colored(c.ink3).copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp), modifier = Modifier.padding(top = 2.dp))
            }
            // "+0.3 YoY" chip — pastel mint bg #A8E6CF@40%, dark-green ink #155E3A.
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFFA8E6CF).copy(alpha = 0.4f)).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(VIcons.TrendingUp, contentDescription = null, tint = Color(0xFF155E3A), modifier = Modifier.size(11.dp))
                Text("+0.3 YoY", style = VTheme.type.label.colored(Color(0xFF155E3A)).copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            signals.forEach { (label, w) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = VTheme.type.label.colored(c.ink2).copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp), modifier = Modifier.width(116.dp))
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(c.cream)) {
                        Box(Modifier.fillMaxWidth(w / 100f).height(6.dp).clip(RoundedCornerShape(999.dp)).background(c.tealDeep))
                    }
                    Text(w.toString(), style = VTheme.type.dataSm.colored(c.ink3).copy(fontSize = 11.sp), textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                }
            }
        }
    }
}

/** Renders a 1-decimal score (e.g. 7.9) without pulling in platform String.format. */
private fun formatScore(v: Float): String {
    val tenths = kotlin.math.round(v * 10f).toInt()
    return "${tenths / 10}.${tenths % 10}"
}
