package com.littlebridge.enrollplus.ui.v2.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherMarksState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherMarksViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * TeacherMarksScreenV2 — `Teacher.tsx → MarksFlow`, wired to the real [TeacherMarksViewModel]
 * (`TeacherRepository.getMarks` / `submitMarks`).
 *
 * Class/subject selectors, assessment header, per-student score fields (live edits via the VM),
 * a live class-avg footer, and a "Save marks" stateful button. No MockV2 in production; the three
 * UI states come from [VStateHost]. When [classId]/[examId] are blank an empty prompt is shown.
 */
@Composable
fun TeacherMarksScreenV2(
    classId: String = "",
    examId: String = "",
    modifier: Modifier = Modifier,
    viewModel: TeacherMarksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    LaunchedEffect(classId, examId) {
        if (classId.isNotBlank() && examId.isNotBlank()) viewModel.load(classId, examId)
    }

    TeacherMarksContent(
        state = state,
        hasSelection = classId.isNotBlank() && examId.isNotBlank(),
        onSetMark = viewModel::setMark,
        onSubmit = viewModel::submit,
        onRetry = { if (classId.isNotBlank() && examId.isNotBlank()) viewModel.load(classId, examId) },
        modifier = modifier,
    )
}

@Composable
private fun TeacherMarksContent(
    state: TeacherMarksState,
    hasSelection: Boolean,
    onSetMark: (String, Float?) -> Unit,
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
            VInput(value = state.subject.ifBlank { "Subject" }, onValueChange = {}, modifier = Modifier.weight(1f), enabled = false)
        }

        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = !hasSelection || state.students.isEmpty(),
            emptyTitle = if (hasSelection) "No students" else "Choose an assessment",
            emptyBody = if (hasSelection) "This assessment has no students to mark yet."
            else "Pick a class and exam to enter marks.",
            emptyIcon = VIcons.ListChecks,
            onRetry = onRetry,
        ) {
            VCard {
                VLabel("Assessment")
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(state.examName.ifBlank { "Assessment" }, style = VTheme.type.bodyStrong.colored(c.ink))
                        Text("Max ${state.maxMarks}", style = VTheme.type.dataSm.colored(c.ink2).copy(fontSize = 11.sp))
                    }
                }
            }
            VCard {
                state.students.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VAvatar(name = s.name, size = 30.dp)
                        Text(s.name, style = VTheme.type.body.colored(c.ink).copy(fontSize = 13.sp), modifier = Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(width = 80.dp, height = 40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, c.border2, RoundedCornerShape(6.dp)),
                        ) {
                            VInput(
                                value = s.marks?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() } ?: "",
                                onValueChange = { raw -> onSetMark(s.studentId, raw.trim().toFloatOrNull()) },
                                placeholder = "—",
                                keyboardType = KeyboardType.Number,
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    val avg = state.students.mapNotNull { it.marks }.takeIf { it.isNotEmpty() }?.average()
                    Text("Class avg (live) • ${state.enteredCount}/${state.students.size} entered", style = VTheme.type.caption.colored(c.ink2))
                    Text(avg?.roundToInt()?.toString() ?: "—", style = VTheme.type.data.colored(c.ink))
                }
            }
            // RA-S18: result-driven Save (same fix as teacher attendance). No fake `stateful`
            // timer — the spinner shows only while in flight (`isSubmitting`) and the "Saved"
            // check only after the VM confirms the write (`submitSuccess`). Disabled while
            // submitting or after a confirmed save (re-enables when a mark is edited).
            VButton(
                text = if (state.submitSuccess) "Saved" else "Save marks",
                onClick = onSubmit,
                full = true,
                size = VButtonSize.Lg,
                tone = VButtonTone.Lavender,
                loading = state.isSubmitting,
                success = state.submitSuccess,
                enabled = !state.isSubmitting && !state.submitSuccess,
                successLabel = "Saved",
            )
            // RA-S18: surface a submit error inline (VStateHost owns the load path; a failed save
            // must not silently look like nothing happened, nor wipe the entered marks).
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
