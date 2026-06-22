package com.littlebridge.enrollplus.ui.v2.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherAssessmentsViewModel
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherClass
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherClassesViewModel
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-40: the teacher write plane (Attendance / Marks / Syllabus) was unreachable
 * because the portal hardcoded blank class/subject/exam ids. This selector sources
 * the real owned classes from `GET /teacher/classes` and (for Marks) the exams from
 * `GET /teacher/assessments`, feeding non-blank ids into the data-entry screens.
 *
 * [requireExam] is true for the Marks plane, which additionally needs a valid exam_id;
 * the exam picker (with inline create) only renders then.
 */
@Composable
fun TeacherClassPicker(
    classesViewModel: TeacherClassesViewModel = koinViewModel(),
    selectedClassId: String,
    onSelectClass: (TeacherClass) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by classesViewModel.state.collectAsStateV2()
    val c = VTheme.colors

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            "SELECT CLASS",
            style = VTheme.type.label.copy(color = c.ink3, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        when {
            state.isLoading -> Text("Loading classes…", style = VTheme.type.body.copy(color = c.ink3))
            state.error != null -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(state.error ?: "Couldn't load classes", style = VTheme.type.body.copy(color = c.danger))
                VButton(
                    text = "Retry",
                    onClick = { classesViewModel.load() },
                    variant = VButtonVariant.Ghost,
                    size = VButtonSize.Sm,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            state.classes.isEmpty() -> Text(
                "No classes assigned to you yet. Ask your administrator to assign a class.",
                style = VTheme.type.body.copy(color = c.ink3),
            )
            else -> Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.classes.forEach { cls ->
                    val selected = cls.id == selectedClassId
                    val label = "${cls.className} · ${cls.subject}"
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) c.tealDeep else c.cream)
                            .border(
                                width = 1.dp,
                                color = if (selected) c.tealDeep else c.hairline,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable { onSelectClass(cls) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            label,
                            style = VTheme.type.body.copy(
                                color = if (selected) c.card else c.ink,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * RA-40: the exam picker for the Marks plane. Lists assessments for [classId] and
 * lets the teacher create one inline when none exist — without a valid exam_id the
 * marks screen can never load, so this is the missing reachability link.
 */
@Composable
fun TeacherExamPicker(
    classId: String,
    assessmentsViewModel: TeacherAssessmentsViewModel = koinViewModel(),
    onSelectExam: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by assessmentsViewModel.state.collectAsStateV2()
    val c = VTheme.colors

    LaunchedEffect(classId) { assessmentsViewModel.load(classId) }
    // Bubble the active selection up so the host can gate the marks screen on it.
    LaunchedEffect(state.selectedExamId) { onSelectExam(state.selectedExamId) }

    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newMax by remember { mutableStateOf("100") }

    if (classId.isBlank()) return

    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text(
                "SELECT EXAM",
                style = VTheme.type.label.copy(color = c.ink3, fontWeight = FontWeight.SemiBold),
            )
            VButton(
                text = if (showCreate) "Cancel" else "New exam",
                onClick = { showCreate = !showCreate },
                variant = VButtonVariant.Ghost,
                size = VButtonSize.Sm,
            )
        }

        if (showCreate) {
            VCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    VInput(value = newName, onValueChange = { newName = it }, label = "Exam name", placeholder = "Unit Test I")
                    VInput(value = newMax, onValueChange = { newMax = it.filter { ch -> ch.isDigit() } }, label = "Max marks", placeholder = "100")
                    VButton(
                        text = "Create exam",
                        onClick = {
                            assessmentsViewModel.createAssessment(
                                classId = classId,
                                name = newName,
                                maxMarks = newMax.toIntOrNull(),
                                examDate = null,
                            )
                            newName = ""
                            newMax = "100"
                            showCreate = false
                        },
                        enabled = newName.isNotBlank() && !state.isCreating,
                        loading = state.isCreating,
                        full = true,
                    )
                }
            }
        }

        when {
            state.isLoading -> Text("Loading exams…", style = VTheme.type.body.copy(color = c.ink3), modifier = Modifier.padding(top = 8.dp))
            state.error != null -> Text(state.error ?: "Couldn't load exams", style = VTheme.type.body.copy(color = c.danger), modifier = Modifier.padding(top = 8.dp))
            state.assessments.isEmpty() && !showCreate -> Text(
                "No exams yet — tap \"New exam\" to create one before entering marks.",
                style = VTheme.type.body.copy(color = c.ink3),
                modifier = Modifier.padding(top = 8.dp),
            )
            else -> Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.assessments.forEach { exam ->
                    val selected = exam.id == state.selectedExamId
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) c.tealDeep else c.cream)
                            .border(1.dp, if (selected) c.tealDeep else c.hairline, RoundedCornerShape(10.dp))
                            .clickable { assessmentsViewModel.selectExam(exam.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            "${exam.name} · /${exam.maxMarks}",
                            style = VTheme.type.body.copy(
                                color = if (selected) c.card else c.ink,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                        )
                    }
                }
            }
        }
    }
}
