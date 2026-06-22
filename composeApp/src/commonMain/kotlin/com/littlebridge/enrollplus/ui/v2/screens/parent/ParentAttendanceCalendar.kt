package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentAttendanceDayDto
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.util.MONTH_LONG
import com.littlebridge.enrollplus.util.WEEKDAY_SHORT
import com.littlebridge.enrollplus.util.dayOfWeek
import com.littlebridge.enrollplus.util.daysInMonth
import com.littlebridge.enrollplus.util.isoOf
import com.littlebridge.enrollplus.util.parseIsoDate
import com.littlebridge.enrollplus.util.todayIso

/**
 * RA-S19 — Parent attendance month-calendar (freely navigable).
 *
 * A true month grid: each day cell is colour-coded by its attendance status — present=green,
 * late=amber, absent=red (COLOR IS SEMANTIC: the lavender brand accent is NOT used to mean
 * "present"; green is). The month can be paged FREELY in either direction (the parent can walk
 * back to any prior month or forward toward today), not just the months that happen to contain
 * marked data. Built entirely with V* primitives + [VTheme] tokens + the shared kotlinx-free
 * date helpers in `util/DateUtil.kt`.
 *
 * Input is the same `records: List<ParentAttendanceDayDto>` the endpoint already returns
 * (`date` = "YYYY-MM-DD", `status` = present|late|absent), so no backend change is needed.
 */
@Composable
internal fun ParentAttendanceCalendar(
    records: List<ParentAttendanceDayDto>,
) {
    val c = VTheme.colors

    // Index records by ISO date for O(1) day lookup. If a day has multiple rows
    // (it shouldn't — the upsert is per-day), the last one wins.
    val byDate = remember(records) { records.associate { it.date to it.status.lowercase() } }

    // Anchor the calendar on the most recent month that actually has data; otherwise the current
    // real month. From there, navigation is FREE in both directions (no longer clamped to the set
    // of data-months) — exactly the "switch the month" interaction the brief asks for.
    val (startYear, startMonth) = remember(records) {
        val newest = records.mapNotNull { parseIsoDate(it.date)?.let { (y, m, _) -> y to m } }
            .maxWithOrNull(compareBy({ it.first }, { it.second }))
        newest ?: (parseIsoDate(todayIso())?.let { (y, m, _) -> y to m } ?: (2026 to 1))
    }

    // Visible month, expressed as an absolute month-offset relative to the anchor so we can page
    // smoothly across year boundaries with simple integer math.
    var monthOffset by remember(startYear, startMonth) { mutableStateOf(0) }
    val (year, month) = remember(startYear, startMonth, monthOffset) {
        addMonths(startYear, startMonth, monthOffset)
    }
    // Don't let parents page into the future beyond the current real month — there's nothing there.
    val (todayYear, todayMonth) = remember {
        parseIsoDate(todayIso())?.let { (y, m, _) -> y to m } ?: (startYear to startMonth)
    }
    val canGoForward = (year < todayYear) || (year == todayYear && month < todayMonth)

    VCard {
        // ── Header: pager ◄ · Month YYYY · ► ────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PagerArrow(icon = VIcons.ChevronLeft, enabled = true) { monthOffset-- }
            AnimatedContent(
                targetState = year to month,
                transitionSpec = {
                    val forward = targetState.toComparable() > initialState.toComparable()
                    if (forward) {
                        (slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(220)) { -it / 2 } + fadeOut(tween(120)))
                    } else {
                        (slideInHorizontally(tween(220)) { -it / 2 } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(120)))
                    }
                },
                label = "calMonth",
            ) { (y, m) ->
                Text(
                    "${MONTH_LONG.getOrNull(m - 1) ?: "—"} $y",
                    style = VTheme.type.bodyStrong.colored(c.ink).copy(fontWeight = FontWeight.Bold),
                )
            }
            PagerArrow(icon = VIcons.ChevronRight, enabled = canGoForward) {
                if (canGoForward) monthOffset++
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Weekday header row ──────────────────────────────────────────────
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEach { wd ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(wd, style = VTheme.type.caption.colored(c.ink3), textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Day grid ────────────────────────────────────────────────────────
        val firstWeekday = dayOfWeek(year, month, 1) // 0=Sun .. 6=Sat
        val totalDays = daysInMonth(year, month)
        val cells = firstWeekday + totalDays
        val rows = (cells + 6) / 7
        val today = remember { todayIso() }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (week in 0 until rows) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (dow in 0 until 7) {
                        val cellIndex = week * 7 + dow
                        val day = cellIndex - firstWeekday + 1
                        Box(Modifier.weight(1f)) {
                            if (day in 1..totalDays) {
                                val iso = isoOf(year, month, day)
                                DayCell(day = day, status = byDate[iso], isToday = iso == today)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Legend ──────────────────────────────────────────────────────────
        VLabel("Legend")
        Spacer(Modifier.height(8.dp))
        // COLOR IS SEMANTIC: present=green, late=amber, absent=red — meaning carried by colour.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(c.successInk, "Present")
            LegendDot(c.warningInk, "Late")
            LegendDot(c.dangerInk, "Absent")
        }
    }
}

private fun Pair<Int, Int>.toComparable(): Int = first * 12 + second

/** Adds [delta] months (can be negative) to a (year, 1-based month), returning the new pair. */
private fun addMonths(year: Int, month: Int, delta: Int): Pair<Int, Int> {
    // Convert to a zero-based absolute month index, shift, convert back.
    val abs = year * 12 + (month - 1) + delta
    val y = abs / 12
    val m = abs % 12 + 1
    return y to m
}

@Composable
private fun PagerArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = VTheme.colors
    val ix = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (enabled) c.cream else Color.Transparent)
            .then(if (enabled) Modifier.clickable(interactionSource = ix, indication = null, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) c.ink2 else c.placeholder,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DayCell(day: Int, status: String?, isToday: Boolean) {
    val c = VTheme.colors
    // COLOR IS SEMANTIC: present=green, late=amber, absent=red. Lavender is reserved for the brand
    // accent (today's ring) — never overloaded to mean "present".
    val fill = when (status) {
        "present" -> c.successInk
        "late" -> c.warningInk
        "absent" -> c.dangerInk
        else -> Color.Transparent
    }
    val fg = if (status != null) c.card else c.ink2
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (status == null) c.cream.copy(alpha = 0.4f) else fill)
            .border(
                if (isToday) 1.5.dp else 1.dp,
                when {
                    isToday -> c.accent
                    status == null -> c.hairline
                    else -> Color.Transparent
                },
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$day",
            style = VTheme.type.caption.colored(if (isToday && status == null) c.accentDeep else fg)
                .copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val c = VTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(label, style = VTheme.type.caption.colored(c.ink2))
    }
}
