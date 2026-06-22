package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentTimetableData
import com.littlebridge.enrollplus.feature.parent.presentation.LivePeriod
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.util.formatClock12h
import com.littlebridge.enrollplus.util.parseHourMinute
import com.littlebridge.enrollplus.util.todayWeekday

/**
 * Today's schedule card — the child's live class timetable for today, with a contained
 * swipe that grows the same card through THREE faces (and back), mirroring the attendance
 * card's swipe-to-expand interaction the brief calls for:
 *
 *   face 0 — CURRENT CLASS (compact): the live "happening now / next up" verdict + a
 *            day-progress bar (N of M classes done). The default resting face.
 *   face 1 — TODAY TIMELINE (expanded): EVERY subject for today on a connected rail,
 *            each marked LIVE against the wall clock — done (green tick), now (pulsing
 *            violet), and yet-to-come (dimmed) — exactly as the timetable + current time
 *            dictate. Swiping LEFT from face 0 expands the card to this.
 *   face 2 — WEEKLY TIMETABLE: the full week. Swiping LEFT again reaches it.
 *
 * Live: each period carries a [LivePeriod.relation] (-1 finished, 0 now, 1 upcoming),
 * re-derived from the device clock by the ViewModel's per-minute refresh, so the marking
 * (done / now / pending) updates through the school day on its own.
 *
 * LAW: the swipe is component-scoped (local [face] state), never page nav; every period
 * is real (from the /timetable endpoint reading the canonical TeacherPeriodsTable).
 */
@Composable
fun ParentScheduleCard(
    todayPeriods: List<LivePeriod>,
    timetable: ParentTimetableData?,
    modifier: Modifier = Modifier,
) {
    val hasToday = todayPeriods.isNotEmpty()
    val hasWeek = timetable != null && timetable.weekdays.any { it.periods.isNotEmpty() }
    // 0 = current class (compact), 1 = today timeline (expanded), 2 = weekly timetable.
    var face by remember { mutableStateOf(0) }
    // The highest face the data can reach — clamp swipes so we never land on an empty face.
    val maxFace = when {
        hasToday && hasWeek -> 2
        hasToday || hasWeek -> 1
        else -> 0
    }

    val swipe = if (maxFace > 0) {
        Modifier.pointerInput(maxFace) {
            var dx = 0f
            detectHorizontalDragGestures(
                onDragStart = { dx = 0f },
                onDragEnd = {
                    if (dx <= -36f) face = (face + 1).coerceAtMost(maxFace)   // swipe left → expand
                    else if (dx >= 36f) face = (face - 1).coerceAtLeast(0)    // swipe right → collapse
                },
            ) { _, delta -> dx += delta }
        }
    } else Modifier

    VCard(modifier = modifier.then(swipe), padding = 14.dp) {
        AnimatedContent(
            targetState = face,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it / 3 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(160)))
                }
            },
            label = "scheduleFace",
        ) { f ->
            when (f) {
                0 -> CurrentClassFace(
                    periods = todayPeriods,
                    // From the compact face, the next swipe target is the today timeline if there
                    // are periods, else the weekly view if there's a week.
                    onExpand = when {
                        hasToday -> ({ face = 1 })
                        hasWeek -> ({ face = 1 })
                        else -> null
                    },
                    expandLabel = if (hasToday) "Swipe to see today's full schedule" else "Swipe for the weekly timetable",
                )
                1 -> if (hasToday) {
                    TodayTimelineFace(
                        periods = todayPeriods,
                        onCollapse = { face = 0 },
                        onExpand = if (hasWeek) ({ face = 2 }) else null,
                    )
                } else {
                    WeeklyTimetableFace(timetable = timetable, onCollapse = { face = 0 })
                }
                else -> WeeklyTimetableFace(
                    timetable = timetable,
                    onCollapse = { face = if (hasToday) 1 else 0 },
                )
            }
        }
    }
}

/**
 * Face 0 — CURRENT CLASS (compact). The resting face: a single live verdict ("In class now"
 * / "Up next" / "Day complete") + a day-progress bar so a parent gets the at-a-glance answer
 * without expanding. A swipe-left hint invites the full timeline.
 */
@Composable
private fun CurrentClassFace(
    periods: List<LivePeriod>,
    onExpand: (() -> Unit)?,
    expandLabel: String,
) {
    val c = VTheme.colors
    val total = periods.size
    val done = periods.count { it.relation == -1 }
    val nowPeriod = periods.firstOrNull { it.relation == 0 }
    val nextPeriod = periods.firstOrNull { it.relation == 1 }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(VIcons.Clock, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(14.dp))
                Text(
                    "CURRENT CLASS",
                    style = VTheme.type.label.colored(c.ink3).copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                )
            }
            if (onExpand != null) {
                val ix = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.1f))
                        .clickable(interactionSource = ix, indication = null) { onExpand() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.ChevronRight, contentDescription = "Expand schedule", tint = c.accentDeep, modifier = Modifier.size(15.dp))
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (periods.isEmpty()) {
            ScheduleEmptyPlate()
            if (onExpand != null) {
                Spacer(Modifier.height(8.dp))
                SwipeHint(expandLabel)
            }
            return
        }

        // ── The live verdict hero — what's happening for the child right now. ──────
        val (icon, tint, heroBg, headline, subline) = when {
            nowPeriod != null -> Quint(
                VIcons.BookOpen, c.accentDeep, c.accent.copy(alpha = 0.10f),
                "In class now",
                buildString {
                    append(nowPeriod.subject)
                    val end = formatClockSafe(nowPeriod.endTime)
                    append("  ·  until $end")
                },
            )
            nextPeriod != null -> Quint(
                VIcons.Clock, c.warningInk, c.warning.copy(alpha = 0.18f),
                "Up next",
                buildString {
                    append(nextPeriod.subject)
                    val start = formatClockSafe(nextPeriod.startTime)
                    append("  ·  at $start")
                },
            )
            else -> Quint(
                VIcons.ShieldCheck, c.successInk, c.success.copy(alpha = 0.20f),
                "Day complete",
                "All of today's classes are done",
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(heroBg)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    headline,
                    style = VTheme.type.h3.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp),
                )
                Text(subline, style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 11.sp), maxLines = 1)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Day-progress bar — how much of the school day is behind us. ────────────
        DayProgress(done = done, total = total)

        if (onExpand != null) {
            Spacer(Modifier.height(10.dp))
            SwipeHint(expandLabel)
        }
    }
}

/** A small immutable 5-tuple so the verdict can be destructured cleanly. */
private data class Quint(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val heroBg: Color,
    val headline: String,
    val subline: String,
)

/** A labelled day-progress bar: "N of M classes done" with an animated violet fill. */
@Composable
private fun DayProgress(done: Int, total: Int) {
    val c = VTheme.colors
    val ratio = if (total > 0) done.toFloat() / total else 0f
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = ratio.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "dayProgress",
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "Today's progress",
                style = VTheme.type.label.colored(c.ink3).copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
            )
            Text(
                "$done of $total classes done",
                style = VTheme.type.caption.colored(c.navyDeep).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(999.dp)).background(c.cream),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(c.accentSoft, c.accent, c.accentDeep),
                        ),
                    ),
            )
        }
    }
}

/**
 * Face 1 — TODAY TIMELINE (expanded). EVERY subject for today on one connected rail, each
 * row marked LIVE against the wall clock: completed periods carry a filled green tick, the
 * current period a pulsing violet node + "Now" chip, and upcoming periods a hollow dimmed
 * node — "completed and yet to be completed as per the timetable and current time", exactly
 * as the brief asks. Header shows the live N-of-M progress; a back chevron collapses.
 */
@Composable
private fun TodayTimelineFace(
    periods: List<LivePeriod>,
    onCollapse: () -> Unit,
    onExpand: (() -> Unit)?,
) {
    val c = VTheme.colors
    val total = periods.size
    val done = periods.count { it.relation == -1 }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ix = remember { MutableInteractionSource() }
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.10f))
                        .clickable(interactionSource = ix, indication = null) { onCollapse() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.ChevronLeft, contentDescription = "Back", tint = c.accentDeep, modifier = Modifier.size(15.dp))
                }
                Text("Today's schedule", style = VTheme.type.bodyStrong.colored(c.navyDeep))
            }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    "$done / $total done",
                    style = VTheme.type.label.colored(c.accentDeep).copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // The connected timeline rail — each row a live-marked node + the period detail.
        periods.forEachIndexed { index, p ->
            TimelineRow(period = p, isFirst = index == 0, isLast = index == periods.lastIndex)
        }

        if (onExpand != null) {
            Spacer(Modifier.height(8.dp))
            SwipeHint("Swipe for the weekly timetable")
        }
    }
}

/**
 * A single connected timeline row: a status node (with the rail line through it) on the left,
 * the time + subject + meta on the right, all styled by the period's live relation to the clock.
 */
@Composable
private fun TimelineRow(period: LivePeriod, isFirst: Boolean, isLast: Boolean) {
    val c = VTheme.colors
    val isNow = period.relation == 0
    val isDone = period.relation == -1

    val nodeColor = when {
        isDone -> c.successInk
        isNow -> c.accent
        else -> c.placeholder
    }
    val railColor = c.hairline
    val subjectColor = if (isDone) c.ink3 else c.navyDeep
    val timeColor = when {
        isNow -> c.accentDeep
        isDone -> c.placeholder
        else -> c.ink2
    }

    // Live pulse on the "now" node.
    val pulse = rememberInfiniteTransition(label = "nowPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        // ── Rail + node column ────────────────────────────────────────────────
        Column(
            Modifier.width(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // top rail segment
            Box(
                Modifier.width(2.dp).height(8.dp)
                    .background(if (isFirst) Color.Transparent else railColor),
            )
            // node
            Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                if (isNow) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale; alpha = 0.25f }
                            .clip(CircleShape)
                            .background(c.accent),
                    )
                }
                Box(
                    Modifier.size(if (isNow) 18.dp else 16.dp).clip(CircleShape).background(nodeColor),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        isDone -> Icon(VIcons.Check, contentDescription = "Completed", tint = Color.White, modifier = Modifier.size(11.dp))
                        isNow -> Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        else -> {}
                    }
                }
            }
            // bottom rail segment — green up to & including done, fading after
            Box(
                Modifier.width(2.dp).height(if (isLast) 0.dp else 30.dp)
                    .background(if (isLast) Color.Transparent else railColor),
            )
        }

        Spacer(Modifier.width(10.dp))

        // ── Period detail ─────────────────────────────────────────────────────
        Column(
            Modifier
                .weight(1f)
                .padding(top = 6.dp, bottom = 8.dp)
                .then(
                    if (isNow) Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accent.copy(alpha = 0.07f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                    else Modifier,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${formatClockSafe(period.startTime)} – ${formatClockSafe(period.endTime)}",
                    style = VTheme.type.caption.colored(timeColor).copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                )
                StatusTag(isDone = isDone, isNow = isNow)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                period.subject,
                style = VTheme.type.bodyStrong.colored(subjectColor).copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
            )
            val meta = listOfNotNull(
                period.teacherName.takeIf { it.isNotBlank() },
                period.room.takeIf { it.isNotBlank() }?.let { "Room $it" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp))
            }
        }
    }
}

/** A tiny status tag — Done (green) / Now (violet) / Upcoming (muted). COLOR IS SEMANTIC. */
@Composable
private fun StatusTag(isDone: Boolean, isNow: Boolean) {
    val c = VTheme.colors
    val (label, bg, fg) = when {
        isDone -> Triple("Done", c.success.copy(alpha = 0.35f), c.successInk)
        isNow -> Triple("Now", c.accent.copy(alpha = 0.18f), c.accentDeep)
        else -> Triple("Upcoming", c.cream, c.ink3)
    }
    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(bg).padding(horizontal = 7.dp, vertical = 1.dp)) {
        Text(label, style = VTheme.type.label.colored(fg).copy(fontWeight = FontWeight.Bold, fontSize = 8.5.sp))
    }
}

/** The premium "no classes scheduled" plate, shared across faces. */
@Composable
private fun ScheduleEmptyPlate() {
    val c = VTheme.colors
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
            Icon(VIcons.Calendar, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                "No classes scheduled",
                style = VTheme.type.h3.colored(c.navyDeep).copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            )
            Text(
                "The day's timetable will appear here once it's published",
                style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp),
            )
        }
    }
}

/** The consistent swipe-affordance hint line. */
@Composable
private fun SwipeHint(text: String) {
    val c = VTheme.colors
    Text(
        text,
        style = VTheme.type.label.colored(c.ink3).copy(fontSize = 9.sp, letterSpacing = 0.4.sp),
    )
}

@Composable
private fun WeeklyTimetableFace(timetable: ParentTimetableData?, onCollapse: () -> Unit) {
    val c = VTheme.colors
    val today = todayWeekday()
    val weekdays = timetable?.weekdays.orEmpty().sortedBy { it.weekday }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val ix = remember { MutableInteractionSource() }
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.1f))
                    .clickable(interactionSource = ix, indication = null) { onCollapse() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.ChevronLeft, contentDescription = "Back", tint = c.accentDeep, modifier = Modifier.size(15.dp))
            }
            Text("Weekly timetable", style = VTheme.type.bodyStrong.colored(c.navyDeep))
            Spacer(Modifier.width(30.dp))
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            weekdays.forEach { day ->
                val isToday = day.weekday == today
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            weekdayName(day.weekday),
                            style = VTheme.type.labelStrong.colored(if (isToday) c.accentDeep else c.ink2).copy(fontSize = 11.sp),
                        )
                        if (isToday) {
                            Box(
                                Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent.copy(alpha = 0.16f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text("Today", style = VTheme.type.label.colored(c.accentDeep).copy(fontWeight = FontWeight.Bold, fontSize = 8.5.sp))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (day.periods.isEmpty()) {
                        Text("No classes", style = VTheme.type.caption.colored(c.placeholder).copy(fontSize = 11.sp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            day.periods.sortedBy { parseHourMinute(it.startTime) ?: 0 }.forEach { p ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        formatClockSafe(p.startTime),
                                        style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp),
                                        modifier = Modifier.width(56.dp),
                                    )
                                    Text(
                                        p.subject,
                                        style = VTheme.type.body.colored(c.navyDeep).copy(fontSize = 12.sp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Format an "HH:mm" wire time to a friendly 12h clock, falling back to the raw string. */
private fun formatClockSafe(hm: String): String =
    parseHourMinute(hm)?.let { formatClock12h(it) } ?: hm

private fun weekdayName(weekday: Int): String = when (weekday) {
    1 -> "Monday"; 2 -> "Tuesday"; 3 -> "Wednesday"; 4 -> "Thursday"
    5 -> "Friday"; 6 -> "Saturday"; 7 -> "Sunday"; else -> "—"
}
