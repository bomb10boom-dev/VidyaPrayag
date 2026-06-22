package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardAlertDto
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardChildSummary
import com.littlebridge.enrollplus.feature.parent.presentation.AttendanceDayState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentDashboardState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentDashboardViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VEmptyState
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.VErrorState
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.presentation.PermissionViewModel
import com.littlebridge.enrollplus.ui.v2.components.*
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.util.nowMinutesOfDay
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * ParentHomeScreenV2 — the rebuilt parent dashboard, a premium Compose adaptation of the
 * website reference (`PhoneMockup.tsx`). The website is a React/Tailwind marketing mockup; this
 * is a native KMP app, so the design language is *adapted*, not copied verbatim — same lavender
 * canvas, navy ink, violet accent, the aurora wash and the layered card stack, rendered with
 * native gestures, springs and Canvas craft.
 *
 * DESIGN LAW — NEVER COLLAPSE TO WHITE SPACE. Every card renders a rich, intentional, premium
 * state even when the backend returns sparse/empty data (the demo child has empty marks /
 * timetable). The screen is always full and composed: an aurora-washed hero with the child
 * identity + a live journey ring, a school-day timeline rail, then the live feature cards. No
 * card "returns" early; sparse data becomes a polished, on-brand empty state.
 *
 * NO FLOATING TOASTS (LAW): the reference mockup decorates itself with floating glass plates
 * ("Marked present", "Maths result published"). Those are presentation flourishes and are
 * intentionally surfaced natively inside the cards / the bell — never as transient overlays.
 */
@Composable
fun ParentHomeScreenV2(
    modifier: Modifier = Modifier,
    onDiscoverSchools: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenFees: () -> Unit = {},
    onOpenAcademics: () -> Unit = {},
    viewModel: ParentDashboardViewModel = koinViewModel(),
    permissionVm: PermissionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val showRationale by permissionVm.showNotificationRationale.collectAsStateV2()

    // Live clock — re-derive the time-aware greeting + period/end-of-day fields each minute.
    LaunchedEffect(Unit) {
        permissionVm.checkNotificationPermission()
        while (true) {
            delay(60_000L)
            viewModel.refreshLiveClock()
        }
    }

    ParentDashboardContent(
        state = state,
        onRetry = viewModel::load,
        onOpenFees = onOpenFees,
        onOpenAcademics = onOpenAcademics,
        modifier = modifier,
    )

    VConfirmDialog(
        visible = showRationale,
        title = "Stay Informed",
        message = "Enable notifications to receive important updates about school events, attendance, and fee reminders.",
        confirmLabel = "Enable",
        onConfirm = permissionVm::requestNotificationPermission,
        onDismiss = permissionVm::declineNotifications,
        cancelLabel = "Not Now",
        icon = VIcons.Bell,
    )
}

/** Stateless body. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ParentDashboardContent(
    state: ParentDashboardState,
    onRetry: () -> Unit,
    onOpenFees: () -> Unit,
    onOpenAcademics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val d = VTheme.dimens

    var coveredDetailOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = coveredDetailOpen) { coveredDetailOpen = false }

    Box(
        modifier
            .fillMaxSize()
            // The canvas IS the website background token (#FCF8FF) — a clean near-white with the
            // faintest lavender tint. Lavender is the brand accent, not a wall-to-wall fill, so the
            // wash is a barely-there whisper (≤4%) top-left, exactly like the reference mockup.
            .background(c.background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.accent.copy(alpha = 0.04f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.02f),
                        radius = size.width * 0.9f,
                    ),
                )
            },
    ) {
        // CRITICAL LAYOUT FIX (root cause of the "cards crammed at the top, ~70% empty space" bug):
        // the dashboard body used to live inside VStateHost, whose loading leg drives an
        // AnimatedContent. AnimatedContent lays out Box-like (it STACKS its children so it can
        // crossfade them), and nested inside a verticalScroll (an UNBOUNDED-height parent) the
        // settled Content child collapsed — all ~8 sibling cards painted on top of one another at
        // y=0, so only the last few (Fees / Results / Covered) were visible and the rest of the
        // screen was dead space.
        //
        // The state legs are now resolved OUTSIDE the scroll, each in its own bounded, centered Box,
        // and the card stack is a plain verticalScroll Column with NO AnimatedContent anywhere in
        // its parentage. The cards therefore always lay out top-to-bottom with the intended rhythm.
        when {
            // First load (no child resolved yet) → a centered brand-violet spinner (never the
            // teal VLoadingState, never fillMaxSize inside a scroll).
            state.isLoading && state.selectedChild == null ->
                DashboardCenterState {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = c.accent,
                        modifier = Modifier.size(36.dp),
                    )
                }

            // Hard error before anything could load → centered retryable error.
            state.error != null && state.selectedChild == null ->
                DashboardCenterState {
                    VErrorState(
                        message = state.error ?: "",
                        onRetry = onRetry,
                    )
                }

            // No child linked → centered empty state.
            state.children.isEmpty() ->
                DashboardCenterState {
                    VEmptyState(
                        icon = VIcons.User,
                        title = "No child linked yet",
                        body = "Link your child to see their daily journey and progress.",
                    )
                }

            // Content — the live dashboard. A single scrolling Column, cards top-to-bottom.
            // NOTE: the shared ParentHeader (identity chip + child switcher + icon cluster) is
            // rendered by ParentPortalV2 ABOVE this content on every tab — Home no longer draws a
            // bespoke pseudo-header. The greeting + journey live in a proper white content card.
            else -> {
                val child = state.selectedChild
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ── Greeting + live journey — a premium WHITE content card ───────
                    TodayCard(
                        child = child,
                        className = state.timetable?.className.orEmpty(),
                        todayState = state.today.state,
                        statusLabel = state.today.label,
                        contextLine = contextLineFor(state),
                    )

                    // ── Alert strip (real /dashboard alerts — populated for the demo) ─
                    if (state.alerts.isNotEmpty()) {
                        AlertStrip(alerts = state.alerts)
                    }

                    // ── Attendance card (primary feature) ────────────────────────────
                    ParentAttendanceCard(
                        today = state.today,
                        attendance = state.attendance,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // ── Current class card (live, swipe-to-expand) ───────────────────
                    // Swipe left to grow from the compact "current class" verdict → the full
                    // today timeline (every subject marked done/now/pending live) → the weekly
                    // timetable; swipe right to collapse back. Mirrors the attendance card.
                    ParentScheduleCard(
                        todayPeriods = state.todayPeriods,
                        timetable = state.timetable,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // ── Covered today card (live + end-of-day summary) ───────────────
                    ParentCoveredCard(
                        coveredToday = state.coveredToday,
                        schoolDayEnded = state.schoolDayEnded,
                        onOpenDetail = { coveredDetailOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // ── Academics: latest published result + sparkline ───────────────
                    ParentResultsCard(
                        latestMark = state.latestMark,
                        previousMark = state.previousMarkForSubject,
                        trend = state.markTrend,
                        onOpenAcademics = onOpenAcademics,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // ── Fees ─────────────────────────────────────────────────────────
                    ParentFeesCard(
                        fees = state.fees,
                        onOpenFees = onOpenFees,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(2.dp))
                }
            }
        }

        ParentCoveredDetailOverlay(
            visible = coveredDetailOpen,
            coveredToday = state.coveredToday,
            syllabus = state.syllabus,
            schoolDayEnded = state.schoolDayEnded,
            onDismiss = { coveredDetailOpen = false },
        )
    }
}

/**
 * A bounded, centered host for the dashboard's loading / error / empty legs. Unlike VStateHost's
 * loading leg (which uses fillMaxSize inside a scroll, plus a teal spinner) this lives OUTSIDE the
 * scroll, fills the available canvas exactly once, and centers its content on the lavender wash.
 */
@Composable
private fun DashboardCenterState(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * TodayCard — the dashboard's flagship greeting card, adapting the reference's identity block to a
 * premium WHITE card on the lavender canvas (NOT a purple pseudo-header — the shared ParentHeader
 * already owns identity/switching above this).
 *
 * It carries the child's avatar (with a semantic status pip), name + class, a truthful status chip
 * (present=green, late=amber, absent=red — color is meaning), a live **journey ring** built from
 * real `overall_progress`, and a time-aware context line. Always renders rich content for a linked
 * child, so the dashboard never opens on emptiness.
 */
@Composable
private fun TodayCard(
    child: DashboardChildSummary?,
    className: String,
    todayState: AttendanceDayState,
    statusLabel: String,
    contextLine: String,
) {
    val c = VTheme.colors
    val name = child?.name?.ifBlank { "Your child" } ?: "Your child"
    val level = child?.currentLevel ?: 0
    val rawProgress = child?.overallProgress ?: 0.0
    val progressPct = (if (rawProgress <= 1.0) rawProgress * 100.0 else rawProgress).roundToInt().coerceIn(0, 100)
    val status = statusVisualFor(todayState)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // Avatar with a semantic status pip (green check when present, etc.).
                Box {
                    VAvatar(name = name, src = child?.profilePic, size = 54.dp, ring = true)
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(c.card)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(status.color),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(status.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(9.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        name,
                        style = VTheme.type.h2.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold, fontSize = 19.sp),
                        maxLines = 1,
                    )
                    val sub = listOfNotNull(
                        "Level $level".takeIf { level > 0 },
                        className.takeIf { it.isNotBlank() },
                    ).joinToString("  ·  ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 12.sp))
                    }
                }
                // Status chip — color carries meaning (semantic), not always violet.
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(status.softBg)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        statusLabel.ifBlank { status.word },
                        style = VTheme.type.label.colored(status.ink).copy(fontWeight = FontWeight.Bold, fontSize = 9.5.sp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Journey row: a real violet progress ring (brand accent moment) + a contextual line.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                JourneyRing(percent = progressPct, modifier = Modifier.size(68.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "LEARNING JOURNEY",
                        style = VTheme.type.label.colored(c.accentDeep).copy(
                            fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 0.9.sp,
                        ),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "$progressPct% of the way to the next level",
                        style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    if (contextLine.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            contextLine,
                            style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp),
                        )
                    }
                }
            }
        }
    }
}

/** Violet progress ring (the brand-accent moment) with the percent centred, on a white card. */
@Composable
private fun JourneyRing(percent: Int, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    val sweep by animateFloatAsState(targetValue = percent / 100f, label = "journeySweep")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = c.accent.copy(alpha = 0.16f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(c.accentSoft, c.accent, c.accentDeep)),
                startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "$percent%",
            style = VTheme.type.dataLg.colored(c.accentDeep).copy(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp),
        )
    }
}

/** A compact strip of the real dashboard alerts (e.g. "Upcoming PTM · Nov 25"). */
@Composable
private fun AlertStrip(alerts: List<DashboardAlertDto>) {
    val c = VTheme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(alerts, key = { it.id }) { a ->
            val (bg, fg, icon) = when (a.type.uppercase()) {
                "CRITICAL" -> Triple(c.danger.copy(alpha = 0.5f), c.dangerInk, VIcons.AlertCircle)
                "WARNING" -> Triple(c.warning.copy(alpha = 0.55f), c.warningInk, VIcons.AlertTriangle)
                else -> Triple(c.accent.copy(alpha = 0.12f), c.accentDeep, VIcons.Bell)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.card)
                    .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
                    .padding(start = 8.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
                }
                Column {
                    Text(a.title, style = VTheme.type.label.colored(c.navyDeep).copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp))
                    if (a.value.isNotBlank()) {
                        Text(a.value, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp))
                    }
                }
            }
        }
    }
}

/**
 * A semantic visual for the resolved today-state. COLOR CARRIES MEANING (design law): present is
 * green, late is amber, absent is red, holidays/breaks are calm navy, awaiting is the brand violet.
 */
private data class StatusVisual(
    val word: String,
    val color: Color,
    val ink: Color,
    val softBg: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun statusVisualFor(state: AttendanceDayState): StatusVisual {
    val c = VTheme.colors
    return when (state) {
        AttendanceDayState.Present -> StatusVisual(
            "Present", c.successInk, c.successInk, c.success.copy(alpha = 0.30f), VIcons.Check,
        )
        AttendanceDayState.Late -> StatusVisual(
            "Late", c.warningInk, c.warningInk, c.warning.copy(alpha = 0.45f), VIcons.Clock,
        )
        AttendanceDayState.Absent -> StatusVisual(
            "Absent", c.dangerInk, c.dangerInk, c.danger.copy(alpha = 0.45f), VIcons.Close,
        )
        AttendanceDayState.Holiday -> StatusVisual(
            "Holiday", c.navy, c.navy, c.navy.copy(alpha = 0.10f), VIcons.Calendar,
        )
        AttendanceDayState.Vacation -> StatusVisual(
            "Break", c.navy, c.navy, c.navy.copy(alpha = 0.10f), VIcons.Sparkles,
        )
        AttendanceDayState.Sunday -> StatusVisual(
            "Sunday", c.ink3, c.ink2, c.cream, VIcons.Calendar,
        )
        AttendanceDayState.NoData -> StatusVisual(
            "Awaiting", c.accent, c.accentDeep, c.accent.copy(alpha = 0.12f), VIcons.Clock,
        )
    }
}

/** Build the time-aware contextual line referencing the selected child. */
private fun contextLineFor(state: ParentDashboardState): String {
    val name = state.selectedChild?.name?.takeIf { it.isNotBlank() } ?: "your child"
    val firstName = name.substringBefore(' ')
    val partOfDay = when (nowMinutesOfDay() / 60) {
        in 0..11 -> "morning"
        in 12..16 -> "afternoon"
        else -> "evening"
    }
    val tail = when (state.today.state) {
        AttendanceDayState.Present -> "$firstName is marked present today."
        AttendanceDayState.Late -> "$firstName arrived late today."
        AttendanceDayState.Absent -> "$firstName is marked absent today."
        AttendanceDayState.Holiday -> "It's a holiday — ${state.today.label}."
        AttendanceDayState.Vacation -> "${state.today.label} — enjoy the break."
        AttendanceDayState.Sunday -> "It's Sunday — no school today."
        AttendanceDayState.NoData -> "Here's $firstName's day at a glance."
    }
    return "Good $partOfDay. $tail"
}
