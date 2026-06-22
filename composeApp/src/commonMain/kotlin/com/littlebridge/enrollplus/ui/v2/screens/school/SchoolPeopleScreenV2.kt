package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherCardDto
import com.littlebridge.enrollplus.feature.admin.presentation.RiskStudent
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolTeachersState
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolTeachersViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.StaffRosterState
import com.littlebridge.enrollplus.feature.admin.presentation.StaffViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.StudentAnalyticsState
import com.littlebridge.enrollplus.feature.admin.presentation.StudentAnalyticsViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.StudentRosterState
import com.littlebridge.enrollplus.feature.admin.presentation.StudentRosterViewModel
import com.littlebridge.enrollplus.ui.v2.components.VActionCard
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.components.VStatusDot
import com.littlebridge.enrollplus.ui.v2.components.VTopTabs
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.staggeredItemEntrance
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * SchoolPeopleScreenV2 — RA-S17 rebuild.
 *
 * The People tab is now a [VTopTabs]-driven 3-sub-tab surface — **Teachers /
 * Students / Non-teaching staff** — each with a search field and tappable,
 * DB-backed rows that open the person's profile. Deletion has been removed from
 * the rows entirely: it now lives inside each profile behind a confirm dialog
 * (RA-S17 directive). The parent→child link-request queue stays as a top entry.
 *
 * Data: teachers via [SchoolTeachersViewModel] (`/school/teachers`), students via
 * [StudentRosterViewModel] (`/school/students?q=&class=`), staff via [StaffViewModel]
 * (`/school/staff?q=&department=`), cohort analytics via [StudentAnalyticsViewModel]
 * (`/student-cohort`). All three states come from [VStateHost] (LAW 2/3/6); no MockV2.
 */
@Composable
fun SchoolPeopleScreenV2(
    modifier: Modifier = Modifier,
    onOpenLinkRequests: () -> Unit = {},
    onOpenStudent: (String) -> Unit = {},
    onOpenTeacher: (String) -> Unit = {},
    // RA-TAM — overflow "Assign classes" opens the reusable assignment module.
    onAssignClasses: (String) -> Unit = {},
    onOpenStaff: (String) -> Unit = {},
    viewModel: StudentAnalyticsViewModel = koinViewModel(),
    teachersViewModel: SchoolTeachersViewModel = koinViewModel(),
    studentsViewModel: StudentRosterViewModel = koinViewModel(),
    staffViewModel: StaffViewModel = koinViewModel(),
    teacherRefreshKey: Int,
    studentRefreshKey: Int,
) {
    val analyticsState by viewModel.state.collectAsStateV2()
    val teachersState by teachersViewModel.state.collectAsStateV2()
    val studentsState by studentsViewModel.state.collectAsStateV2()
    val staffState by staffViewModel.state.collectAsStateV2()

    LaunchedEffect(teacherRefreshKey){
        teachersViewModel.load()
    }
    LaunchedEffect(studentRefreshKey){
        studentsViewModel.load()
    }
    SchoolPeopleContent(
        analyticsState = analyticsState,
        onAnalyticsRetry = viewModel::load,
        teachersState = teachersState,
        onTeachersRetry = teachersViewModel::load,
        onAddTeacher = teachersViewModel::addTeacher,
        onLoadMoreTeachers = teachersViewModel::loadMore,
        onDeactivateTeacher = teachersViewModel::removeTeacher,
        studentsState = studentsState,
        onStudentsRetry = studentsViewModel::load,
        onStudentSearch = { studentsViewModel.load() }, // students VM reloads full list; client-side filter below
        onAddStudent = studentsViewModel::addStudent,
        onImportStudentsCsv = studentsViewModel::importStudentsCsv,
        onClearStudentMessages = studentsViewModel::clearMessages,
        staffState = staffState,
        onStaffRetry = staffViewModel::load,
        onStaffSearch = staffViewModel::onQueryChange,
        onAddStaff = staffViewModel::addStaff,
        onOpenLinkRequests = onOpenLinkRequests,
        onOpenStudent = onOpenStudent,
        onOpenTeacher = onOpenTeacher,
        onAssignClasses = onAssignClasses,
        onOpenStaff = onOpenStaff,
        modifier = modifier,
    )
}

@Composable
private fun SchoolPeopleContent(
    analyticsState: StudentAnalyticsState,
    onAnalyticsRetry: () -> Unit,
    teachersState: SchoolTeachersState,
    onTeachersRetry: () -> Unit,
    onAddTeacher: (name: String, identifier: String, initialPassword: String?, onAdded: (() -> Unit)?) -> Unit,
    onLoadMoreTeachers: () -> Unit,
    onDeactivateTeacher: (String) -> Unit,
    studentsState: StudentRosterState,
    onStudentsRetry: () -> Unit,
    onStudentSearch: (String) -> Unit,
    onAddStudent: (name: String, className: String, section: String, rollNumber: String) -> Unit,
    onImportStudentsCsv: (String) -> Unit,
    onClearStudentMessages: () -> Unit,
    staffState: StaffRosterState,
    onStaffRetry: () -> Unit,
    onStaffSearch: (String) -> Unit,
    onAddStaff: (name: String, role: String, department: String, phone: String, email: String) -> Unit,
    onOpenLinkRequests: () -> Unit,
    onOpenStudent: (String) -> Unit,
    onOpenTeacher: (String) -> Unit,
    onAssignClasses: (String) -> Unit,
    onOpenStaff: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors

    var subTab by remember { mutableStateOf("Teachers") }
    var showAddTeacher by remember { mutableStateOf(false) }
    var showAddStaff by remember { mutableStateOf(false) }
    var showAddStudent by remember { mutableStateOf(false) }
    var showImportStudents by remember { mutableStateOf(false) }

    // Auto-close the student dialogs once the VM confirms success.
    LaunchedEffect(studentsState.infoMessage) {
        val msg = studentsState.infoMessage
        if (msg != null && (msg == "Student added" || msg.startsWith("Imported"))) {
            showAddStudent = false
            showImportStudents = false
            onClearStudentMessages()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(top = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("People", style = VTheme.type.h1.colored(c.ink), modifier = Modifier.padding(horizontal = 20.dp))

        VActionCard(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "Child link requests",
            subtitle = "Review parents requesting access to student records",
            icon = VIcons.Plus,
            onClick = onOpenLinkRequests,
        )

        // ── RA-S17: sub-tabs ─────────────────────────────────────────────────
        VTopTabs(
            tabs = listOf("Teachers", "Students", "Non-teaching staff"),
            selected = subTab,
            onSelect = { subTab = it },
        )

        Column(
            Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (subTab) {
                "Teachers" -> TeachersSubTab(
                    state = teachersState,
                    onRetry = onTeachersRetry,
                    onAddClick = { showAddTeacher = true },
                    onOpenTeacher = onOpenTeacher,
                    onLoadMore = onLoadMoreTeachers,
                    onDeactivate = onDeactivateTeacher,
                    // RA-TAM — "Assign classes" now opens the reusable Teacher
                    // Assignment Management module (one of its 3 entry points).
                    onAssignClass = onAssignClasses,
                )
                "Students" -> StudentsSubTab(
                    state = studentsState,
                    onRetry = onStudentsRetry,
                    onOpenStudent = onOpenStudent,
                    onAddClick = { showAddStudent = true },
                    onImportClick = { showImportStudents = true },
                    analyticsState = analyticsState,
                    onAnalyticsRetry = onAnalyticsRetry,
                )
                "Non-teaching staff" -> StaffSubTab(
                    state = staffState,
                    onRetry = onStaffRetry,
                    onSearch = onStaffSearch,
                    onAddClick = { showAddStaff = true },
                    onOpenStaff = onOpenStaff,
                )
            }
        }
    }

    // ── Add-teacher dialog (RA-22) ─────────────────────────────────────────
    if (showAddTeacher) {
        AddTeacherDialog(
            isSubmitting = teachersState.isMutating,
            onDismiss = { showAddTeacher = false },
            onSubmit = { name, identifier, password ->
                onAddTeacher(name, identifier, password) { showAddTeacher = false }
            },
        )
    }

    // ── Add-staff dialog (RA-S17) ──────────────────────────────────────────
    if (showAddStaff) {
        AddStaffDialog(
            isSubmitting = staffState.isSaving,
            onDismiss = { showAddStaff = false },
            onSubmit = { name, role, dept, phone, email ->
                onAddStaff(name, role, dept, phone, email)
                showAddStaff = false
            },
        )
    }

    // ── Add-student dialog (manual single add) ─────────────────────────────
    if (showAddStudent) {
        AddStudentPeopleDialog(
            isSubmitting = studentsState.isSaving,
            error = studentsState.addError,
            onDismiss = { showAddStudent = false; onClearStudentMessages() },
            onSubmit = { name, cls, sec, roll -> onAddStudent(name, cls, sec, roll) },
        )
    }

    // ── Import-students dialog (CSV / paste) ───────────────────────────────
    if (showImportStudents) {
        ImportStudentsDialog(
            isSubmitting = studentsState.isImporting,
            error = studentsState.importError,
            onDismiss = { showImportStudents = false; onClearStudentMessages() },
            onSubmit = { csv -> onImportStudentsCsv(csv) },
        )
    }
}

// ───────────────────────── Teachers sub-tab ─────────────────────────

@Composable
private fun TeachersSubTab(
    state: SchoolTeachersState,
    onRetry: () -> Unit,
    onAddClick: () -> Unit,
    onOpenTeacher: (String) -> Unit,
    onLoadMore: () -> Unit,
    onDeactivate: (String) -> Unit,
    onAssignClass: (String) -> Unit,
) {
    val c = VTheme.colors
    var query by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Teachers", style = VTheme.type.h3.colored(c.ink))
        VButton(
            text = "Add teacher",
            onClick = onAddClick,
            variant = VButtonVariant.Secondary,
            size = VButtonSize.Sm,
            leading = { Icon(VIcons.Plus, contentDescription = null, modifier = Modifier.size(14.dp)) },
            enabled = !state.isMutating,
        )
    }
    VInput(
        value = query,
        onValueChange = { query = it },
        label = "",
        placeholder = "Search by name, role or subject",
        leadingIcon = VIcons.Search,
        modifier = Modifier.fillMaxWidth(),
    )

    // Card DTOs have no phone/contact field — search the human-facing summary
    // instead: name, role label, assigned subjects and grades.
    val filtered = state.teachers.filter { t ->
        query.isBlank() ||
            t.profile.name.contains(query, ignoreCase = true) ||
            t.profile.role.contains(query, ignoreCase = true) ||
            t.academicAssignment.subjects.any { it.contains(query, ignoreCase = true) } ||
            t.academicAssignment.grades.any { it.contains(query, ignoreCase = true) }
    }

    VStateHost(
        loading = state.isLoading,
        error = state.errorMessage,
        isEmpty = filtered.isEmpty(),
        emptyTitle = if (state.teachers.isEmpty()) "No teachers yet" else "No matches",
        emptyBody = if (state.teachers.isEmpty())
            "Add your first teacher so they can sign in and manage their classes."
        else "No teacher matches \"$query\".",
        emptyIcon = VIcons.Users,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 5) },
    ) {
        val ready = filtered.isNotEmpty() && !state.isLoading
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            filtered.forEachIndexed { index, t ->
                Box(modifier = Modifier.staggeredItemEntrance(index = index, trigger = ready)) {
                    TeacherCard(
                        teacher = t,
                        isMutating = state.isMutating,
                        onViewProfile = { onOpenTeacher(t.id) },
                        onDeactivate = { onDeactivate(t.id) },
                        onAssignClass = { onAssignClass(t.id) },
                    )
                }
            }

            // Pagination: only meaningful when NOT filtering locally (a local
            // search filters just the loaded page; loading more would surprise).
            if (query.isBlank() && state.hasNext) {
                VButton(
                    text = if (state.isLoadingMore) "Loading…" else "Load more",
                    onClick = onLoadMore,
                    variant = VButtonVariant.Ghost,
                    size = VButtonSize.Sm,
                    full = true,
                    enabled = !state.isLoadingMore && !state.isLoading,
                    loading = state.isLoadingMore,
                )
            }
        }
    }
}

/**
 * Teacher summary CARD — the redesigned School-Admin teacher list row. Every
 * card is self-contained: header (avatar + name + role + status), academic
 * assignment (grades / subjects), workload (classes / students), activity
 * (attendance % + last active), and a footer with View Profile plus an overflow
 * menu. Every action is driven by the backend `actions` flags — nothing is
 * hardcoded — and every data section degrades gracefully when empty.
 */
@Composable
private fun TeacherCard(
    teacher: TeacherCardDto,
    isMutating: Boolean,
    onViewProfile: () -> Unit,
    onDeactivate: () -> Unit,
    onAssignClass: () -> Unit,
) {
    val c = VTheme.colors
    val isActive = teacher.profile.status.equals("ACTIVE", ignoreCase = true)
    var menuOpen by remember { mutableStateOf(false) }
    // The overflow menu only has content when the backend grants at least one
    // overflow action; otherwise we hide the ⋮ entirely.
    val hasOverflow = teacher.actions.canAssignClass || teacher.actions.canDeactivate

    VCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ── Header: avatar · name · role · status ──────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VAvatar(
                    name = teacher.profile.name,
                    src = teacher.profile.avatarUrl?.takeIf { it.isNotBlank() },
                    size = 46.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        teacher.profile.name.ifBlank { "Unnamed teacher" },
                        style = VTheme.type.bodyStrong.colored(c.ink),
                    )
                    if (teacher.profile.role.isNotBlank()) {
                        Text(teacher.profile.role, style = VTheme.type.caption.colored(c.ink2))
                    }
                }
                VBadge(
                    text = if (isActive) "Active" else "Inactive",
                    tone = if (isActive) VBadgeTone.Success else VBadgeTone.Neutral,
                )
            }

            CardDivider()

            // ── Academic assignment: grades + subjects ─────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledChipsRow(
                    label = "Grades",
                    values = teacher.academicAssignment.grades,
                    emptyText = "No grades assigned",
                )
                LabeledChipsRow(
                    label = "Subjects",
                    values = teacher.academicAssignment.subjects,
                    emptyText = "No subjects assigned",
                )
            }

            CardDivider()

            // ── Workload: classes + students (side by side) ────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkloadStat(
                    label = "Classes",
                    value = teacher.workload.totalClasses.toString(),
                    icon = VIcons.BookOpen,
                    modifier = Modifier.weight(1f),
                )
                WorkloadStat(
                    label = "Students",
                    value = teacher.workload.totalStudents.toString(),
                    icon = VIcons.Users,
                    modifier = Modifier.weight(1f),
                )
            }

            CardDivider()

            // ── Activity: attendance % + last active ───────────────────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(VIcons.TrendingUp, contentDescription = null, tint = c.ink3, modifier = Modifier.size(14.dp))
                    Text(
                        teacher.activity.attendancePercentage
                            ?.let { "Attendance $it%" }
                            ?: "Attendance —",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(VIcons.Clock, contentDescription = null, tint = c.ink3, modifier = Modifier.size(14.dp))
                    Text(
                        lastActiveLabel(teacher.activity.lastActiveAt),
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
            }

            // ── Actions: View Profile + overflow (backend-driven) ──────────
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (teacher.actions.canViewProfile) {
                    VButton(
                        text = "View Profile",
                        onClick = onViewProfile,
                        variant = VButtonVariant.Secondary,
                        size = VButtonSize.Sm,
                        modifier = Modifier.weight(1f),
                        leading = { Icon(VIcons.Eye, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (hasOverflow) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.cream)
                                .clickable(enabled = !isMutating) { menuOpen = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(VIcons.More, contentDescription = "More actions", tint = c.ink2, modifier = Modifier.size(18.dp))
                        }
                        // Overflow items are strictly backend-driven by the
                        // `actions` flags — nothing here is hardcoded. Both map to
                        // existing flows (assign-class lives in the profile;
                        // deactivate reuses the soft-delete endpoint).
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (teacher.actions.canAssignClass) {
                                DropdownMenuItem(
                                    text = { Text("Assign classes") },
                                    onClick = { menuOpen = false; onAssignClass() },
                                    leadingIcon = { Icon(VIcons.GraduationCap, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                )
                            }
                            if (teacher.actions.canDeactivate) {
                                DropdownMenuItem(
                                    text = { Text("Deactivate", style = VTheme.type.body.colored(c.dangerInk)) },
                                    onClick = { menuOpen = false; onDeactivate() },
                                    leadingIcon = { Icon(VIcons.Close, contentDescription = null, tint = c.dangerInk, modifier = Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Thin hairline divider used between a teacher card's sections. */
@Composable
private fun CardDivider() {
    val c = VTheme.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.ink.copy(alpha = 0.06f)))
}

/**
 * A labelled wrapped row of value "chips" (e.g. Grades / Subjects). Renders
 * [emptyText] in a muted tone when [values] is empty so an unassigned teacher
 * still reads cleanly.
 */
@Composable
private fun LabeledChipsRow(
    label: String,
    values: List<String>,
    emptyText: String,
) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        if (values.isEmpty()) {
            Text(emptyText, style = VTheme.type.caption.colored(c.ink3))
        } else {
            // Bullet-joined to mirror the "Grade 6 • Grade 7" mock and avoid a
            // new FlowRow dependency (keeps to existing layout primitives).
            Text(
                values.joinToString("  •  "),
                style = VTheme.type.body.colored(c.ink),
            )
        }
    }
}

/** A single workload stat tile (big number + caption + icon). */
@Composable
private fun WorkloadStat(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.ink.copy(alpha = 0.05f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(14.dp))
            Text(label, style = VTheme.type.label.colored(c.ink3))
        }
        Text(value, style = VTheme.type.dataLg.colored(c.ink).copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
    }
}

/**
 * Humanise the ISO-8601 lastActiveAt into a short label. We intentionally keep
 * this dependency-free (no kotlinx-datetime parsing here): a null/blank value
 * → "Never active"; otherwise we surface the calendar date portion, falling
 * back to the raw value if it is not in the expected shape.
 */
private fun lastActiveLabel(iso: String?): String {
    if (iso.isNullOrBlank()) return "Never active"
    // ISO-8601 UTC like "2026-06-16T09:30:00Z" → "Active 2026-06-16".
    val datePart = iso.substringBefore('T').takeIf { it.length == 10 && it.count { ch -> ch == '-' } == 2 }
    return datePart?.let { "Active $it" } ?: "Active"
}

// ───────────────────────── Students sub-tab ─────────────────────────

@Composable
private fun StudentsSubTab(
    state: StudentRosterState,
    onRetry: () -> Unit,
    onOpenStudent: (String) -> Unit,
    onAddClick: () -> Unit,
    onImportClick: () -> Unit,
    analyticsState: StudentAnalyticsState,
    onAnalyticsRetry: () -> Unit,
) {
    val c = VTheme.colors
    var query by remember { mutableStateOf("") }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Students", style = VTheme.type.h3.colored(c.ink))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(
                text = "Import CSV",
                onClick = onImportClick,
                variant = VButtonVariant.Ghost,
                size = VButtonSize.Sm,
                leading = { Icon(VIcons.Upload, contentDescription = null, modifier = Modifier.size(14.dp)) },
                enabled = !state.isImporting && !state.isSaving,
            )
            VButton(
                text = "Add student",
                onClick = onAddClick,
                variant = VButtonVariant.Secondary,
                size = VButtonSize.Sm,
                leading = { Icon(VIcons.Plus, contentDescription = null, modifier = Modifier.size(14.dp)) },
                enabled = !state.isSaving && !state.isImporting,
            )
        }
    }
    VInput(
        value = query,
        onValueChange = { query = it },
        label = "",
        placeholder = "Search by name, roll no. or code",
        leadingIcon = VIcons.Search,
        modifier = Modifier.fillMaxWidth(),
    )

    val filtered = state.students.filter {
        query.isBlank() ||
            it.fullName.contains(query, ignoreCase = true) ||
            it.rollNumber.contains(query, ignoreCase = true) ||
            it.studentCode.contains(query, ignoreCase = true) ||
            it.className.contains(query, ignoreCase = true)
    }

    VStateHost(
        loading = state.isLoading,
        error = state.error,
        isEmpty = filtered.isEmpty(),
        emptyTitle = if (state.students.isEmpty()) "No students yet" else "No matches",
        emptyBody = if (state.students.isEmpty())
            "Students appear here once they are enrolled in your school."
        else "No student matches \"$query\".",
        emptyIcon = VIcons.Users,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 6) },
    ) {
        val ready = filtered.isNotEmpty() && !state.isLoading
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filtered.forEachIndexed { index, s ->
                Box(modifier = Modifier.staggeredItemEntrance(index = index, trigger = ready)) {
                    PersonRow(
                        name = s.fullName,
                        subtitle = "${s.className} · Sec ${s.section} · Roll ${s.rollNumber}",
                        src = s.profilePhotoUrl,
                        onClick = { onOpenStudent(s.id) },
                    )
                }
            }
        }
    }

    // ── Cohort analytics (kept under Students) ──────────────────────────────
    Spacer(Modifier.height(8.dp))
    Text("Cohort analytics", style = VTheme.type.h3.colored(c.ink))
    VStateHost(
        loading = analyticsState.isLoading,
        error = analyticsState.errorMessage,
        isEmpty = analyticsState.atRiskStudents.isEmpty() &&
            analyticsState.subjectEngagements.isEmpty() &&
            analyticsState.criticalRiskCount == 0 &&
            analyticsState.mediumRiskCount == 0 &&
            analyticsState.lowRiskCount == 0,
        emptyTitle = "No cohort data yet",
        emptyBody = "Student risk and engagement analytics appear here once attendance and marks start flowing in.",
        emptyIcon = VIcons.Users,
        onRetry = onAnalyticsRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 4) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VCard {
                VLabel("Student risk distribution")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RiskTile("Critical", analyticsState.criticalRiskCount, c.dangerInk, Modifier.weight(1f))
                    RiskTile("Medium", analyticsState.mediumRiskCount, c.warningInk, Modifier.weight(1f))
                    RiskTile("Low", analyticsState.lowRiskCount, c.successInk, Modifier.weight(1f))
                }
            }
            if (analyticsState.atRiskStudents.isNotEmpty()) {
                Column {
                    Text("At-risk students", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        analyticsState.atRiskStudents.forEach { RiskStudentRow(it) }
                    }
                }
            }
            if (analyticsState.subjectEngagements.isNotEmpty()) {
                VCard {
                    Text("Subject engagement", style = VTheme.type.h3.colored(c.ink))
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        analyticsState.subjectEngagements.forEach { e ->
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(e.name, style = VTheme.type.body.colored(c.ink))
                                    Text("${e.percentage.roundToInt()}%", style = VTheme.type.dataSm.colored(c.ink2))
                                }
                                Spacer(Modifier.height(4.dp))
                                VProgressBar(
                                    value = e.percentage,
                                    tone = if (e.percentage < 60f) VBadgeTone.Warning else VBadgeTone.Arctic,
                                )
                                val status = e.status
                                if (!status.isNullOrBlank()) {
                                    Text(status, style = VTheme.type.label.colored(c.ink3))
                                }
                            }
                        }
                    }
                }
            }
            if (analyticsState.cohortComparison.isNotEmpty()) {
                VCard {
                    Text("Cohort comparison", style = VTheme.type.h3.colored(c.ink))
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        analyticsState.cohortComparison.forEachIndexed { i, v ->
                            val label = analyticsState.cohortLabels.getOrNull(i) ?: "Grade ${i + 1}"
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, style = VTheme.type.body.colored(c.ink))
                                    Text("${v.roundToInt()}%", style = VTheme.type.dataSm.colored(c.ink2))
                                }
                                Spacer(Modifier.height(4.dp))
                                VProgressBar(value = v, tone = VBadgeTone.Arctic)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────── Non-teaching-staff sub-tab ───────────────────────

@Composable
private fun StaffSubTab(
    state: StaffRosterState,
    onRetry: () -> Unit,
    onSearch: (String) -> Unit,
    onAddClick: () -> Unit,
    onOpenStaff: (String) -> Unit,
) {
    val c = VTheme.colors

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Non-teaching staff", style = VTheme.type.h3.colored(c.ink))
        VButton(
            text = "Add staff",
            onClick = onAddClick,
            variant = VButtonVariant.Secondary,
            size = VButtonSize.Sm,
            leading = { Icon(VIcons.Plus, contentDescription = null, modifier = Modifier.size(14.dp)) },
            enabled = !state.isSaving,
        )
    }
    VInput(
        value = state.query,
        onValueChange = onSearch,
        label = "",
        placeholder = "Search by name, role or department",
        leadingIcon = VIcons.Search,
        modifier = Modifier.fillMaxWidth(),
    )

    VStateHost(
        loading = state.isLoading,
        error = state.error,
        isEmpty = state.staff.isEmpty(),
        emptyTitle = if (state.query.isBlank()) "No staff yet" else "No matches",
        emptyBody = if (state.query.isBlank())
            "Add office, accounts, library, transport or support staff so they appear here."
        else "No staff matches \"${state.query}\".",
        emptyIcon = VIcons.Users,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 5) },
    ) {
        val ready = state.staff.isNotEmpty() && !state.isLoading
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.staff.forEachIndexed { index, s ->
                Box(modifier = Modifier.staggeredItemEntrance(index = index, trigger = ready)) {
                    PersonRow(
                        name = s.fullName,
                        subtitle = listOfNotNull(s.role, s.department?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        src = s.photoUrl,
                        onClick = { onOpenStaff(s.id) },
                    )
                }
            }
        }
    }
}

// ───────────────────────────── shared row ─────────────────────────────

/**
 * RA-S17: a tap-to-open person row. There is intentionally **no** delete button
 * here — deletion lives inside the profile behind a confirm dialog.
 */
@Composable
private fun PersonRow(
    name: String,
    subtitle: String,
    src: String? = null,
    onClick: () -> Unit,
) {
    val c = VTheme.colors
    VCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VAvatar(name = name, src = src, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Text(name, style = VTheme.type.bodyStrong.colored(c.ink))
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = VTheme.type.caption.colored(c.ink2))
                }
            }
            Icon(VIcons.ArrowRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
        }
    }
}

// ───────────────────────────── dialogs ─────────────────────────────

/**
 * RA-22: add-teacher form. A teacher is provisioned by email (with an initial
 * password) or by phone (OTP login). Frozen primitives only.
 */
@Composable
private fun AddTeacherDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, identifier: String, initialPassword: String?) -> Unit,
) {
    val c = VTheme.colors
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val isEmail = identifier.contains("@")
    val canSubmit = name.isNotBlank() &&
        identifier.isNotBlank() &&
        (!isEmail || password.isNotBlank()) &&
        !isSubmitting

    Dialog(onDismissRequest = onDismiss) {
        VCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add teacher", style = VTheme.type.h3.colored(c.ink))
                VInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full name",
                    placeholder = "e.g. Asha Verma",
                    leadingIcon = VIcons.User,
                )
                VInput(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = "Email or phone",
                    placeholder = "teacher@school.edu or 98765 43210",
                    leadingIcon = if (isEmail) VIcons.Mail else VIcons.Phone,
                    keyboardType = if (isEmail) KeyboardType.Email else KeyboardType.Text,
                )
                if (isEmail) {
                    VInput(
                        value = password,
                        onValueChange = { password = it },
                        label = "Initial password",
                        placeholder = "Shared with the teacher to sign in",
                        leadingIcon = VIcons.Lock,
                        isPassword = true,
                    )
                } else {
                    Text(
                        "This teacher will sign in with a one-time code sent to their phone.",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
                Spacer(Modifier.height(4.dp))
                VButton(
                    text = "Add teacher",
                    onClick = {
                        onSubmit(name, identifier, password.takeIf { isEmail && it.isNotBlank() })
                    },
                    variant = VButtonVariant.Primary,
                    full = true,
                    enabled = canSubmit,
                    loading = isSubmitting,
                )
                VButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = VButtonVariant.Ghost,
                    full = true,
                    enabled = !isSubmitting,
                )
            }
        }
    }
}

/**
 * RA-S17: add-staff form for a non-teaching-staff member. Name + role required;
 * department / phone / email optional. Frozen primitives only.
 */
@Composable
private fun AddStaffDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, role: String, department: String, phone: String, email: String) -> Unit,
) {
    val c = VTheme.colors
    var name by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    val canSubmit = name.isNotBlank() && role.isNotBlank() && !isSubmitting

    Dialog(onDismissRequest = onDismiss) {
        VCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Add staff member", style = VTheme.type.h3.colored(c.ink))
                VInput(
                    value = name,
                    onValueChange = { name = it },
                    label = "Full name",
                    placeholder = "e.g. Ramesh Kumar",
                    leadingIcon = VIcons.User,
                )
                VInput(
                    value = role,
                    onValueChange = { role = it },
                    label = "Role",
                    placeholder = "e.g. Accountant, Librarian, Security",
                    leadingIcon = VIcons.User,
                )
                VInput(
                    value = department,
                    onValueChange = { department = it },
                    label = "Department (optional)",
                    placeholder = "e.g. Office, Transport",
                    leadingIcon = VIcons.Bookmark,
                )
                VInput(
                    value = phone,
                    onValueChange = { phone = it },
                    label = "Phone (optional)",
                    placeholder = "98765 43210",
                    leadingIcon = VIcons.Phone,
                    keyboardType = KeyboardType.Phone,
                )
                VInput(
                    value = email,
                    onValueChange = { email = it },
                    label = "Email (optional)",
                    placeholder = "staff@school.edu",
                    leadingIcon = VIcons.Mail,
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(4.dp))
                VButton(
                    text = "Add staff",
                    onClick = { onSubmit(name, role, department, phone, email) },
                    variant = VButtonVariant.Primary,
                    full = true,
                    enabled = canSubmit,
                    loading = isSubmitting,
                )
                VButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = VButtonVariant.Ghost,
                    full = true,
                    enabled = !isSubmitting,
                )
            }
        }
    }
}

// ───────────────────────── Student add / import dialogs ─────────────────────

/** Manual single-student add from the People → Students sub-tab. */
@Composable
private fun AddStudentPeopleDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, className: String, section: String, rollNumber: String) -> Unit,
) {
    val c = VTheme.colors
    var name by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var section by remember { mutableStateOf("") }
    var roll by remember { mutableStateOf("") }

    val canSubmit = name.isNotBlank() && className.isNotBlank() && roll.isNotBlank() && !isSubmitting

    Dialog(onDismissRequest = onDismiss) {
        VCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add student", style = VTheme.type.h3.colored(c.ink))
                VInput(name, { name = it }, label = "Full name", placeholder = "e.g. Aarav Sharma", leadingIcon = VIcons.User)
                VInput(className, { className = it }, label = "Class", placeholder = "e.g. Grade 4")
                VInput(section, { section = it }, label = "Section", placeholder = "A")
                VInput(roll, { roll = it }, label = "Roll number", placeholder = "e.g. 12", keyboardType = KeyboardType.Number)
                if (error != null) {
                    Text(error, style = VTheme.type.body.colored(c.dangerInk))
                }
                Spacer(Modifier.height(2.dp))
                VButton(
                    text = "Add student",
                    onClick = { onSubmit(name, className, section, roll) },
                    variant = VButtonVariant.Primary,
                    full = true,
                    enabled = canSubmit,
                    loading = isSubmitting,
                )
                VButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = VButtonVariant.Ghost,
                    full = true,
                    enabled = !isSubmitting,
                )
            }
        }
    }
}

/**
 * Bulk CSV import. The admin pastes (or, on platforms with a file picker,
 * loads) CSV rows. The first line must be a header; accepted columns:
 *   full_name, class_name, roll_number, section, student_code
 * `section` and `student_code` are optional. Sent verbatim to
 * POST /api/v1/school/students/import, which parses + validates each row.
 */
@Composable
private fun ImportStudentsDialog(
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (csv: String) -> Unit,
) {
    val c = VTheme.colors
    var csv by remember {
        mutableStateOf("full_name,class_name,section,roll_number\n")
    }
    val canSubmit = csv.lineSequence().drop(1).any { it.isNotBlank() } && !isSubmitting

    Dialog(onDismissRequest = onDismiss) {
        VCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Import students (CSV)", style = VTheme.type.h3.colored(c.ink))
                Text(
                    "First row must be the header. Columns: full_name, class_name, " +
                        "roll_number (required); section, student_code (optional).",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                VInput(
                    value = csv,
                    onValueChange = { csv = it },
                    label = "CSV content",
                    placeholder = "full_name,class_name,section,roll_number\nAarav Sharma,Grade 4,A,12",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                )
                if (error != null) {
                    Text(error, style = VTheme.type.body.colored(c.dangerInk))
                }
                Spacer(Modifier.height(2.dp))
                VButton(
                    text = "Import",
                    onClick = { onSubmit(csv) },
                    variant = VButtonVariant.Primary,
                    full = true,
                    enabled = canSubmit,
                    loading = isSubmitting,
                )
                VButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = VButtonVariant.Ghost,
                    full = true,
                    enabled = !isSubmitting,
                )
            }
        }
    }
}

// ───────────────────────────── analytics bits ─────────────────────────────

@Composable
private fun RiskTile(label: String, count: Int, tone: Color, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.ink.copy(alpha = 0.06f)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), style = VTheme.type.dataLg.colored(tone).copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
        Text(label, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp))
    }
}

@Composable
private fun RiskStudentRow(s: RiskStudent) {
    val c = VTheme.colors
    val tone = when (s.riskLevel.lowercase()) {
        "critical" -> c.danger
        "medium" -> c.warning
        else -> c.success
    }
    val badgeTone = when (s.riskLevel.lowercase()) {
        "critical" -> VBadgeTone.Danger
        "medium" -> VBadgeTone.Warning
        else -> VBadgeTone.Success
    }
    VCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VAvatar(name = s.name, src = s.imageUrl.ifBlank { null }, size = 42.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VStatusDot(color = tone)
                    Text(s.name, style = VTheme.type.bodyStrong.colored(c.ink))
                }
                if (s.masteryTrend.isNotBlank()) {
                    Text("Mastery: ${s.masteryTrend}", style = VTheme.type.caption.colored(c.ink2))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                VBadge(text = s.riskLevel, tone = badgeTone)
                Text("${s.retentionRisk}% risk", style = VTheme.type.label.colored(c.ink3).copy(fontSize = 10.sp))
            }
        }
    }
}
