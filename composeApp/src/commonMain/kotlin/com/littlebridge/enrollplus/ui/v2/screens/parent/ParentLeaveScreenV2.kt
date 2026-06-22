package com.littlebridge.enrollplus.ui.v2.screens.parent

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentLeaveDto
import com.littlebridge.enrollplus.feature.parent.presentation.ParentLeaveState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentLeaveViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VDatePicker
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * ParentLeaveScreenV2 — RA-44 parent leg of the leave workflow.
 *
 * Wired to [ParentLeaveViewModel] (`GET /api/v1/parent/leave`, `POST /api/v1/parent/leave`).
 * A parent picks a child, enters the leave dates + reason, and submits — the
 * request routes to the child's class teacher (notified server-side). The list
 * below shows the parent's own requests with live status. Three states via
 * [VStateHost] (LAW 3). Portal overlay — back returns to the parent tabs.
 */
@Composable
fun ParentLeaveScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ParentLeaveViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    Column(modifier.fillMaxSize()) {
        VBackHeader(title = "Leave", onBack = onBack)
        ParentLeaveContent(
            state = state,
            onSelectChild = viewModel::selectChild,
            onApply = viewModel::apply,
            onRetry = { viewModel.load() },
            onConsumeResult = viewModel::consumeSubmitResult,
            modifier = Modifier.fillMaxSize().statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ParentLeaveContent(
    state: ParentLeaveState,
    onSelectChild: (String) -> Unit,
    onApply: (String, String, String) -> Unit,
    onRetry: () -> Unit,
    onConsumeResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var dateFrom by remember { mutableStateOf("") }
    var dateTo by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    // Clear the form after a successful submission.
    LaunchedEffect(state.submittedOk) {
        if (state.submittedOk) {
            dateFrom = ""; dateTo = ""; reason = ""
            onConsumeResult()
        }
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VSectionHeader(title = "APPLY FOR LEAVE")

        // Child picker (RA-56) — only shown when the parent has children.
        if (state.children.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.children.forEach { child ->
                    VTag(
                        text = child.name.ifBlank { "Child" },
                        active = child.id == state.selectedChild?.id,
                        onClick = { onSelectChild(child.id) },
                        accentActive = true,
                    )
                }
            }
        }

        VCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        VDatePicker(
                            value = dateFrom,
                            onValueChange = { dateFrom = it },
                            label = "From",
                            placeholder = "Start date",
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        VDatePicker(
                            value = dateTo,
                            onValueChange = { dateTo = it },
                            label = "To",
                            placeholder = "End date",
                        )
                    }
                }
                VInput(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Reason",
                    placeholder = "e.g. Fever / family event",
                    singleLine = false,
                )
                if (state.submitError != null) {
                    Text(state.submitError!!, style = VTheme.type.caption.colored(c.danger))
                }
                VButton(
                    text = "Submit request",
                    onClick = { onApply(dateFrom.trim(), dateTo.trim(), reason) },
                    full = true,
                    enabled = !state.submitting && state.selectedChild != null,
                    loading = state.submitting,
                    variant = VButtonVariant.Primary,
                    tone = VButtonTone.Lavender,
                )
            }
        }

        VSectionHeader(title = "MY REQUESTS")

        VStateHost(
            loading = state.loading,
            error = state.error,
            isEmpty = state.requests.isEmpty(),
            emptyTitle = "No leave requests",
            emptyBody = "Requests you submit will appear here with their status.",
            emptyIcon = VIcons.ClipboardList,
            onRetry = onRetry,
        ) {
            state.requests.forEach { req ->
                ParentLeaveCard(req = req)
            }
        }
    }
}

@Composable
private fun ParentLeaveCard(req: ParentLeaveDto) {
    val c = VTheme.colors
    VCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(req.childName, style = VTheme.type.bodyStrong.colored(c.ink), modifier = Modifier.weight(1f))
            // Semantic status: "Approved" reads success green, "Rejected" danger red,
            // anything pending stays calm neutral — the universal traffic-light language a
            // parent reads instantly (the brand violet is reserved for actions, not states).
            val tone = when {
                req.status.equals("Approved", ignoreCase = true) -> VBadgeTone.Success
                req.status.equals("Rejected", ignoreCase = true) -> VBadgeTone.Danger
                else -> VBadgeTone.Warning
            }
            VBadge(text = req.status, tone = tone)
        }
        Spacer(Modifier.height(2.dp))
        Text("${req.dateFrom} → ${req.dateTo}", style = VTheme.type.caption.colored(c.ink3))
        if (req.reason.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(req.reason, style = VTheme.type.body.colored(c.ink2))
        }
    }
}
