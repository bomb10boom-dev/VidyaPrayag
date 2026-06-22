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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.presentation.LeaveRequestItem
import com.littlebridge.enrollplus.feature.admin.presentation.LeaveRequestsState
import com.littlebridge.enrollplus.feature.admin.presentation.LeaveRequestsViewModel
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
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * LeaveRequestsScreenV2 — admin Leave Requests overlay.
 *
 * Wired to [LeaveRequestsViewModel] (`GET /api/v1/school/leaves?type=...`,
 * `POST /api/v1/school/leaves/{id}/status`).
 *
 * Layout:
 *   • Student/Teacher tag selector
 *   • KPI VCard (approval rate + weekly count)
 *   • Pending requests list — each VCard has avatar, requester, date range, reason,
 *     Approve (Teal Primary) + Reject (Danger Secondary) VButtons.
 *
 * Approved/Rejected rows show a status VBadge instead of action buttons.
 * Three states via [VStateHost] (LAW 3). Portal overlay — back returns to tabs.
 */
@Composable
fun LeaveRequestsScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LeaveRequestsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Leave Requests", onBack = onBack)
        LeaveRequestsContent(
            state = state,
            onTypeChange = viewModel::setRequestType,
            onApprove = viewModel::approveRequest,
            onReject = viewModel::rejectRequest,
            onRetry = { viewModel.loadRequests() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LeaveRequestsContent(
    state: LeaveRequestsState,
    onTypeChange: (String) -> Unit,
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
        // Type selector
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VTag(
                text = "Students",
                active = state.requestType == "Student",
                onClick = { onTypeChange("Student") },
            )
            VTag(
                text = "Teachers",
                active = state.requestType == "Teacher",
                onClick = { onTypeChange("Teacher") },
            )
        }

        // KPI row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                KpiCard(label = "Approval rate", value = "${state.approvalRate}%")
            }
            Box(Modifier.weight(1f)) {
                KpiCard(label = "This week", value = state.weeklyCount.toString())
            }
        }

        VSectionHeader(title = "PENDING REQUESTS")

        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            isEmpty = state.requests.isEmpty(),
            emptyTitle = "No requests",
            emptyBody = "There are no ${state.requestType.lowercase()} leave requests right now.",
            emptyIcon = VIcons.ClipboardList,
            onRetry = onRetry,
        ) {
            state.requests.forEach { req ->
                LeaveRequestCard(
                    req = req,
                    onApprove = { onApprove(req.id) },
                    onReject = { onReject(req.id) },
                )
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String) {
    val c = VTheme.colors
    VCard {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(4.dp))
        Text(value, style = VTheme.type.dataLg.colored(c.ink))
    }
}

@Composable
private fun LeaveRequestCard(
    req: LeaveRequestItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val c = VTheme.colors
    val isPending = req.status.equals("Pending", ignoreCase = true)
    VCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VAvatar(name = req.requesterName.ifBlank { "?" }, src = req.imageUrl.ifBlank { null }, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        req.requesterName,
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
                Spacer(Modifier.height(2.dp))
                Text(req.dateRange, style = VTheme.type.caption.colored(c.ink3))
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
                        variant = VButtonVariant.Primary,
                        tone = VButtonTone.Teal,
                        size = VButtonSize.Sm,
                    )
                }
            }
        }
    }
}
