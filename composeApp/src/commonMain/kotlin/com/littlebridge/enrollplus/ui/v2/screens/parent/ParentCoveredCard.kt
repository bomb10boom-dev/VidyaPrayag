package com.littlebridge.enrollplus.ui.v2.screens.parent

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.CoveredUnit
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VStatusDot
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * "Covered today" card — the syllabus units the child's class covered today, live, and an
 * end-of-day summary once the school day is over. Tapping opens an in-app detail overlay
 * (handled by the dashboard, NOT a separate screen) with the full per-subject breakdown.
 *
 * LAW: every unit/number from the real /syllabus feed; nothing hardcoded.
 */
@Composable
fun ParentCoveredCard(
    coveredToday: List<CoveredUnit>,
    schoolDayEnded: Boolean,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val count = coveredToday.size

    VCard(modifier = modifier, padding = 14.dp, onClick = onOpenDetail) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(VIcons.BookOpen, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(14.dp))
                Text(
                    if (schoolDayEnded) "COVERED TODAY · SUMMARY" else "COVERED TODAY · LIVE",
                    style = VTheme.type.label.colored(c.ink3).copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
            }
            // Semantic "live" pulse — an active, in-progress signal reads in success green
            // (matching the dashboard's present=green language), not a flat brand tint.
            if (!schoolDayEnded) VStatusDot(color = c.successInk, size = 6.dp)
        }

        Spacer(Modifier.height(8.dp))

        if (count == 0) {
            // Premium empty state — tinted plate with an icon + copy.
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
                    Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.BookOpen, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (schoolDayEnded) "Nothing logged today" else "Nothing covered yet today",
                        style = VTheme.type.h3.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                    )
                    Text(
                        if (schoolDayEnded) "Teachers didn't log syllabus coverage today"
                        else "This fills in live as the school day progresses",
                        style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp),
                    )
                }
            }
        } else {
            val subjectCount = coveredToday.map { it.subject }.distinct().size
            Text(
                "$count ${if (count == 1) "topic" else "topics"} across $subjectCount ${if (subjectCount == 1) "subject" else "subjects"}",
                style = VTheme.type.h3.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
            )
            Spacer(Modifier.height(8.dp))
            // a tight preview — first two units, the rest folded into "+N more"
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                coveredToday.take(2).forEach { u -> CoveredRow(u) }
                if (count > 2) {
                    Text(
                        "+${count - 2} more",
                        style = VTheme.type.label.colored(c.accentDeep).copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Tap for the full breakdown",
            style = VTheme.type.label.colored(c.ink3).copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
        )
    }
}

@Composable
private fun CoveredRow(u: CoveredUnit) {
    val c = VTheme.colors
    // Per-subject harmonious accent (keyed by name → the same subject is always the same hue,
    // here and everywhere else in the portal), so the list isn't a wall of one violet.
    val tint = parentSubjectColor(c, u.subject)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(999.dp)).background(tint.copy(alpha = 0.14f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(u.subject, style = VTheme.type.label.colored(tint).copy(fontWeight = FontWeight.Bold, fontSize = 9.sp))
        }
        Text(
            u.title,
            style = VTheme.type.body.colored(c.navyDeep).copy(fontSize = 12.sp),
            maxLines = 1,
        )
    }
}
