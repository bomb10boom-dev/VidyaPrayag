package com.littlebridge.enrollplus.ui.v2.screens.teacher

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherLeaveDto
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherLeaveState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherLeaveViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * TeacherLeaveScreenV2 — RA-44 teacher leg of the leave workflow.
 *
 * Wired to [TeacherLeaveViewModel] (`GET /api/v1/teacher/leave-requests`,
 * `PATCH /api/v1/teacher/leave-requests/{id}`). Lists leave requests routed to
 * the teacher's classes; Approve / Reject write to Supabase and notify the
 * applicant parent. Three states via [VStateHost] (LAW 3). Portal overlay —
 * back returns to the teacher tabs.
 */
@Composable
fun TeacherLeaveScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherLeaveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    Column(modifier.fillMaxSize()) {
        VBackHeader(title = "Leave Requests", onBack = onBack)
        TeacherLeaveContent(
            state = state,
            onApprove = viewModel::approve,
            onReject = viewModel::reject,
            onRetry = { viewModel.load() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TeacherLeaveContent(
    state: TeacherLeaveState,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VCard {
            Text("Pending", style = VTheme.type.label.colored(c.ink3))
            Spacer(Modifier.height(4.dp))
            Text(state.pendingCount.toString(), style = VTheme.type.dataLg.colored(c.ink))
        }

        VSectionHeader(title = "LEAVE REQUESTS")

        VStateHost(
            loading = state.loading,
            error = state.error,
            isEmpty = state.requests.isEmpty(),
            emptyTitle = "No requests",
            emptyBody = "No leave requests for your classes right now.",
            emptyIcon = VIcons.ClipboardList,
            onRetry = onRetry,
        ) {
            state.requests.forEach { req ->
                TeacherLeaveCard(
                    req = req,
                    deciding = state.decidingId == req.id,
                    onApprove = { onApprove(req.id) },
                    onReject = { onReject(req.id) },
                )
            }
        }
    }
}

@Composable
private fun TeacherLeaveCard(
    req: TeacherLeaveDto,
    deciding: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val c = VTheme.colors
    val isPending = req.status.equals("Pending", ignoreCase = true)
    val classLine = listOfNotNull(
        req.className?.takeIf { it.isNotBlank() },
        req.section?.takeIf { it.isNotBlank() }?.let { "Sec $it" },
    ).joinToString(" · ")
    VCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VAvatar(name = req.studentName.ifBlank { "?" }, src = req.imageUrl?.ifBlank { null }, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        req.studentName,
                        style = VTheme.type.bodyStrong.colored(c.ink),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!isPending) {
                        val tone = when {
                            req.status.equals("Approved", ignoreCase = true) -> VBadgeTone.Success
                            req.status.equals("Rejected", ignoreCase = true) -> VBadgeTone.Danger
                            else -> VBadgeTone.Neutral
                        }
                        VBadge(text = req.status, tone = tone)
                    }
                }
                if (classLine.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(classLine, style = VTheme.type.caption.colored(c.ink3))
                }
                Spacer(Modifier.height(2.dp))
                Text("${req.dateFrom} → ${req.dateTo}", style = VTheme.type.caption.colored(c.ink3))
                if (req.reason.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(req.reason, style = VTheme.type.body.colored(c.ink2))
                }
            }
        }
        if (isPending) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    VButton(
                        text = "Reject",
                        onClick = onReject,
                        full = true,
                        enabled = !deciding,
                        loading = deciding,
                        variant = VButtonVariant.Secondary,
                        tone = VButtonTone.Navy,
                        size = VButtonSize.Sm,
                    )
                }
                Box(Modifier.weight(1f)) {
                    VButton(
                        text = "Approve",
                        onClick = onApprove,
                        full = true,
                        enabled = !deciding,
                        loading = deciding,
                        variant = VButtonVariant.Primary,
                        tone = VButtonTone.Teal,
                        size = VButtonSize.Sm,
                    )
                }
            }
        }
    }
}
