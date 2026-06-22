package com.littlebridge.enrollplus.ui.v2.screens.teacher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.teacher.presentation.Homework
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherHomeworkState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherHomeworkViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * TeacherHomeworkScreenV2 — `Teacher.tsx → HomeworkFlow`, wired to the real
 * [TeacherHomeworkViewModel] (`TeacherRepository.getHomework` / `createHomework`).
 *
 * Homework cards (with submission progress / "just assigned" badges) and an "Assign new homework"
 * button. No MockV2 in production; the three UI states come from [VStateHost].
 */
@Composable
fun TeacherHomeworkScreenV2(
    modifier: Modifier = Modifier,
    viewModel: TeacherHomeworkViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    TeacherHomeworkContent(
        state = state,
        onAssign = { /* Phase 3E: open the create-homework sheet, then viewModel.create(...) */ },
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun TeacherHomeworkContent(
    state: TeacherHomeworkState,
    onAssign: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.items.isEmpty(),
            emptyTitle = "No homework assigned",
            emptyBody = "Homework you assign will appear here with live submission counts.",
            emptyIcon = VIcons.FileText,
            onRetry = onRetry,
        ) {
            state.items.forEach { hw -> HomeworkCard(hw) }
        }
        VButton(
            text = "Assign new homework",
            onClick = onAssign,
            full = true,
            size = VButtonSize.Lg,
            tone = VButtonTone.Lavender,
            loading = state.isCreating,
            leading = { Icon(VIcons.Plus, null, modifier = Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun HomeworkCard(hw: Homework) {
    val c = VTheme.colors
    val complete = hw.totalCount > 0 && hw.submittedCount >= hw.totalCount
    VCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(hw.title, style = VTheme.type.bodyStrong.colored(c.ink))
                Text(
                    listOfNotNull(hw.subject.ifBlank { null }, hw.className.ifBlank { null }, hw.dueDate.ifBlank { null }?.let { "Due $it" })
                        .joinToString(" • "),
                    style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 11.sp),
                )
            }
            if (hw.totalCount > 0) {
                VBadge(text = "${hw.submittedCount} / ${hw.totalCount} submitted", tone = if (complete) VBadgeTone.Success else VBadgeTone.Warning)
            } else {
                VBadge(text = "Just assigned", tone = VBadgeTone.Arctic)
            }
        }
        if (hw.totalCount > 0) {
            Spacer(Modifier.height(8.dp))
            VProgressBar(value = (hw.submissionRatio * 100f).roundToInt().toFloat())
        }
    }
}
