package com.littlebridge.enrollplus.ui.v2.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.teacher.presentation.AttendanceStatus
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherAttendanceState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherAttendanceViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * TeacherAttendanceScreenV2 — `Teacher.tsx → AttendanceFlow`, wired to the real
 * [TeacherAttendanceViewModel] (`TeacherRepository.getAttendance` / `submitAttendance`).
 *
 * Class/date selectors, "Mark all present", per-student P/A/L pill row (live edits via the VM),
 * and a sticky submit summary. No MockV2 in production; the three UI states come from [VStateHost].
 * When [classId] is blank the screen shows an empty prompt (selection lands with Phase 3E nav).
 */
@Composable
fun TeacherAttendanceScreenV2(
    classId: String = "",
    date: String = "",
    modifier: Modifier = Modifier,
    viewModel: TeacherAttendanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    // The portal supplies class/date; load whenever they change and are present.
    LaunchedEffect(classId, date) {
        if (classId.isNotBlank()) viewModel.load(classId, date)
    }

    TeacherAttendanceContent(
        state = state,
        hasSelection = classId.isNotBlank(),
        onMarkAll = { viewModel.markAll(AttendanceStatus.PRESENT) },
        onSetStatus = viewModel::setStatus,
        onSubmit = viewModel::submit,
        onRetry = { if (classId.isNotBlank()) viewModel.load(classId, date) },
        modifier = modifier,
    )
}

@Composable
private fun TeacherAttendanceContent(
    state: TeacherAttendanceState,
    hasSelection: Boolean,
    onMarkAll: () -> Unit,
    onSetStatus: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VInput(value = state.className.ifBlank { "Select class" }, onValueChange = {}, modifier = Modifier.weight(1f), enabled = false)
            VInput(value = state.date.ifBlank { "Today" }, onValueChange = {}, modifier = Modifier.weight(1f), enabled = false)
        }

        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = !hasSelection || state.students.isEmpty(),
            emptyTitle = if (hasSelection) "No students" else "Choose a class",
            emptyBody = if (hasSelection) "This class has no students to mark yet."
            else "Pick a class to start marking attendance.",
            emptyIcon = VIcons.Users,
            onRetry = onRetry,
        ) {
            VButton(text = "Mark all present", onClick = onMarkAll, full = true, variant = VButtonVariant.Secondary)
            VCard {
                state.students.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VAvatar(name = s.name, size = 32.dp)
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = VTheme.type.bodyStrong.colored(c.ink).copy(fontSize = 13.sp))
                            Text("Roll ${s.rollNo}", style = VTheme.type.dataSm.colored(c.ink3).copy(fontSize = 11.sp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            PalPill("P", s.status == AttendanceStatus.PRESENT, c.success) { onSetStatus(s.studentId, AttendanceStatus.PRESENT) }
                            PalPill("A", s.status == AttendanceStatus.ABSENT, c.danger) { onSetStatus(s.studentId, AttendanceStatus.ABSENT) }
                            PalPill("L", s.status == AttendanceStatus.LATE, c.warning) { onSetStatus(s.studentId, AttendanceStatus.LATE) }
                        }
                    }
                }
            }
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    val marked = state.presentCount + state.absentCount + state.lateCount
                    val total = state.students.size
                    val remaining = (total - marked).coerceAtLeast(0)
                    val pct = if (total == 0) 0f else marked.toFloat() / total * 100f
                    Column(Modifier.weight(1f)) {
                        Text("$marked marked • $remaining remaining", style = VTheme.type.caption.colored(c.ink2))
                        Spacer(Modifier.height(4.dp))
                        VProgressBar(value = pct)
                    }
                    Spacer(Modifier.height(0.dp))
                    // RA-S18: result-driven Submit. No fake `stateful` timer — the spinner shows
                    // only while the request is in flight (`isSubmitting`) and the "Submitted" check
                    // shows only after the VM confirms the write (`submitSuccess`). Disabled while
                    // submitting or after a confirmed save (re-enables when the roster is edited).
                    VButton(
                        text = if (state.submitSuccess) "Submitted" else "Submit",
                        onClick = onSubmit,
                        size = VButtonSize.Lg,
                        tone = VButtonTone.Lavender,
                        loading = state.isSubmitting,
                        success = state.submitSuccess,
                        enabled = !state.isSubmitting && !state.submitSuccess,
                        successLabel = "Submitted",
                    )
                }
            }
            // RA-S18: surface a submit error inline (the VStateHost error channel covers the
            // load path; a failed submit must not silently look like nothing happened, nor wipe
            // the marked roster the teacher is mid-way through).
            state.submitError?.let { err ->
                Text(
                    err,
                    style = VTheme.type.caption.colored(c.danger),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PalPill(letter: String, active: Boolean, tone: Color, onClick: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) tone else c.ink.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = VTheme.type.label.colored(if (active) c.background else c.ink3).copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = FontWeight.SemiBold))
    }
}
