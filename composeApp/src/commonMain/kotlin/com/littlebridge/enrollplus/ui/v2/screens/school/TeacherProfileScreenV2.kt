package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAchievementDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherActivityDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAssignmentDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherProfileDto
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherProfileUiState
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherProfileViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-45 / RA-PP: TeacherProfileScreenV2 — a single teacher's detail for the
 * admin, redesigned as a modern dashboard/profile experience.
 *
 * Structure (top → bottom): Hero banner · KPI carousel · Performance overview ·
 * Teaching portfolio carousel · Insights · Recent activity timeline ·
 * Achievements carousel · Professional details · Danger zone.
 *
 * [teacherId] is passed by the caller and loaded via [TeacherProfileViewModel.load]
 * in a LaunchedEffect. Three states via [VStateHost] (LAW 3). Portal overlay —
 * back returns to People.
 */
@Composable
fun TeacherProfileScreenV2(
    teacherId: String,
    onBack: () -> Unit = {},
    // RA-S17: called after a successful soft-delete so the host can pop back to
    // People and refresh the roster.
    onRemoved: () -> Unit = onBack,
    // RA-TAM — Quick Action → reusable Teacher Assignment Management module.
    onOpenAssignments: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    LaunchedEffect(teacherId) { viewModel.load(teacherId) }
    // RA-S17: when the VM confirms removal, leave the profile.
    LaunchedEffect(state.removed) { if (state.removed) onRemoved() }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        VBackHeader(title = "Teacher", onBack = onBack)
        TeacherProfileContent(
            state = state,
            onRetry = viewModel::retry,
            onRemove = { viewModel.remove(teacherId) },
            onOpenAssignments = onOpenAssignments,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TeacherProfileContent(
    state: TeacherProfileUiState,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpenAssignments: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRemove by remember { mutableStateOf(false) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.profile == null && !state.isLoading && state.error == null,
            emptyTitle = "No profile",
            emptyBody = "This teacher's record could not be found.",
            emptyIcon = VIcons.User,
            onRetry = onRetry,
        ) {
            val p = state.profile ?: return@VStateHost
            TeacherProfileBody(p, onOpenAssignments = onOpenAssignments)

            // ── 9. Danger zone — destructive action separated at the bottom. ──
            Spacer(Modifier.height(8.dp))
            DangerZone(
                isRemoving = state.isRemoving,
                removeError = state.removeError,
                onRequestRemove = { confirmRemove = true },
            )
        }
    }

    VConfirmDialog(
        visible = confirmRemove,
        title = "Remove teacher",
        message = "Remove ${state.profile?.name ?: "this teacher"} from your school? " +
            "They will lose access immediately. This can be reversed by re-adding them.",
        confirmLabel = "Remove",
        icon = VIcons.AlertTriangle,
        onConfirm = { confirmRemove = false; onRemove() },
        onDismiss = { confirmRemove = false },
    )
}

@Composable
private fun TeacherProfileBody(p: TeacherProfileDto, onOpenAssignments: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HeroBanner(p)                 // 1. Hero profile banner
        QuickActions(onOpenAssignments = onOpenAssignments) // 1b. Quick actions
        KpiCarousel(p)                // 2. KPI carousel
        PerformanceOverview(p)        // 3. Performance overview
        TeachingPortfolio(p.assignments) // 4. Teaching portfolio carousel
        InsightsSection(p.insights)   // 5. AI / teacher insights
        ActivityTimeline(p.recentActivities) // 6. Recent activity timeline
        AchievementsCarousel(p.achievements) // 7. Achievements carousel
        ProfessionalDetails(p)        // 8. Professional details
    }
}

// ───────────────────────── 1. Hero profile banner ─────────────────────────

@Composable
private fun HeroBanner(p: TeacherProfileDto) {
    val c = VTheme.colors
    val active = p.status.equals("active", ignoreCase = true)
    VCard(padding = 20.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VAvatar(name = p.name, size = 76.dp, ring = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(p.name, style = VTheme.type.h2.colored(c.ink))
                val sub = listOfNotNull(
                    p.designation?.takeIf { it.isNotBlank() },
                    p.role.replaceFirstChar { it.uppercase() }.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, style = VTheme.type.caption.colored(c.ink2))
                }
                VBadge(
                    text = if (active) "Active" else "Inactive",
                    tone = if (active) VBadgeTone.Success else VBadgeTone.Neutral,
                    leadingIcon = VIcons.Check,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HeroFact(
                icon = VIcons.TrendingUp,
                label = "Experience",
                value = p.experienceYears?.let { "$it yr${if (it == 1) "" else "s"}" } ?: "—",
            )
            HeroFact(
                icon = VIcons.Calendar,
                label = "Joined",
                value = p.joinedOn?.takeIf { it.isNotBlank() } ?: "—",
            )
        }
    }
}

@Composable
private fun HeroFact(icon: ImageVector, label: String, value: String) {
    val c = VTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(c.tealDeep.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(value, style = VTheme.type.bodyStrong.colored(c.ink))
            Text(label, style = VTheme.type.label.colored(c.ink3))
        }
    }
}

// ─────────────────────────── 1b. Quick actions ────────────────────────────

/**
 * RA-TAM: Quick Actions row on the Teacher Profile dashboard. The "Assignments"
 * action is one of the three entry points into the reusable Teacher Assignment
 * Management module (single source of truth).
 */
@Composable
private fun QuickActions(onOpenAssignments: () -> Unit) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "QUICK ACTIONS")
        VCard(padding = 16.dp, onClick = onOpenAssignments) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                        .background(c.tealDeep.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.GraduationCap, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Assignments", style = VTheme.type.h4.colored(c.ink))
                    Text(
                        "Manage classes, subjects & sections",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
                Icon(VIcons.ChevronRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ──────────────────────────── 2. KPI carousel ─────────────────────────────

@Composable
private fun KpiCarousel(p: TeacherProfileDto) {
    val kpis = listOf(
        KpiCardData("Total Students", p.studentCount.toString(), "across classes", VIcons.Users, VBadgeTone.Arctic),
        KpiCardData("Classes", p.classCount.toString(), "sections taught", VIcons.School, VBadgeTone.Success),
        KpiCardData("Subjects", p.subjectCount.toString(), "covered", VIcons.BookOpen, VBadgeTone.Warning),
        KpiCardData("Attendance", "${p.attendancePercent.toInt()}%", "personal", VIcons.Check, VBadgeTone.Success),
        KpiCardData("Assignments", "${p.assignmentCompletionPercent.toInt()}%", "completion", VIcons.Target, VBadgeTone.Arctic),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "OVERVIEW")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(kpis) { kpi -> KpiCard(kpi) }
        }
    }
}

 data class KpiCardData(
    val label: String,
    val value: String,
    val support: String,
    val icon: ImageVector,
    val tone: VBadgeTone,
)

@Composable
private fun KpiCard(data: KpiCardData) {
    val c = VTheme.colors
    val tint = when (data.tone) {
        VBadgeTone.Accent -> c.tealDeep
        VBadgeTone.Arctic -> c.tealDeep
        VBadgeTone.Success -> c.successInk
        VBadgeTone.Warning -> c.warningInk
        VBadgeTone.Danger -> c.dangerInk
        VBadgeTone.Neutral -> c.ink3
    }
    VCard(modifier = Modifier.width(150.dp), padding = 16.dp) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(data.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(data.value, style = VTheme.type.dataLg.colored(c.ink))
        Text(data.label, style = VTheme.type.bodyStrong.colored(c.ink2))
        Text(data.support, style = VTheme.type.label.colored(c.ink3))
    }
}

// ───────────────────────── 3. Performance overview ────────────────────────

@Composable
private fun PerformanceOverview(p: TeacherProfileDto) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "PERFORMANCE")
        VCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricBar("Attendance", p.attendancePercent, VBadgeTone.Success)
                MetricBar("Assignment Completion", p.assignmentCompletionPercent, VBadgeTone.Arctic)
                MetricBar("Parent Satisfaction", p.parentSatisfactionPercent, VBadgeTone.Warning)
            }
        }
    }
}

@Composable
private fun MetricBar(label: String, value: Float, tone: VBadgeTone) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = VTheme.type.bodyStrong.colored(c.ink2))
            Text("${value.toInt()}%", style = VTheme.type.bodyStrong.colored(c.ink))
        }
        VProgressBar(value = value, tone = tone, height = 8.dp)
    }
}

// ────────────────────── 4. Teaching portfolio carousel ────────────────────

@Composable
private fun TeachingPortfolio(assignments: List<TeacherAssignmentDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "TEACHING PORTFOLIO")
        if (assignments.isEmpty()) {
            EmptyCard(VIcons.BookOpen, "No class or subject assignments yet.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(assignments) { a -> PortfolioCard(a) }
            }
        }
    }
}

@Composable
private fun PortfolioCard(a: TeacherAssignmentDto) {
    val c = VTheme.colors
    VCard(modifier = Modifier.width(180.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.tealDeep.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.BookOpen, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(18.dp))
            }
            VBadge(text = "Sec ${a.section}", tone = VBadgeTone.Neutral)
        }
        Spacer(Modifier.height(12.dp))
        Text(a.subject, style = VTheme.type.h4.colored(c.ink))
        Text(a.className, style = VTheme.type.caption.colored(c.ink2))
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(VIcons.Users, contentDescription = null, tint = c.ink3, modifier = Modifier.size(14.dp))
            Text("${a.studentCount} students", style = VTheme.type.caption.colored(c.ink3))
        }
    }
}

// ──────────────────────── 5. AI / teacher insights ────────────────────────

@Composable
private fun InsightsSection(insights: List<String>) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "INSIGHTS")
        if (insights.isEmpty()) {
            EmptyCard(VIcons.Sparkles, "No insights available yet.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                insights.forEach { insight ->
                    VCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.teal.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(VIcons.Sparkles, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(17.dp))
                            }
                            Text(insight, style = VTheme.type.body.colored(c.ink), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────── 6. Recent activity timeline ──────────────────────

@Composable
private fun ActivityTimeline(activities: List<TeacherActivityDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "RECENT ACTIVITY")
        if (activities.isEmpty()) {
            EmptyCard(VIcons.Clock, "No recent activity yet.")
        } else {
            VCard(padding = 18.dp) {
                Column {
                    activities.forEachIndexed { index, activity ->
                        TimelineRow(activity, isLast = index == activities.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineRow(activity: TeacherActivityDto, isLast: Boolean) {
    val c = VTheme.colors
    val tone = activityTone(activity.type)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // marker + connector line
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(tone))
            if (!isLast) {
                Box(Modifier.width(2.dp).height(34.dp).background(c.hairline))
            }
        }
        Column(Modifier.padding(bottom = if (isLast) 0.dp else 12.dp)) {
            Text(activity.title, style = VTheme.type.bodyStrong.colored(c.ink))
            Text(formatActivityMeta(activity), style = VTheme.type.label.colored(c.ink3))
        }
    }
}

@Composable
private fun activityTone(type: String) = when (type.lowercase()) {
    "homework" -> VTheme.colors.tealDeep
    "exam_result" -> VTheme.colors.successInk
    "assessment" -> VTheme.colors.warningInk
    "announcement" -> VTheme.colors.dangerInk
    else -> VTheme.colors.ink3
}

private fun formatActivityMeta(activity: TeacherActivityDto): String {
    val label = activity.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val date = activity.createdAt.take(10)
    return if (date.isNotBlank()) "$label · $date" else label
}

// ───────────────────────── 7. Achievements carousel ───────────────────────

@Composable
private fun AchievementsCarousel(achievements: List<TeacherAchievementDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ACHIEVEMENTS")
        if (achievements.isEmpty()) {
            EmptyCard(VIcons.Star, "No achievements yet.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(achievements) { index, item -> AchievementCard(item, index) }
            }
        }
    }
}

@Composable
private fun AchievementCard(item: TeacherAchievementDto, index: Int) {
    val c = VTheme.colors
    val tones = listOf(c.warningInk, c.successInk, c.tealDeep, c.dangerInk)
    val tint = tones[index % tones.size]
    VCard(modifier = Modifier.width(200.dp), padding = 16.dp) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VIcons.Star, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(item.title, style = VTheme.type.h4.colored(c.ink))
        Spacer(Modifier.height(4.dp))
        Text(item.description, style = VTheme.type.caption.colored(c.ink2), maxLines = 3)
    }
}

// ──────────────────────── 8. Professional details ─────────────────────────

@Composable
private fun ProfessionalDetails(p: TeacherProfileDto) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "PROFESSIONAL DETAILS")
        VCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailRow(VIcons.Mail, "Email", p.email?.takeIf { it.isNotBlank() } ?: "—")
                DetailRow(VIcons.Phone, "Phone", p.phone?.takeIf { it.isNotBlank() } ?: "—")
                DetailRow(VIcons.Calendar, "Joined Date", p.joinedOn?.takeIf { it.isNotBlank() } ?: "—")
                DetailRow(
                    VIcons.TrendingUp,
                    "Experience",
                    p.experienceYears?.let { "$it year${if (it == 1) "" else "s"}" } ?: "—",
                )
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val c = VTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.cream),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = c.ink2, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = VTheme.type.label.colored(c.ink3))
            Text(value, style = VTheme.type.bodyStrong.colored(c.ink))
        }
    }
}

// ─────────────────────────────── 9. Danger zone ───────────────────────────

@Composable
private fun DangerZone(
    isRemoving: Boolean,
    removeError: String?,
    onRequestRemove: () -> Unit,
) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "DANGER ZONE")
        VCard(padding = 18.dp, border = true) {
            Text("Remove teacher", style = VTheme.type.bodyStrong.colored(c.dangerInk))
            Spacer(Modifier.height(4.dp))
            Text(
                "Removing this teacher revokes their access immediately. This can be reversed by re-adding them.",
                style = VTheme.type.caption.colored(c.ink2),
            )
            Spacer(Modifier.height(14.dp))
            removeError?.let { err ->
                Text(err, style = VTheme.type.caption.colored(c.dangerInk))
                Spacer(Modifier.height(8.dp))
            }
            VButton(
                text = "Remove from school",
                onClick = onRequestRemove,
                variant = VButtonVariant.Destructive,
                full = true,
                enabled = !isRemoving,
                loading = isRemoving,
                leading = { Icon(VIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }
}

// ────────────────────────────── shared bits ───────────────────────────────

@Composable
private fun EmptyCard(icon: ImageVector, message: String) {
    val c = VTheme.colors
    VCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.cream),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(17.dp))
            }
            Text(message, style = VTheme.type.body.colored(c.ink2))
        }
    }
}
