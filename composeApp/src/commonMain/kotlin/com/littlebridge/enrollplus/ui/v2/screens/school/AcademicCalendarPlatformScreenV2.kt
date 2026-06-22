package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicCalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventType
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarKpiDto
import com.littlebridge.enrollplus.feature.admin.presentation.AcademicCalendarPlatformViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.CalendarViewMode
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.components.VPullRefresh
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * AcademicCalendarPlatformScreenV2 — the premium Academic Calendar platform
 * (VP-CAL). Nine sections, top to bottom:
 *   1. Hero Overview (academic year / days / holidays / next event)
 *   2. Upcoming Highlights carousel
 *   3. View Switcher (Month / Agenda / Timeline)
 *   4. Interactive Calendar (indicators / counts / colors / tap)
 *   5. Upcoming Events Timeline
 *   6. Draft Events
 *   7. Published Events
 *   8. Academic Milestones
 *   9. Calendar Analytics (KPI carousel)
 *
 * Built entirely from the V* design system (no raw Material surfaces). Pull-to-
 * refresh, empty + loading states, and a "Create Event" CTA into the 7-step
 * wizard are all wired here.
 */
@Composable
fun AcademicCalendarPlatformScreenV2(
    onBack: () -> Unit,
    onCreateEvent: () -> Unit,
    onOpenEvent: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AcademicCalendarPlatformViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val c = VTheme.colors

    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        VBackHeader(
            title = "Academic Calendar",
            onBack = onBack,
            action = {
                VButton(
                    text = "Create",
                    onClick = onCreateEvent,
                    size = VButtonSize.Sm,
                    tone = VButtonTone.Teal,
                    leading = { Icon(VIcons.Plus, null, tint = Color.White, modifier = Modifier.size(14.dp)) },
                )
            },
        )

        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            isEmpty = state.isEmpty,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            emptyTitle = "No calendar yet",
            emptyBody = "Create your first academic event to start planning the year.",
            emptyIcon = VIcons.Calendar,
            onRetry = { viewModel.load() },
        ) {
            VPullRefresh(isRefreshing = state.isRefreshing, onRefresh = { viewModel.refresh() }) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val dash = state.dashboard

                    // ── 1. Hero Overview ─────────────────────────────────────
                    HeroOverview(dash?.hero?.academicYear, dash?.hero?.academicDays ?: 0,
                        dash?.hero?.holidayDays ?: 0, dash?.hero?.totalEvents ?: 0, dash?.hero?.nextEvent)

                    // ── 2. Upcoming Highlights carousel ──────────────────────
                    val highlights = dash?.upcomingHighlights.orEmpty()
                    if (highlights.isNotEmpty()) {
                        VLabel("Upcoming highlights")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(highlights.size) { i ->
                                HighlightCard(highlights[i], onClick = { onOpenEvent(highlights[i].id) })
                            }
                        }
                    }

                    // ── 3. View Switcher ─────────────────────────────────────
                    VLabel("View")
                    ViewSwitcher(state.viewMode, onSelect = viewModel::setViewMode)

                    // ── 4 / 5 — content for the selected view ────────────────
                    when (state.viewMode) {
                        CalendarViewMode.MONTH -> InteractiveCalendar(state.events, onOpenEvent)
                        CalendarViewMode.AGENDA -> AgendaList(state.events, onOpenEvent)
                        CalendarViewMode.TIMELINE -> TimelineList(dash?.upcomingTimeline.orEmpty(), onOpenEvent)
                    }

                    // ── 5. Upcoming Events Timeline (always shown) ───────────
                    val timeline = dash?.upcomingTimeline.orEmpty()
                    if (timeline.isNotEmpty()) {
                        VLabel("Upcoming events")
                        timeline.take(6).forEach { EventRow(it, onClick = { onOpenEvent(it.id) }) }
                    }

                    // ── 6. Draft Events ──────────────────────────────────────
                    val drafts = dash?.draftEvents.orEmpty()
                    if (drafts.isNotEmpty()) {
                        VLabel("Draft events")
                        drafts.take(6).forEach {
                            EventRow(it, onClick = { onOpenEvent(it.id) }, trailingBadge = "DRAFT")
                        }
                    }

                    // ── 7. Published Events ──────────────────────────────────
                    val published = dash?.publishedEvents.orEmpty()
                    if (published.isNotEmpty()) {
                        VLabel("Published events")
                        published.take(6).forEach {
                            EventRow(it, onClick = { onOpenEvent(it.id) }, trailingBadge = "LIVE")
                        }
                    }

                    // ── 8. Academic Milestones ───────────────────────────────
                    val milestones = dash?.milestones.orEmpty()
                    if (milestones.isNotEmpty()) {
                        VLabel("Academic milestones")
                        milestones.take(6).forEach { MilestoneRow(it, onClick = { onOpenEvent(it.id) }) }
                    }

                    // ── 9. Calendar Analytics (KPI carousel) ─────────────────
                    val analytics = dash?.analytics.orEmpty()
                    if (analytics.isNotEmpty()) {
                        VLabel("Calendar analytics")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(analytics.size) { i -> KpiCard(analytics[i]) }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 1 — Hero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroOverview(
    academicYear: String?,
    academicDays: Int,
    holidayDays: Int,
    totalEvents: Int,
    nextEvent: AcademicCalendarEventDto?,
) {
    val c = VTheme.colors
    VCard(background = c.navy, border = false) {
        Text(
            academicYear?.let { "Academic Year $it" } ?: "Academic Calendar",
            style = VTheme.type.h3.colored(Color.White),
        )
        Spacer(Modifier.height(4.dp))
        Text("Centralized planning & scheduling", style = VTheme.type.caption.colored(Color.White.copy(alpha = 0.7f)))
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroStat("Events", totalEvents.toString(), Modifier.weight(1f))
            HeroStat("School days", academicDays.toString(), Modifier.weight(1f))
            HeroStat("Holidays", holidayDays.toString(), Modifier.weight(1f))
        }
        if (nextEvent != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.08f)).padding(12.dp),
            ) {
                Column {
                    Text("NEXT EVENT", style = VTheme.type.label.colored(c.teal))
                    Spacer(Modifier.height(2.dp))
                    Text(nextEvent.title, style = VTheme.type.bodyStrong.colored(Color.White))
                    Text(
                        formatRange(nextEvent.startDate, nextEvent.endDate),
                        style = VTheme.type.caption.colored(Color.White.copy(alpha = 0.7f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = VTheme.type.dataLg.colored(Color.White).copy(fontSize = 22.sp))
        Text(label, style = VTheme.type.label.colored(Color.White.copy(alpha = 0.65f)))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 2 — Highlight carousel card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HighlightCard(e: AcademicCalendarEventDto, onClick: () -> Unit) {
    val c = VTheme.colors
    VCard(modifier = Modifier.width(220.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TypeDot(e.type)
            Text(CalEventType.label(e.type), style = VTheme.type.label.colored(c.ink3))
        }
        Spacer(Modifier.height(6.dp))
        Text(e.title, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(formatRange(e.startDate, e.endDate), style = VTheme.type.caption.colored(c.ink2))
        if (e.hasConflicts) {
            Spacer(Modifier.height(6.dp))
            VBadge("Potential Schedule Conflict", tone = VBadgeTone.Warning)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 3 — View Switcher
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ViewSwitcher(mode: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    val c = VTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.cream).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarViewMode.entries.forEach { m ->
            val selected = m == mode
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (selected) c.card else Color.Transparent)
                    .clickable { onSelect(m) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    m.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = VTheme.type.caption.colored(if (selected) c.tealDeep else c.ink3)
                        .copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section 4 — Interactive month calendar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InteractiveCalendar(events: List<AcademicCalendarEventDto>, onOpenEvent: (String) -> Unit) {
    val c = VTheme.colors
    // Pick the month from the first event (or "current" if events empty).
    val anchor = events.firstOrNull()?.startDate
    val parsed = anchor?.let { parseIso3(it) }
    val year = parsed?.first ?: 2026
    val month = parsed?.second ?: 6
    val days = daysIn(year, month)

    val eventsByDay = remember(events, year, month) {
        events.filter {
            val p = parseIso3(it.startDate)
            p != null && p.first == year && p.second == month
        }.groupBy { parseIso3(it.startDate)!!.third }
    }

    VCard {
        Text(
            "${MONTHS[month - 1]} $year",
            style = VTheme.type.bodyStrong.colored(c.ink),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(it, style = VTheme.type.label.colored(c.ink3))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        (1..days).toList().chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { day ->
                    val dayEvents = eventsByDay[day].orEmpty()
                    val primary = dayEvents.firstOrNull()
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (primary != null) typeColor(primary.type).copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(enabled = primary != null) { primary?.let { onOpenEvent(it.id) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.toString(), style = VTheme.type.caption.colored(c.ink))
                            if (dayEvents.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    dayEvents.take(3).forEach {
                                        Box(Modifier.size(4.dp).clip(CircleShape).background(typeColor(it.type)))
                                    }
                                }
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) {} }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun AgendaList(events: List<AcademicCalendarEventDto>, onOpenEvent: (String) -> Unit) {
    val c = VTheme.colors
    if (events.isEmpty()) {
        Text("No events to show.", style = VTheme.type.caption.colored(c.ink2))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        events.sortedBy { it.startDate }.forEach { EventRow(it, onClick = { onOpenEvent(it.id) }) }
    }
}

@Composable
private fun TimelineList(events: List<AcademicCalendarEventDto>, onOpenEvent: (String) -> Unit) {
    val c = VTheme.colors
    if (events.isEmpty()) {
        Text("Nothing upcoming.", style = VTheme.type.caption.colored(c.ink2))
        return
    }
    Column {
        events.forEach { e ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(typeColor(e.type)))
                    Box(Modifier.width(2.dp).height(40.dp).background(c.hairline))
                }
                Column(Modifier.padding(bottom = 8.dp).clickable { onOpenEvent(e.id) }) {
                    Text(e.title, style = VTheme.type.bodyStrong.colored(c.ink))
                    Text(formatRange(e.startDate, e.endDate), style = VTheme.type.caption.colored(c.ink2))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EventRow(
    e: AcademicCalendarEventDto,
    onClick: () -> Unit,
    trailingBadge: String? = null,
) {
    val c = VTheme.colors
    VCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(typeColor(e.type)))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 1)
                Text(
                    "${CalEventType.label(e.type)} • ${formatRange(e.startDate, e.endDate)}",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                if (e.hasConflicts) {
                    Spacer(Modifier.height(4.dp))
                    VBadge("Potential Schedule Conflict", tone = VBadgeTone.Warning)
                }
            }
            if (trailingBadge != null) {
                VBadge(trailingBadge, tone = if (trailingBadge == "LIVE") VBadgeTone.Success else VBadgeTone.Neutral)
            }
            Icon(VIcons.ChevronRight, null, tint = c.ink3, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MilestoneRow(e: AcademicCalendarEventDto, onClick: () -> Unit) {
    val c = VTheme.colors
    VCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(VIcons.Star, null, tint = c.warningInk, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = VTheme.type.bodyStrong.colored(c.ink))
                Text(formatRange(e.startDate, e.endDate), style = VTheme.type.caption.colored(c.ink2))
            }
        }
    }
}

@Composable
private fun KpiCard(kpi: CalendarKpiDto) {
    val c = VTheme.colors
    VCard(modifier = Modifier.width(140.dp)) {
        Text(kpi.value.toString(), style = VTheme.type.dataLg.colored(c.ink))
        Text(kpi.label, style = VTheme.type.label.colored(c.ink3))
    }
}

@Composable
private fun TypeDot(type: String) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(typeColor(type)))
}

// ─────────────────────────────────────────────────────────────────────────────
// helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun typeColor(type: String): Color {
    val c = VTheme.colors
    return when (type.uppercase()) {
        CalEventType.EXAM -> c.danger
        CalEventType.HOLIDAY -> c.success
        CalEventType.PTM -> c.teal
        CalEventType.SCHOOL_EVENT -> c.navy
        CalEventType.ACTIVITY -> c.warning
        CalEventType.ADMINISTRATIVE -> c.ink3
        CalEventType.MILESTONE -> c.tealDeep
        else -> c.ink3
    }
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private fun parseIso3(iso: String): Triple<Int, Int, Int>? {
    if (iso.length < 10) return null
    val y = iso.substring(0, 4).toIntOrNull() ?: return null
    val m = iso.substring(5, 7).toIntOrNull() ?: return null
    val d = iso.substring(8, 10).toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return Triple(y, m, d)
}

private fun daysIn(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

private fun formatRange(start: String, end: String): String {
    if (start == end || end.isBlank()) return formatShort(start)
    return "${formatShort(start)} – ${formatShort(end)}"
}

private fun formatShort(iso: String): String {
    val p = parseIso3(iso) ?: return iso
    val m = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${m[p.second - 1]} ${p.third}, ${p.first}"
}
