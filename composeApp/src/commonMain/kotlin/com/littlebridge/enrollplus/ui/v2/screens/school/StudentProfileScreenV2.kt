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
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentActivityDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentParentDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentProfileDto
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentTeacherDto
import com.littlebridge.enrollplus.feature.admin.presentation.StudentProfileUiState
import com.littlebridge.enrollplus.feature.admin.presentation.StudentProfileViewModel
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
 * RA-45 / RA-SP: StudentProfileScreenV2 — a single student's record for the
 * admin, redesigned as a modern student-dashboard experience (matching the
 * Teacher Profile redesign).
 *
 * Structure (top → bottom): Hero banner · KPI carousel · Academic overview ·
 * Teacher connections · Parent connections · Attendance overview · Insights ·
 * Recent activity timeline · Marks · Leave · Fees · Contact info ·
 * Administrative info · Danger zone.
 *
 * [studentId] is passed by the caller and loaded via [StudentProfileViewModel.load]
 * in a LaunchedEffect. Three states via [VStateHost] (LAW 3). Portal overlay —
 * back returns to the roster.
 */
@Composable
fun StudentProfileScreenV2(
    studentId: String,
    onBack: () -> Unit = {},
    onRemoved: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    viewModel: StudentProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    LaunchedEffect(studentId) { viewModel.load(studentId) }
    LaunchedEffect(state.removed) { if (state.removed) onRemoved() }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        VBackHeader(title = "Student", onBack = onBack)
        StudentProfileContent(
            state = state,
            onRetry = viewModel::retry,
            onRemove = { viewModel.remove(studentId) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StudentProfileContent(
    state: StudentProfileUiState,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
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
            emptyBody = "This student's record could not be found.",
            emptyIcon = VIcons.User,
            onRetry = onRetry,
        ) {
            val p = state.profile ?: return@VStateHost
            StudentProfileBody(p)

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
        title = "Remove student",
        message = "Remove ${state.profile?.student?.fullName ?: "this student"} from your school? " +
            "Their records will be hidden. This can be reversed by re-adding them.",
        confirmLabel = "Remove",
        icon = VIcons.AlertTriangle,
        onConfirm = { confirmRemove = false; onRemove() },
        onDismiss = { confirmRemove = false },
    )
}

@Composable
private fun StudentProfileBody(p: StudentProfileDto) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HeroBanner(p)               // 1. Hero profile banner
        KpiCarousel(p)              // 2. KPI carousel
        AcademicOverview(p)         // 3. Academic overview
        TeacherConnections(p.teachers) // 4. Teacher connections
        ParentConnections(p.parents)   // 5. Parent connections
        AttendanceOverview(p)       // 6. Attendance overview
        InsightsSection(p.insights) // 7. Insights
        ActivityTimeline(p.activities) // 8. Recent activity timeline
        MarksSection(p)             // 9. Marks
        LeaveSection(p)             // 10. Leave
        FeesSection(p)              // 11. Fees
        ContactInformation(p)       // 12. Contact information
        AdministrativeInformation(p) // 13. Administrative information
    }
}

// ───────────────────────── 1. Hero profile banner ─────────────────────────

@Composable
private fun HeroBanner(p: StudentProfileDto) {
    val c = VTheme.colors
    val s = p.student
    val active = p.status.equals("active", ignoreCase = true)
    VCard(padding = 20.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VAvatar(name = s.fullName, src = s.profilePhotoUrl, size = 76.dp, ring = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.fullName, style = VTheme.type.h2.colored(c.ink))
                Text(
                    "${s.className} · Sec ${s.section}",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VBadge(
                        text = if (active) "Active" else "Inactive",
                        tone = if (active) VBadgeTone.Success else VBadgeTone.Neutral,
                        leadingIcon = VIcons.Check,
                    )
                    if (p.isNewAdmission) VBadge(text = "New Admission", tone = VBadgeTone.Arctic)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            HeroFact(VIcons.Bookmark, "Admission No.", s.studentCode)
            HeroFact(VIcons.User, "Roll No.", s.rollNumber)
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

// ──────────────────────────── 2. KPI carousel ─────────────────────────────

@Composable
private fun KpiCarousel(p: StudentProfileDto) {
    val kpis = buildList {
        add(KpiCardData("Attendance", "${p.attendancePercent.toInt()}%", "overall", VIcons.Check, VBadgeTone.Success))
        add(KpiCardData("Teachers", p.teacherCount.toString(), "connected", VIcons.Users, VBadgeTone.Arctic))
        add(KpiCardData("Parents", p.parentCount.toString(), "linked", VIcons.Heart, VBadgeTone.Warning))
        add(KpiCardData("Subjects", p.subjectCount.toString(), "studied", VIcons.BookOpen, VBadgeTone.Arctic))
        p.academicScore?.let {
            add(KpiCardData("Academic Score", "${it.toInt()}%", "average", VIcons.Star, VBadgeTone.Success))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "OVERVIEW")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(kpis) { kpi -> KpiCard(kpi) }
        }
    }
}


@Composable
private fun KpiCard(data: KpiCardData) {
    val c = VTheme.colors
    val tint = when (data.tone) {
        VBadgeTone.Arctic, VBadgeTone.Accent -> c.tealDeep
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

// ─────────────────────────── 3. Academic overview ─────────────────────────

@Composable
private fun AcademicOverview(p: StudentProfileDto) {
    val s = p.student
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ACADEMIC OVERVIEW")
        VCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailRow(VIcons.School, "Class", s.className)
                DetailRow(VIcons.Bookmark, "Section", s.section)
                DetailRow(VIcons.User, "Roll Number", s.rollNumber)
                DetailRow(VIcons.Calendar, "Admission Date", p.admissionDate?.takeIf { it.isNotBlank() } ?: "—")
            }
        }
    }
}

// ───────────────────────── 4. Teacher connections ─────────────────────────

@Composable
private fun TeacherConnections(teachers: List<StudentTeacherDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "TEACHER CONNECTIONS")
        if (teachers.isEmpty()) {
            EmptyCard(VIcons.Users, "No teachers connected yet.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(teachers) { t -> TeacherConnectionCard(t) }
            }
        }
    }
}

@Composable
private fun TeacherConnectionCard(t: StudentTeacherDto) {
    val c = VTheme.colors
    VCard(modifier = Modifier.width(190.dp), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            VAvatar(name = t.name, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(t.name, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 1)
                Text(t.subject, style = VTheme.type.caption.colored(c.ink2), maxLines = 1)
            }
        }
        if (!t.designation.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            VBadge(text = t.designation?:"", tone = VBadgeTone.Arctic)
        }
    }
}

// ───────────────────────── 5. Parent connections ──────────────────────────

@Composable
private fun ParentConnections(parents: List<StudentParentDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "PARENT CONNECTIONS")
        if (parents.isEmpty()) {
            EmptyCard(VIcons.Heart, "No parents linked yet.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                parents.forEach { ParentConnectionCard(it) }
            }
        }
    }
}

@Composable
private fun ParentConnectionCard(parent: StudentParentDto) {
    val c = VTheme.colors
    VCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VAvatar(name = parent.name, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(parent.name, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 1)
                Text(parent.relation, style = VTheme.type.caption.colored(c.ink2))
                parent.phone?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = VTheme.type.label.colored(c.ink3))
                }
            }
            if (parent.isPrimaryGuardian) {
                VBadge(text = "Primary Guardian", tone = VBadgeTone.Success)
            }
        }
    }
}

// ───────────────────────── 6. Attendance overview ─────────────────────────

@Composable
private fun AttendanceOverview(p: StudentProfileDto) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ATTENDANCE OVERVIEW")
        VCard(padding = 18.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Attendance rate", style = VTheme.type.bodyStrong.colored(c.ink2))
                Text("${p.attendanceRate}%", style = VTheme.type.bodyStrong.colored(c.ink))
            }
            Spacer(Modifier.height(8.dp))
            VProgressBar(
                value = p.attendanceRate.toFloat(),
                tone = if (p.attendanceRate < 75) VBadgeTone.Warning else VBadgeTone.Success,
                height = 8.dp,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("Present", p.presentDays, Modifier.weight(1f))
                StatPill("Absent", p.absentDays, Modifier.weight(1f))
                StatPill("Late", p.lateDays, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(c.cream).padding(12.dp),
    ) {
        Text(value.toString(), style = VTheme.type.dataSm.colored(c.ink))
        Text(label, style = VTheme.type.label.colored(c.ink3))
    }
}

// ─────────────────────────── 7. Insights ──────────────────────────────────

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

// ─────────────────────── 8. Recent activity timeline ──────────────────────

@Composable
private fun ActivityTimeline(activities: List<StudentActivityDto>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "RECENT ACTIVITY")
        if (activities.isEmpty()) {
            EmptyCard(VIcons.Calendar, "No recent activity yet.")
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
private fun TimelineRow(activity: StudentActivityDto, isLast: Boolean) {
    val c = VTheme.colors
    val tone = activityTone(activity.type)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
    "admission" -> VTheme.colors.tealDeep
    "marks" -> VTheme.colors.successInk
    "parent_link" -> VTheme.colors.warningInk
    "attendance" -> VTheme.colors.dangerInk
    else -> VTheme.colors.ink3
}

private fun formatActivityMeta(activity: StudentActivityDto): String {
    val label = activity.type.replace('_', ' ').replaceFirstChar { it.uppercase() }
    val date = activity.createdAt.take(10)
    return if (date.isNotBlank()) "$label · $date" else label
}

// ─────────────────────────────── 9. Marks ─────────────────────────────────

@Composable
private fun MarksSection(p: StudentProfileDto) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "MARKS")
        if (p.marks.isEmpty()) {
            EmptyCard(VIcons.BookOpen, "No marks recorded yet.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                p.marks.forEach { m ->
                    VCard(padding = 16.dp) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.subject} · ${m.assessmentName}", style = VTheme.type.bodyStrong.colored(c.ink))
                                m.examDate?.let { Text(it, style = VTheme.type.label.colored(c.ink3)) }
                            }
                            Text(
                                "${m.marks?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "—"} / ${m.maxMarks}",
                                style = VTheme.type.dataSm.colored(c.ink),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── 10. Leave ────────────────────────────────

@Composable
private fun LeaveSection(p: StudentProfileDto) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "LEAVE")
        if (p.leave.isEmpty()) {
            EmptyCard(VIcons.Calendar, "No leave applications.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                p.leave.forEach { l ->
                    VCard(padding = 16.dp) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${l.dateFrom} → ${l.dateTo}", style = VTheme.type.bodyStrong.colored(c.ink))
                                Text(l.reason, style = VTheme.type.caption.colored(c.ink2))
                            }
                            VBadge(
                                text = l.status,
                                tone = when (l.status.lowercase()) {
                                    "approved" -> VBadgeTone.Success
                                    "rejected" -> VBadgeTone.Danger
                                    else -> VBadgeTone.Warning
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── 11. Fees ─────────────────────────────────

@Composable
private fun FeesSection(p: StudentProfileDto) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "FEES")
        if (p.fees.isEmpty()) {
            EmptyCard(VIcons.Bookmark, "No fee records.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                p.fees.forEach { f ->
                    VCard(padding = 16.dp) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(f.title, style = VTheme.type.bodyStrong.colored(c.ink))
                                f.dueDate?.let { Text("Due $it", style = VTheme.type.label.colored(c.ink3)) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "${f.currency} ${if (f.amount % 1.0 == 0.0) f.amount.toInt().toString() else f.amount.toString()}",
                                    style = VTheme.type.dataSm.colored(c.ink),
                                )
                                VBadge(
                                    text = f.status,
                                    tone = when (f.status.uppercase()) {
                                        "PAID" -> VBadgeTone.Success
                                        "OVERDUE" -> VBadgeTone.Danger
                                        else -> VBadgeTone.Warning
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────── 12. Contact information ──────────────────────────

@Composable
private fun ContactInformation(p: StudentProfileDto) {
    val primary = p.parents.firstOrNull { it.isPrimaryGuardian } ?: p.parents.firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "CONTACT INFORMATION")
        VCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailRow(VIcons.Heart, "Primary Guardian", primary?.name ?: "—")
                DetailRow(VIcons.Phone, "Phone", primary?.phone?.takeIf { it.isNotBlank() } ?: "—")
            }
        }
    }
}

// ────────────────────── 13. Administrative information ─────────────────────

@Composable
private fun AdministrativeInformation(p: StudentProfileDto) {
    val s = p.student
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ADMINISTRATIVE INFORMATION")
        VCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DetailRow(VIcons.Bookmark, "Admission Number", s.studentCode)
                DetailRow(VIcons.Calendar, "Admission Date", p.admissionDate?.takeIf { it.isNotBlank() } ?: "—")
                DetailRow(VIcons.User, "Student ID", s.id)
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

// ─────────────────────────────── Danger zone ──────────────────────────────

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
            Text("Remove student", style = VTheme.type.bodyStrong.colored(c.dangerInk))
            Spacer(Modifier.height(4.dp))
            Text(
                "Removing this student hides their records from your school. This can be reversed by re-adding them.",
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
