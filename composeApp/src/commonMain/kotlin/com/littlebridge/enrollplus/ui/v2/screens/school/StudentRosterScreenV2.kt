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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentDto
import com.littlebridge.enrollplus.feature.admin.presentation.StudentRosterState
import com.littlebridge.enrollplus.feature.admin.presentation.StudentRosterViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-45: StudentRosterScreenV2 — the admin's live student roster.
 *
 * Wired to [StudentRosterViewModel] (`GET /api/v1/school/students`, school-scoped).
 * Admin can add a student (`POST`) and remove one (`DELETE`, soft-delete, gated by
 * a confirm dialog). Tapping a row opens that student's profile. Three states via
 * [VStateHost] (LAW 3). Portal overlay — back returns to the People tab.
 */
@Composable
fun StudentRosterScreenV2(
    onBack: () -> Unit = {},
    onOpenStudent: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: StudentRosterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    var showAdd by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<StudentDto?>(null) }

    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(
            title = "Students",
            onBack = onBack,
            action = {
                VButton(
                    text = "Add",
                    onClick = { showAdd = true },
                    variant = VButtonVariant.Secondary,
                    size = VButtonSize.Sm,
                    enabled = !state.isSaving,
                )
            },
        )
        StudentRosterContent(
            state = state,
            onRetry = viewModel::load,
            onOpenStudent = onOpenStudent,
            onRemoveClick = { pendingRemoval = it },
            modifier = Modifier.fillMaxSize(),
        )
    }

    // Auto-close the add dialog once a student is successfully added.
    LaunchedEffect(state.infoMessage) {
        if (showAdd && state.infoMessage == "Student added") {
            showAdd = false
            viewModel.clearMessages()
        }
    }

    if (showAdd) {
        AddStudentDialog(
            isSubmitting = state.isSaving,
            error = state.addError,
            onDismiss = { showAdd = false; viewModel.clearMessages() },
            onSubmit = { name, cls, sec, roll -> viewModel.addStudent(name, cls, sec, roll) },
        )
    }

    val removal = pendingRemoval
    VConfirmDialog(
        visible = removal != null,
        title = "Remove student",
        message = "Remove ${removal?.fullName ?: "this student"} from the roster? " +
            "They will no longer appear in attendance or analytics. This can be reversed by re-adding them.",
        confirmLabel = "Remove",
        icon = VIcons.AlertTriangle,
        onConfirm = {
            removal?.let { viewModel.removeStudent(it.id) }
            pendingRemoval = null
        },
        onDismiss = { pendingRemoval = null },
    )
}

@Composable
private fun StudentRosterContent(
    state: StudentRosterState,
    onRetry: () -> Unit,
    onOpenStudent: (String) -> Unit,
    onRemoveClick: (StudentDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.students.isEmpty(),
            emptyTitle = "No students yet",
            emptyBody = "Add your first student so they appear in attendance, marks and analytics.",
            emptyIcon = VIcons.Users,
            onRetry = onRetry,
            skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 8) },
        ) {
            state.students.forEach { s ->
                StudentCard(
                    student = s,
                    removing = state.removingIds.contains(s.id),
                    onOpen = { onOpenStudent(s.id) },
                    onRemove = { onRemoveClick(s) },
                )
            }
        }
    }
}

/**
 * RA-SP: the modern, relationship-aware student card replacing the old plain
 * row. Shows avatar, name, class+section, status/new-admission/low-attendance
 * badges, then a metric strip (attendance %, parents linked, teacher count) and
 * an overflow menu exposing View Profile · Edit · Contact Parent · Remove.
 */
@Composable
private fun StudentCard(
    student: StudentDto,
    removing: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = VTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    val lowAttendance = student.attendancePercent in 0.1f..74.9f

    VCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VAvatar(name = student.fullName, src = student.profilePhotoUrl, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(student.fullName, style = VTheme.type.bodyStrong.colored(c.ink), maxLines = 1)
                Text(
                    "${student.className} · Sec ${student.section} · Roll ${student.rollNumber}",
                    style = VTheme.type.caption.colored(c.ink2),
                    maxLines = 1,
                )
            }
            // Overflow menu (quick actions).
            Box {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(c.ink.copy(alpha = 0.06f))
                        .clickable(enabled = !removing) { menuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.More, contentDescription = "Actions", tint = c.ink2, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("View Profile") },
                        onClick = { menuOpen = false; onOpen() },
                        leadingIcon = { Icon(VIcons.User, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { menuOpen = false; onOpen() },
                        leadingIcon = { Icon(VIcons.Settings, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Contact Parent") },
                        onClick = { menuOpen = false; onOpen() },
                        leadingIcon = { Icon(VIcons.Phone, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove", style = VTheme.type.body.colored(c.dangerInk)) },
                        onClick = { menuOpen = false; onRemove() },
                        leadingIcon = { Icon(VIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
        }

        // Status badges.
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VBadge(
                text = if (student.status.equals("active", ignoreCase = true)) "Active" else "Inactive",
                tone = if (student.status.equals("active", ignoreCase = true)) VBadgeTone.Success else VBadgeTone.Neutral,
            )
            if (student.isNewAdmission) {
                VBadge(text = "New Admission", tone = VBadgeTone.Arctic)
            }
            if (lowAttendance) {
                VBadge(text = "Low Attendance", tone = VBadgeTone.Warning)
            }
        }

        // Metric strip.
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricChip(
                icon = VIcons.Check,
                value = if (student.attendancePercent > 0f) "${student.attendancePercent.toInt()}%" else "—",
                label = "Attendance",
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                icon = VIcons.Heart,
                value = student.parentCount.toString(),
                label = "Parents",
                modifier = Modifier.weight(1f),
            )
            MetricChip(
                icon = VIcons.Users,
                value = student.teacherCount.toString(),
                label = "Teachers",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricChip(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(c.cream).padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(16.dp))
        Text(value, style = VTheme.type.bodyStrong.colored(c.ink))
        Text(label, style = VTheme.type.label.colored(c.ink3), maxLines = 1)
    }
}

/** RA-45: add-student form. Class + roll + name required; section defaults A. */
@Composable
private fun AddStudentDialog(
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
