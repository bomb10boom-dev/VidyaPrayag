/*
 * File: SchoolHomeScreenV2.kt
 * Module: ui.v2.screens.school
 *
 * The REDESIGNED School Admin command center — a modern, analytics-driven home
 * that replaces the legacy ERP layout. Every widget is driven by real data from
 * GET /api/admin/dashboard/overview (SchoolDashboardViewModel.overview); nothing
 * is hardcoded and modules without backing data hide gracefully.
 *
 * Layout (top → bottom):
 *   1. Personalized header (greeting, school, session, last updated, quick actions)
 *   2. Smart Insights carousel (horizontal, actionable, data-driven)
 *   3. School Pulse (flagship animated gauge 0..100 + category breakdown)
 *   4. KPI cards grid (with trend deltas)
 *   5. Campus Health analytics (attendance trend chart)
 *   6. Fee Collection analytics (bars + summary)
 *   7. Parent Engagement Center (leaderboard + gamification)
 *   8. Communication Center (actionable cards)
 *   9. Event Dashboard (upcoming countdown + recently completed)
 *  10. Teacher Spotlight (top performer)
 *  11. Student Achievement showcase (carousel)
 *  12. Birthday & Celebration widget
 *  13. Live Activity feed (timeline)
 *  14. Analytics / Risk-monitor entry cards
 *
 * Design language: modern SaaS — rounded 16–24dp, gradient accents, smooth
 * animations, skeleton loading, dark-mode aware (all colors via VTheme tokens).
 */
package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardActivity
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardAnalytics
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardOverview
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewAchievement
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewBirthday
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewEvent
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewInsight
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewKpi
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewLeaderClass
import com.littlebridge.enrollplus.feature.admin.domain.model.OverviewSchoolPulse
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicCalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventStatus
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventType
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarDashboardDto
import com.littlebridge.enrollplus.feature.admin.presentation.AcademicCalendarPlatformViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolDashboardViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.NotificationsViewModel
import com.littlebridge.enrollplus.presentation.PermissionViewModel
import com.littlebridge.enrollplus.ui.v2.components.*
import com.littlebridge.enrollplus.ui.v2.screens.SkeletonDashboard
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VElevationLevel
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.vElevation
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.min

// =====================================================================
// Screen entry
// =====================================================================

@Composable
fun SchoolHomeScreenV2(
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenPews: () -> Unit = {},
    onExit: () -> Unit = {},
    viewModel: SchoolDashboardViewModel = koinViewModel(),
    notificationsViewModel: NotificationsViewModel = koinViewModel(),
    calendarViewModel: AcademicCalendarPlatformViewModel = koinViewModel(),
    permissionVm: PermissionViewModel = koinViewModel(),
) {
    val adminName by viewModel.adminName.collectAsStateV2()
    val loading by viewModel.isLoading.collectAsStateV2()
    val error by viewModel.errorMessage.collectAsStateV2()
    val notifications by notificationsViewModel.state.collectAsStateV2()
    val overview by viewModel.overview.collectAsStateV2()
    val analytics by viewModel.analytics.collectAsStateV2()
    val activity by viewModel.activity.collectAsStateV2()
    val calendarState by calendarViewModel.state.collectAsStateV2()

    val showRationale by permissionVm.showNotificationRationale.collectAsStateV2()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        permissionVm.checkNotificationPermission()
    }

    SchoolDashboardContent(
        modifier = modifier,
        adminName = adminName,
        unreadCount = notifications.unreadCount,
        loading = loading,
        error = error,
        overview = overview,
        analytics = analytics,
        activity = activity,
        calendarDashboard = calendarState.dashboard,
        onRetry = {
            viewModel.refresh()
            calendarViewModel.refresh()
        },
        onOpenNotifications = onOpenNotifications,
        onOpenCalendar = onOpenCalendar,
        onOpenAnalytics = onOpenAnalytics,
        onOpenPews = onOpenPews,
        onExit = onExit,
    )
    VConfirmDialog(
        visible = showRationale,
        title = "Stay Informed",
        message = "Enable notifications to receive important updates about school events, attendance, and institutional alerts.",
        confirmLabel = "Enable",
        onConfirm = permissionVm::requestNotificationPermission,
        onDismiss = permissionVm::declineNotifications,
        cancelLabel = "Not Now",
        icon = VIcons.Bell,
    )

}

@Composable
private fun SchoolDashboardContent(
    modifier: Modifier = Modifier,
    adminName: String,
    unreadCount: Int,
    loading: Boolean,
    error: String?,
    overview: AdminDashboardOverview?,
    analytics: AdminDashboardAnalytics?,
    activity: AdminDashboardActivity?,
    calendarDashboard: CalendarDashboardDto?,
    onRetry: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenPews: () -> Unit,
    onExit: () -> Unit,
) {
    // Pull-to-refresh: swipe down anywhere on the dashboard to re-sync the
    // overview / KPIs / insights / cards. The indicator only engages once the
    // initial load has resolved (overview != null); during the very first load
    // the skeleton owns the screen, so we never show a refresh spinner on top
    // of a skeleton. The gesture wraps the scrollable body and does NOT block
    // vertical scrolling (PullToRefreshBox only intercepts the overscroll-at-top
    // gesture). See [VPullRefresh].
    VPullRefresh(
        isRefreshing = loading && overview != null,
        onRefresh = onRetry,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .padding(bottom = 130.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
        VStateHost(
            loading = loading && overview == null,
            error = if (overview == null) error else null,
            isEmpty = false,
            onRetry = onRetry,
            skeleton = { SkeletonDashboard() },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {

                // 1. Header
                DashboardHeader(
                    overview = overview,
                    fallbackName = adminName,
                    unreadCount = unreadCount,
                    onNotifications = onOpenNotifications,
                    onAvatar = onExit,
                )

                // Quick actions row
                QuickActionsRow(
                    onAnnouncement = onOpenNotifications,
                    onEvent = onOpenCalendar,
                    onNotice = onOpenNotifications,
                    onReports = onOpenAnalytics,
                )

                // 1b. Academic Calendar integration — Quick Insights + Upcoming
                //     Events carousel, sourced from GET /api/admin/calendar/dashboard.
                calendarDashboard?.let { cal ->
                    CalendarQuickInsights(dashboard = cal, onOpenCalendar = onOpenCalendar)
                    CalendarUpcomingCarousel(dashboard = cal, onOpenCalendar = onOpenCalendar)
                }

                // 2. Smart insights carousel
                val insights = overview?.insights.orEmpty()
                if (insights.isNotEmpty()) {
                    SmartInsightsCarousel(insights = insights)
                }

                // 3. School Pulse (flagship)
                overview?.schoolPulse?.let { pulse ->
                    SchoolPulseCard(pulse = pulse)
                }

                // 4. KPI grid
                val kpis = overview?.kpis.orEmpty().filter { it.available }
                if (kpis.isNotEmpty()) {
                    KpiGrid(kpis = kpis, onClick = onOpenAnalytics)
                }

                // 5. Campus health analytics (attendance trend)
                CampusHealthCard(
                    analytics = analytics,
                    onClick = onOpenAnalytics,
                )

                // 6. Fee analytics
                overview?.feeAnalytics?.takeIf { it.available }?.let { fee ->
                    FeeAnalyticsCard(fee = fee, onClick = onOpenAnalytics)
                }

                // 7. Parent engagement center
                overview?.parentEngagement?.takeIf { it.available }?.let { pe ->
                    ParentEngagementCard(engagement = pe)
                }

                // 8. Communication center
                overview?.communication?.let { comm ->
                    CommunicationCenterCard(
                        unread = comm.unreadMessages,
                        pending = comm.pendingQueries,
                        announcements = comm.announcements,
                        acks = comm.noticeAcknowledgements,
                        onOpenComms = onOpenNotifications,
                    )
                }

                // 9. Event dashboard
                overview?.events?.takeIf { it.available }?.let { ev ->
                    EventDashboardCard(
                        upcoming = ev.upcoming,
                        completed = ev.recentlyCompleted,
                        onOpenCalendar = onOpenCalendar,
                    )
                }

                // 10. Teacher spotlight
                overview?.teacherSpotlight?.takeIf { it.available }?.let { spot ->
                    TeacherSpotlightCard(
                        name = spot.name,
                        department = spot.department,
                        avatarUrl = spot.avatarUrl,
                        score = spot.score,
                        highlight = spot.highlight,
                        onClick = onOpenPews,
                    )
                }

                // 11. Student achievement showcase
                overview?.achievements?.takeIf { it.available && it.items.isNotEmpty() }?.let { ach ->
                    AchievementShowcase(items = ach.items)
                }

                // 12. Birthdays
                overview?.birthdays?.takeIf { it.available }?.let { b ->
                    BirthdayWidget(today = b.today, upcoming = b.upcoming)
                }

                // 13. Live activity feed
                val activities = activity?.activities.orEmpty()
                if (activities.isNotEmpty()) {
                    ActivityFeedCard(
                        activities = activities.map {
                            ActivityItem(it.title, it.description, it.time)
                        },
                        onClick = onOpenNotifications,
                    )
                }

                // 14. Entry cards
                AnalyticsEntryCard(onClick = onOpenAnalytics)
                PewsEntryCard(onClick = onOpenPews)
            }
        }
        }
    }


}

// =====================================================================
// 1. Header
// =====================================================================

@Composable
private fun DashboardHeader(
    overview: AdminDashboardOverview?,
    fallbackName: String,
    unreadCount: Int,
    onNotifications: () -> Unit,
    onAvatar: () -> Unit,
) {
    val c = VTheme.colors
    val header = overview?.header
    val name = header?.adminName?.takeIf { it.isNotBlank() } ?: fallbackName
    val greeting = header?.greeting?.takeIf { it.isNotBlank() } ?: "Welcome"
    val schoolName = header?.schoolName?.takeIf { it.isNotBlank() } ?: "Your School"
    val session = buildString {
        header?.academicYear?.takeIf { it.isNotBlank() }?.let { append(it) }
        header?.currentTerm?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$greeting, $name 👋",
                    style = VTheme.type.h2.colored(c.ink),
                )
                Text(
                    text = schoolName,
                    style = VTheme.type.bodyStrong.colored(c.ink2),
                )
                if (session.isNotBlank()) {
                    Text(
                        text = session,
                        style = VTheme.type.caption.colored(c.ink3),
                    )
                }
            }

            NotificationButton(count = unreadCount, onClick = onNotifications)

            Box(
                modifier = Modifier.clip(CircleShape).clickable { onAvatar() },
            ) {
                VAvatar(name = name, size = 44.dp, src = header?.adminAvatarUrl)
            }
        }
    }


}

@Composable
private fun NotificationButton(count: Int, onClick: () -> Unit) {
    val c = VTheme.colors
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(c.ink.copy(alpha = .06f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = VIcons.Bell,
            contentDescription = "Notifications",
            tint = c.ink,
            modifier = Modifier.size(20.dp),
        )
        if (count > 0) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(c.danger)
                    .align(Alignment.TopEnd),
            )
        }
    }


}

@Composable
private fun QuickActionsRow(
    onAnnouncement: () -> Unit,
    onEvent: () -> Unit,
    onNotice: () -> Unit,
    onReports: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuickActionChip("Announcement", VIcons.Megaphone, onAnnouncement)
        QuickActionChip("Add Event", VIcons.Calendar, onEvent)
        QuickActionChip("Send Notice", VIcons.Send, onNotice)
        QuickActionChip("Reports", VIcons.TrendingUp, onReports)
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    val c = VTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(c.card)
            .vElevation(VElevationLevel.Card, radius = 50.dp)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(16.dp))
        Text(label, style = VTheme.type.caption.colored(c.ink), maxLines = 1)
    }
}

// =====================================================================
// 2. Smart Insights carousel
// =====================================================================

@Composable
private fun SmartInsightsCarousel(insights: List<OverviewInsight>) {
    val c = VTheme.colors
    // Most urgent first.
    val ordered = insights.sortedBy { severityRank(it.severity) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Smart Insights", style = VTheme.type.h3.colored(c.ink))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ordered.forEach { InsightCard(it) }
        }
    }


}

private fun severityRank(s: String): Int = when (s.uppercase()) {
    "HIGH" -> 0; "MEDIUM" -> 1; "LOW" -> 2; else -> 3
}

@Composable
private fun InsightCard(insight: OverviewInsight) {
    val c = VTheme.colors
    val accent = when (insight.type.uppercase()) {
        "ALERT" -> c.dangerInk
        "REMINDER" -> c.warningInk
        "ACHIEVEMENT" -> c.successInk
        else -> c.tealDeep
    }
    val icon = when (insight.type.uppercase()) {
        "ALERT" -> VIcons.AlertTriangle
        "REMINDER" -> VIcons.Clock
        "ACHIEVEMENT" -> VIcons.Star
        else -> VIcons.Sparkles
    }
    Column(
        modifier = Modifier
            .widthIn(min = 230.dp, max = 280.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .vElevation(VElevationLevel.Card, radius = 20.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Text(insight.title, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 2)
        }
        Text(insight.description, style = VTheme.type.caption.colored(c.ink2), maxLines = 3)
    }


}

// =====================================================================
// 3. School Pulse (flagship)
// =====================================================================

@Composable
private fun SchoolPulseCard(pulse: OverviewSchoolPulse) {
    val c = VTheme.colors
    val animated by animateFloatAsState(
        targetValue = pulse.score / 100f,
        animationSpec = tween(900, easing = LinearEasing),
        label = "pulse",
    )
    VCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)), padding = 0.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(c.navy, c.navyDeep)))
                .padding(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("School Pulse", style = VTheme.type.h3.colored(Color.White))
                        Text(
                            pulse.message,
                            style = VTheme.type.caption.colored(Color.White.copy(alpha = .8f)),
                        )
                    }
                    StatusPill(pulse.status)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    PulseGauge(progress = animated, score = pulse.score)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        pulse.categories.filter { it.available }.forEach { cat ->
                            PulseCategoryRow(label = cat.label, score = cat.score)
                        }
                        if (pulse.categories.none { it.available }) {
                            Text(
                                "Metrics appear as your school records data.",
                                style = VTheme.type.caption.colored(Color.White.copy(alpha = .75f)),
                            )
                        }
                    }
                }
            }
        }
    }


}

@Composable
private fun StatusPill(status: String) {
    val tint = when (status.uppercase()) {
        "EXCELLENT", "HEALTHY" -> Color(0xFF5FD6A4)
        "WATCH" -> Color(0xFFFFC275)
        else -> Color(0xFFFF8A82)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = .18f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(status, style = VTheme.type.label.colored(tint))
    }


}

@Composable
private fun PulseGauge(progress: Float, score: Int) {
    val track = Color.White.copy(alpha = .18f)
    val arc = Color(0xFF3CD1BE)
    Box(modifier = Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(112.dp)) {
            val stroke = 12.dp.toPx()
            val diameter = min(size.width, size.height) - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            // Track (full ring)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress
            drawArc(
                color = arc,
                startAngle = 135f,
                sweepAngle = 270f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                score.toString(),
                style = VTheme.type.h1.colored(Color.White),
            )
            Text("/ 100", style = VTheme.type.caption.colored(Color.White.copy(alpha = .75f)))
        }
    }


}

@Composable
private fun PulseCategoryRow(label: String, score: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = VTheme.type.caption.colored(Color.White.copy(alpha = .85f)))
            Text("$score", style = VTheme.type.caption.colored(Color.White))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = .16f)),
        ) {
            val frac by animateFloatAsState(
                targetValue = (score / 100f).coerceIn(0f, 1f),
                animationSpec = tween(800),
                label = "catbar",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF3CD1BE)),
            )
        }
    }


}

// =====================================================================
// 4. KPI grid
// =====================================================================

@Composable
private fun KpiGrid(kpis: List<OverviewKpi>, onClick: () -> Unit) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Key Metrics", style = VTheme.type.h3.colored(c.ink))
        // 2-up rows.
        kpis.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { kpi ->
                    KpiCard(kpi = kpi, modifier = Modifier.weight(1f), onClick = onClick)
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }


}

@Composable
private fun KpiCard(kpi: OverviewKpi, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = VTheme.colors
    val icon = when (kpi.key) {
        "students" -> VIcons.Users
        "teachers" -> VIcons.GraduationCap
        "attendance" -> VIcons.ListChecks
        "fees" -> VIcons.Wallet
        "parents" -> VIcons.Heart
        "approvals" -> VIcons.ShieldCheck
        "events" -> VIcons.Calendar
        else -> VIcons.Target
    }
    val deltaPositive = kpi.deltaDirection != "down"
    val deltaTint = if (deltaPositive) c.successInk else c.warningInk
    val showDelta = kpi.deltaDirection != "flat" && kpi.deltaValue > 0.0
    VCard(modifier = modifier.clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp))
                        .background(c.teal.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(20.dp))
                }
                if (showDelta) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(
                            VIcons.TrendingUp,
                            contentDescription = null,
                            tint = deltaTint,
                            modifier = Modifier.size(13.dp).rotate(if (deltaPositive) 0f else 180f),
                        )
                        Text(formatDelta(kpi), style = VTheme.type.caption.colored(deltaTint))
                    }
                }
            }
            Text(
                "${kpi.value}${kpi.unit}",
                style = VTheme.type.h2.colored(c.ink),
            )
            Text(kpi.label, style = VTheme.type.caption.colored(c.ink2), maxLines = 1)
            if (kpi.deltaLabel.isNotBlank()) {
                Text(kpi.deltaLabel, style = VTheme.type.dataSm.colored(c.ink3), maxLines = 1)
            }
        }
    }


}

private fun formatDelta(kpi: OverviewKpi): String {
    val sign = if (kpi.deltaDirection == "up") "+" else "-"
    val v = if (kpi.deltaValue % 1.0 == 0.0) kpi.deltaValue.toInt().toString()
    else ((kpi.deltaValue * 10).toInt() / 10.0).toString()
    return "$sign$v${kpi.unit}"
}

// =====================================================================
// 5. Campus Health (attendance trend)
// =====================================================================

@Composable
private fun CampusHealthCard(analytics: AdminDashboardAnalytics?, onClick: () -> Unit) {
    val c = VTheme.colors
    val trend = analytics?.attendanceTrend
    val values = trend?.values.orEmpty()
    val labels = trend?.labels.orEmpty()
    val points = values.map { (it.coerceIn(0, 100)) / 100f }
    val hasData = points.size >= 2
    val delta = if (values.size >= 2) values.last() - values.first() else null

    VCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Campus Health", style = VTheme.type.h3.colored(c.ink))
                    Text(
                        if (hasData) "Attendance over ${values.size} ${trend?.period.orEmpty().ifEmpty { "periods" }}"
                        else "No attendance data yet",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
                if (delta != null) DeltaBadge(delta)
            }
            if (hasData) {
                AttendanceLineGraph(points = points)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    labels.forEach { Text(it, style = VTheme.type.dataSm.colored(c.ink3)) }
                }
            } else {
                Text(
                    "Attendance trends appear once daily records are captured.",
                    style = VTheme.type.caption.colored(c.ink2),
                )
            }
        }
    }


}

@Composable
private fun DeltaBadge(delta: Int) {
    val c = VTheme.colors
    val positive = delta >= 0
    val tint = if (positive) c.successInk else c.warningInk
    Box(
        modifier = Modifier.background(tint.copy(alpha = .12f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text("${if (positive) "+" else ""}$delta%", style = VTheme.type.caption.colored(tint))
    }


}

@Composable
private fun AttendanceLineGraph(points: List<Float>) {
    val c = VTheme.colors
    if (points.size < 2) return
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val widthStep = size.width / (points.size - 1)
        val graphHeight = size.height - 18.dp.toPx()
        val path = Path()
        val fillPath = Path()
        points.forEachIndexed { index, value ->
            val x = index * widthStep
            val y = graphHeight - (graphHeight * value.coerceIn(0f, 1f))
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()
        drawPath(path = fillPath, color = c.teal.copy(alpha = .12f))
        drawPath(path = path, color = c.tealDeep, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { index, value ->
            val x = index * widthStep
            val y = graphHeight - (graphHeight * value.coerceIn(0f, 1f))
            drawCircle(color = c.tealDeep, radius = 5.dp.toPx(), center = Offset(x, y))
        }
    }


}

// =====================================================================
// 6. Fee analytics
// =====================================================================

@Composable
private fun FeeAnalyticsCard(
    fee: com.littlebridge.enrollplus.feature.admin.domain.model.OverviewFeeAnalytics,
    onClick: () -> Unit,
) {
    val c = VTheme.colors
    VCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Fee Collection", style = VTheme.type.h3.colored(c.ink))
                    Text("Collection rate ${fee.collectionRate}%", style = VTheme.type.caption.colored(c.ink2))
                }
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.teal.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.Wallet, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(22.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeeStat(
                    modifier = Modifier.weight(1f),
                    label = "Collected",
                    value = formatMoney(fee.totalCollected, fee.currency),
                    positive = true,
                )
                FeeStat(
                    modifier = Modifier.weight(1f),
                    label = "Pending",
                    value = formatMoney(fee.pending, fee.currency),
                    positive = false,
                )
            }
            if (fee.trend.size >= 2) {
                FeeBars(values = fee.trend.map { it.value }, labels = fee.trend.map { it.label })
            }
        }
    }


}

@Composable
private fun FeeStat(label: String, value: String, positive: Boolean, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    val tint = if (positive) c.successInk else c.warningInk
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .10f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = VTheme.type.h4.colored(tint))
        Text(label, style = VTheme.type.caption.colored(c.ink2))
    }


}

@Composable
private fun FeeBars(values: List<Int>, labels: List<String>) {
    val c = VTheme.colors
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEach { v ->
                val frac by animateFloatAsState(
                    targetValue = (v.toFloat() / max).coerceIn(0.04f, 1f),
                    animationSpec = tween(700),
                    label = "feebar",
                )
                // Each column gets equal width via weight; the bar fills a
                // fraction of the fixed-height row (96dp) anchored to the bottom.
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(frac)
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(c.tealDeep),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach {
                Text(it, style = VTheme.type.dataSm.colored(c.ink3), modifier = Modifier.weight(1f))
            }
        }
    }


}

private fun formatMoney(amount: Double, currency: String): String {
    val symbol = when (currency.uppercase()) { "INR" -> "₹"; "USD" -> "$"; else -> "$currency " }
    val rounded = amount.toLong()
    return when {
        rounded >= 10_000_000 -> "$symbol${(rounded / 100_000) / 10.0}Cr"
        rounded >= 100_000 -> "$symbol${(rounded / 1_000) / 100.0}L"
        rounded >= 1_000 -> "$symbol${(rounded / 100) / 10.0}K"
        else -> "$symbol$rounded"
    }


}

// =====================================================================
// 7. Parent engagement center
// =====================================================================

@Composable
private fun ParentEngagementCard(
    engagement: com.littlebridge.enrollplus.feature.admin.domain.model.OverviewParentEngagement,
) {
    val c = VTheme.colors
    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.teal.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.Heart, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Parent Engagement", style = VTheme.type.h3.colored(c.ink))
                    Text(
                        "${engagement.activeParentsPct}% active · ${engagement.activeParents}/${engagement.totalParents} parents",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
            }
            if (engagement.mostEngagedClass.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .background(c.successInk.copy(alpha = .10f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(VIcons.Star, contentDescription = null, tint = c.successInk, modifier = Modifier.size(18.dp))
                    Text("Most engaged: ${engagement.mostEngagedClass}", style = VTheme.type.bodyStrong.colored(c.ink))
                }
            }
            if (engagement.leaderboard.isNotEmpty()) {
                Text("Class Leaderboard", style = VTheme.type.label.colored(c.ink3))
                engagement.leaderboard.forEachIndexed { idx, lc ->
                    LeaderboardRow(rank = idx + 1, item = lc)
                }
            }
        }
    }


}

@Composable
private fun LeaderboardRow(rank: Int, item: OverviewLeaderClass) {
    val c = VTheme.colors
    val medal = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "$rank" }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.width(28.dp), contentAlignment = Alignment.Center) {
            Text(medal, style = VTheme.type.bodyStrong.colored(c.ink))
        }
        Text(item.className, style = VTheme.type.body.colored(c.ink), modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.height(6.dp).width(60.dp).clip(RoundedCornerShape(50)).background(c.ink.copy(alpha = .08f)),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth((item.score / 100f).coerceIn(0f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(50)).background(c.tealDeep),
            )
        }
        val dirTint = when (item.direction) { "up" -> c.successInk; "down" -> c.warningInk; else -> c.ink3 }
        Text("${item.score}", style = VTheme.type.caption.colored(dirTint))
    }


}

// =====================================================================
// 8. Communication center
// =====================================================================

@Composable
private fun CommunicationCenterCard(
    unread: Int,
    pending: Int,
    announcements: Int,
    acks: Int,
    onOpenComms: () -> Unit,
) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Communication", style = VTheme.type.h3.colored(c.ink))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CommTile(Modifier.weight(1f), "Unread", unread.toString(), VIcons.Chat, onOpenComms)
            CommTile(Modifier.weight(1f), "Queries", pending.toString(), VIcons.AlertCircle, onOpenComms)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CommTile(Modifier.weight(1f), "Announcements", announcements.toString(), VIcons.Megaphone, onOpenComms)
            CommTile(Modifier.weight(1f), "Acknowledgements", acks.toString(), VIcons.Check, onOpenComms)
        }
    }


}

@Composable
private fun CommTile(modifier: Modifier, label: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    val c = VTheme.colors
    VCard(modifier = modifier.clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(c.teal.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(18.dp))
            }
            Text(value, style = VTheme.type.h3.colored(c.ink))
            Text(label, style = VTheme.type.caption.colored(c.ink2), maxLines = 1)
        }
    }


}

// =====================================================================
// 9. Event dashboard
// =====================================================================

@Composable
private fun EventDashboardCard(
    upcoming: List<OverviewEvent>,
    completed: List<OverviewEvent>,
    onOpenCalendar: () -> Unit,
) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Events", style = VTheme.type.h3.colored(c.ink))
            Text("View calendar →", style = VTheme.type.caption.colored(c.tealDeep), modifier = Modifier.clickable { onOpenCalendar() })
        }
        if (upcoming.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                upcoming.forEach { EventCountdownCard(it) }
            }
        }
        if (completed.isNotEmpty()) {
            Text("Recently completed", style = VTheme.type.label.colored(c.ink3))
            completed.forEach { CompletedEventRow(it) }
        }
    }


}

@Composable
private fun EventCountdownCard(event: OverviewEvent) {
    val c = VTheme.colors
    val accent = if (event.isHoliday) c.warningInk else c.tealDeep
    Column(
        modifier = Modifier
            .widthIn(min = 150.dp, max = 190.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .vElevation(VElevationLevel.Card, radius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = .14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(
                when {
                    event.daysAway == 0 -> "Today"
                    event.daysAway == 1 -> "Tomorrow"
                    else -> "In ${event.daysAway} days"
                },
                style = VTheme.type.label.colored(accent),
            )
        }
        Text(event.title, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 2)
        Text(event.date, style = VTheme.type.dataSm.colored(c.ink3))
    }


}

@Composable
private fun CompletedEventRow(event: OverviewEvent) {
    val c = VTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(VIcons.Check, contentDescription = null, tint = c.successInk, modifier = Modifier.size(16.dp))
        Text(event.title, style = VTheme.type.body.colored(c.ink), modifier = Modifier.weight(1f), maxLines = 1)
        Text(event.date, style = VTheme.type.dataSm.colored(c.ink3))
    }


}

// =====================================================================
// 1b. Academic Calendar — Quick Insights + Upcoming Events carousel
// =====================================================================

/**
 * Compact KPI strip surfacing the three headline calendar metrics the spec
 * requires on the home page: events this week, draft events, and the next
 * holiday. Values are derived from the calendar dashboard payload so they stay
 * consistent with the dedicated Academic Calendar screen.
 */
@Composable
private fun CalendarQuickInsights(
    dashboard: CalendarDashboardDto,
    onOpenCalendar: () -> Unit,
) {
    val c = VTheme.colors

    // Events this week → server-computed KPI (events starting within 7 days),
    // falling back to the upcoming-timeline size if the KPI is unavailable.
    val eventsThisWeek = dashboard.analytics
        .firstOrNull { it.key.equals("this_week", ignoreCase = true) }
        ?.value
        ?: dashboard.upcomingTimeline.size

    // Draft events → prefer the analytics KPI, fall back to the drafts list.
    val draftCount = dashboard.analytics
        .firstOrNull { it.key.equals("draft", ignoreCase = true) || it.key.equals("drafts", ignoreCase = true) }
        ?.value
        ?: dashboard.draftEvents.size

    // Next holiday → earliest upcoming HOLIDAY across timeline + highlights.
    val nextHoliday = (dashboard.upcomingTimeline + dashboard.upcomingHighlights)
        .filter { it.type.equals(CalEventType.HOLIDAY, ignoreCase = true) }
        .minByOrNull { it.startDate }
    val nextHolidayLabel = nextHoliday?.let { homeFormatShortDate(it.startDate) } ?: "—"

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar at a glance", style = VTheme.type.h3.colored(c.ink))
            Text(
                "Open calendar →",
                style = VTheme.type.caption.colored(c.tealDeep),
                modifier = Modifier.clickable { onOpenCalendar() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarInsightTile(
                modifier = Modifier.weight(1f),
                value = eventsThisWeek.toString(),
                label = "This week",
                icon = VIcons.Calendar,
                accent = c.tealDeep,
                onClick = onOpenCalendar,
            )
            CalendarInsightTile(
                modifier = Modifier.weight(1f),
                value = draftCount.toString(),
                label = "Drafts",
                icon = VIcons.FileText,
                accent = c.warningInk,
                onClick = onOpenCalendar,
            )
            CalendarInsightTile(
                modifier = Modifier.weight(1f),
                value = nextHolidayLabel,
                label = "Next holiday",
                icon = VIcons.Sparkles,
                accent = c.successInk,
                onClick = onOpenCalendar,
            )
        }
    }


}

@Composable
private fun CalendarInsightTile(
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    VCard(modifier = modifier.clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Text(value, style = VTheme.type.h3.colored(c.ink), maxLines = 1)
            Text(label, style = VTheme.type.caption.colored(c.ink2), maxLines = 1)
        }
    }


}

/**
 * Horizontal carousel of upcoming calendar events for the home page. Prefers the
 * curated highlights from the dashboard, falling back to the upcoming timeline.
 */
@Composable
private fun CalendarUpcomingCarousel(
    dashboard: CalendarDashboardDto,
    onOpenCalendar: () -> Unit,
) {
    val c = VTheme.colors
    val events = dashboard.upcomingHighlights.ifEmpty { dashboard.upcomingTimeline }
    if (events.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Upcoming Events", style = VTheme.type.h3.colored(c.ink))
            Text(
                "See all →",
                style = VTheme.type.caption.colored(c.tealDeep),
                modifier = Modifier.clickable { onOpenCalendar() },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            events.take(8).forEach { ev ->
                CalendarUpcomingCard(event = ev, onClick = onOpenCalendar)
            }
        }
    }


}

@Composable
private fun CalendarUpcomingCard(event: AcademicCalendarEventDto, onClick: () -> Unit) {
    val c = VTheme.colors
    val accent = when (event.type.uppercase()) {
        CalEventType.HOLIDAY -> c.warningInk
        CalEventType.PTM -> c.navy
        CalEventType.EXAM -> c.dangerInk
        else -> c.tealDeep
    }
    Column(
        modifier = Modifier
            .widthIn(min = 180.dp, max = 220.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .vElevation(VElevationLevel.Card, radius = 18.dp)
            .clickable { onClick() }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(50)).background(accent.copy(alpha = .14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        ) {
            Text(CalEventType.label(event.type), style = VTheme.type.label.colored(accent))
        }
        Text(event.title, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 2)
        Text(
            if (event.isMultiDay) {
                "${homeFormatShortDate(event.startDate)} – ${homeFormatShortDate(event.endDate)}"
            } else {
                homeFormatShortDate(event.startDate)
            },
            style = VTheme.type.dataSm.colored(c.ink3),
        )
        if (event.status.equals(CalEventStatus.DRAFT, ignoreCase = true)) {
            Text("Draft", style = VTheme.type.label.colored(c.warningInk))
        }
        if (event.hasConflicts) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(VIcons.AlertTriangle, contentDescription = null, tint = c.dangerInk, modifier = Modifier.size(13.dp))
                Text("Conflict", style = VTheme.type.label.colored(c.dangerInk))
            }
        }
    }


}

// ── tiny date helper for the home calendar widgets (ISO yyyy-MM-dd) ─────────

private fun homeFormatShortDate(iso: String): String {
    // iso = yyyy-MM-dd → "DD MON"
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    val month = parts[1].toIntOrNull() ?: return iso
    val day = parts[2].toIntOrNull() ?: return iso
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val mon = months.getOrElse(month - 1) { parts[1] }
    return "$day $mon"
}

// =====================================================================
// 10. Teacher spotlight
// =====================================================================

@Composable
private fun TeacherSpotlightCard(
    name: String,
    department: String,
    avatarUrl: String?,
    score: Int,
    highlight: String,
    onClick: () -> Unit,
) {
    val c = VTheme.colors
    VCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }, padding = 0.dp) {
        Box(
            modifier = Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(c.tealDeep, c.teal)))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                VAvatar(name = name, size = 56.dp, src = avatarUrl, ring = true)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("⭐ Teacher Spotlight", style = VTheme.type.label.colored(Color.White.copy(alpha = .85f)))
                    Text(name, style = VTheme.type.h3.colored(Color.White))
                    if (department.isNotBlank()) {
                        Text(department, style = VTheme.type.caption.colored(Color.White.copy(alpha = .85f)))
                    }
                    if (highlight.isNotBlank()) {
                        Text(highlight, style = VTheme.type.caption.colored(Color.White.copy(alpha = .9f)))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$score", style = VTheme.type.h2.colored(Color.White))
                    Text("score", style = VTheme.type.caption.colored(Color.White.copy(alpha = .8f)))
                }
            }
        }
    }


}

// =====================================================================
// 11. Achievement showcase
// =====================================================================

@Composable
private fun AchievementShowcase(items: List<OverviewAchievement>) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Student Achievements", style = VTheme.type.h3.colored(c.ink))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items.forEach { AchievementCard(it) }
        }
    }


}

@Composable
private fun AchievementCard(item: OverviewAchievement) {
    val c = VTheme.colors
    val accent = when (item.category.uppercase()) {
        "SPORTS" -> c.warningInk
        "COMPETITION" -> c.navy
        else -> c.tealDeep
    }
    Column(
        modifier = Modifier
            .widthIn(min = 200.dp, max = 240.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .vElevation(VElevationLevel.Card, radius = 18.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VAvatar(name = item.studentName, size = 40.dp, src = item.imageUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.studentName, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 1)
                Text(item.category, style = VTheme.type.label.colored(accent))
            }
        }
        Text(item.title, style = VTheme.type.caption.colored(c.ink), maxLines = 2)
        Text(item.detail, style = VTheme.type.dataSm.colored(c.ink2), maxLines = 2)
    }


}

// =====================================================================
// 12. Birthday widget
// =====================================================================

@Composable
private fun BirthdayWidget(today: List<OverviewBirthday>, upcoming: List<OverviewBirthday>) {
    val c = VTheme.colors
    if (today.isEmpty() && upcoming.isEmpty()) return
    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("🎂", style = VTheme.type.h3.colored(c.ink))
                Text("Celebrations", style = VTheme.type.h3.colored(c.ink))
            }
            if (today.isNotEmpty()) {
                Text("Today", style = VTheme.type.label.colored(c.ink3))
                today.forEach { BirthdayRow(it, isToday = true) }
            }
            if (upcoming.isNotEmpty()) {
                Text("Upcoming", style = VTheme.type.label.colored(c.ink3))
                upcoming.forEach { BirthdayRow(it, isToday = false) }
            }
        }
    }


}

@Composable
private fun BirthdayRow(b: OverviewBirthday, isToday: Boolean) {
    val c = VTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VAvatar(name = b.name, size = 38.dp, src = b.avatarUrl)
        Column(modifier = Modifier.weight(1f)) {
            Text(b.name, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 1)
            Text(if (b.role == "TEACHER") "Teacher" else "Student", style = VTheme.type.caption.colored(c.ink2))
        }
        Text(
            if (isToday) "🎉 Today" else "in ${b.daysAway}d",
            style = VTheme.type.caption.colored(if (isToday) c.successInk else c.ink3),
        )
    }


}

// =====================================================================
// 13. Live activity feed
// =====================================================================

data class ActivityItem(val title: String, val subtitle: String, val time: String)

@Composable
private fun ActivityFeedCard(activities: List<ActivityItem>, onClick: () -> Unit) {
    val c = VTheme.colors
    VCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Live Activity", style = VTheme.type.h3.colored(c.ink))
            activities.take(6).forEachIndexed { index, item ->
                TimelineItem(item, isLast = index == activities.take(6).lastIndex)
            }
        }
    }


}

@Composable
private fun TimelineItem(activity: ActivityItem, isLast: Boolean) {
    val c = VTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(10.dp).background(c.tealDeep, CircleShape))
            if (!isLast) Spacer(Modifier.height(44.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(activity.title, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 2)
            if (activity.subtitle.isNotBlank()) {
                Text(activity.subtitle, style = VTheme.type.caption.colored(c.ink2), maxLines = 2)
            }
            Text(activity.time, style = VTheme.type.dataSm.colored(c.ink3))
            if (!isLast) HorizontalDivider(modifier = Modifier.padding(top = 10.dp))
        }
    }


}

// =====================================================================
// 14. Entry cards
// =====================================================================

@Composable
private fun AnalyticsEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FeatureCard(
        title = "School Analytics",
        description = "Attendance, academics & growth insights",
        icon = VIcons.TrendingUp,
        button = "Explore Analytics",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun PewsEntryCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FeatureCard(
        title = "Student Risk Monitor",
        description = "Identify students needing attention early",
        icon = VIcons.AlertCircle,
        button = "Open Monitor",
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    button: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    VCard(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(c.teal.copy(alpha = .15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = VTheme.type.h4.colored(c.ink))
                Text(description, style = VTheme.type.caption.colored(c.ink2))
                Text("$button  →", style = VTheme.type.bodyStrong.colored(c.tealDeep))
            }
        }
    }
}
