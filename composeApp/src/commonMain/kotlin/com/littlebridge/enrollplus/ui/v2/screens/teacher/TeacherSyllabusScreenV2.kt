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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherSyllabusState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherSyllabusViewModel
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * TeacherSyllabusScreenV2 — `Teacher.tsx → SyllabusFlow`, wired to the real
 * [TeacherSyllabusViewModel] (`TeacherRepository.getSyllabus` / `updateSyllabus`).
 *
 * Class/subject selectors, an overall-progress bar, and a tappable unit checklist that toggles
 * coverage and notifies parents via the VM. No MockV2 in production; the three UI states come
 * from [VStateHost]. When [classId]/[subject] are blank an empty prompt is shown.
 */
@Composable
fun TeacherSyllabusScreenV2(
    classId: String = "",
    subject: String = "",
    modifier: Modifier = Modifier,
    viewModel: TeacherSyllabusViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    LaunchedEffect(classId, subject) {
        if (classId.isNotBlank() && subject.isNotBlank()) viewModel.load(classId, subject)
    }

    TeacherSyllabusContent(
        state = state,
        hasSelection = classId.isNotBlank() && subject.isNotBlank(),
        onToggle = viewModel::toggleUnit,
        onRetry = { if (classId.isNotBlank() && subject.isNotBlank()) viewModel.load(classId, subject) },
        modifier = modifier,
    )
}

@Composable
private fun TeacherSyllabusContent(
    state: TeacherSyllabusState,
    hasSelection: Boolean,
    onToggle: (String) -> Unit,
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
            isEmpty = !hasSelection || state.units.isEmpty(),
            emptyTitle = if (hasSelection) "No units" else "Choose a class",
            emptyBody = if (hasSelection) "This subject has no syllabus units yet."
            else "Pick a class and subject to log progress.",
            emptyIcon = VIcons.FileText,
            onRetry = onRetry,
        ) {
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    VLabel("Syllabus progress")
                    Text(
                        "${(state.overallProgress * 100).roundToInt()}% • ${state.coveredCount}/${state.units.size}",
                        style = VTheme.type.dataSm.colored(c.ink2).copy(fontSize = 11.sp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                VProgressBar(value = state.overallProgress * 100f)
            }
            VCard {
                state.units.forEachIndexed { i, unit ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CoverToggle(covered = unit.isCovered, enabled = state.updatingUnitId == null) { onToggle(unit.id) }
                        Column(Modifier.weight(1f)) {
                            Text(unit.title, style = VTheme.type.bodyStrong.colored(c.ink).copy(fontSize = 13.sp))
                            // Smart-cast on `unit.coveredOn` is forbidden cross-module
                            // (the property lives in :shared). Pin into a local val.
                            val coveredOn = unit.coveredOn
                            if (unit.isCovered && !coveredOn.isNullOrBlank()) {
                                Text("Covered $coveredOn", style = VTheme.type.dataSm.colored(c.ink3).copy(fontSize = 11.sp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverToggle(covered: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (covered) c.success else c.ink.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (covered) {
            Icon(VIcons.Check, contentDescription = "Covered", tint = Color(0xFF080808), modifier = Modifier.size(16.dp))
        }
    }
}
