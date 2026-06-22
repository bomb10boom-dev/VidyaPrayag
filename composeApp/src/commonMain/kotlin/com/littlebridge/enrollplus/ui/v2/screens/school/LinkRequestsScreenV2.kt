package com.littlebridge.enrollplus.ui.v2.screens.school

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
import com.littlebridge.enrollplus.feature.admin.domain.model.LinkRequestDto
import com.littlebridge.enrollplus.feature.admin.presentation.LinkRequestsState
import com.littlebridge.enrollplus.feature.admin.presentation.LinkRequestsViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
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
 * RA-48: LinkRequestsScreenV2 — admin queue of pending parent→child link requests.
 *
 * Wired to [LinkRequestsViewModel] (`GET /api/v1/school/link-requests?status=pending`,
 * `POST .../{id}/approve|reject`). Every request is scoped server-side to the
 * admin's own school. Approving materialises the children row + grants the parent
 * read access; rejecting notifies the parent to re-check the roll number.
 *
 * Three states via [VStateHost] (LAW 3). Portal overlay — back returns to tabs.
 */
@Composable
fun LinkRequestsScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LinkRequestsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    Column(modifier.fillMaxSize()
        .statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Child Link Requests", onBack = onBack)
        LinkRequestsContent(
            state = state,
            onApprove = viewModel::approve,
            onReject = viewModel::reject,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun LinkRequestsContent(
    state: LinkRequestsState,
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
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Parents requesting access to a student's records. Approving grants the " +
                "parent attendance, marks and syllabus for the matched student.",
            style = VTheme.type.caption.colored(c.ink3),
        )

        VSectionHeader(title = "PENDING REQUESTS")

        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.requests.isEmpty(),
            emptyTitle = "No pending requests",
            emptyBody = "There are no parent link requests awaiting your review.",
            emptyIcon = VIcons.ClipboardList,
            onRetry = onRetry,
        ) {
            state.requests.forEach { req ->
                LinkRequestCard(
                    req = req,
                    acting = state.actingIds.contains(req.id),
                    onApprove = { onApprove(req.id) },
                    onReject = { onReject(req.id) },
                )
            }
        }
    }
}

@Composable
private fun LinkRequestCard(
    req: LinkRequestDto,
    acting: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val c = VTheme.colors
    val childName = req.childName?.takeIf { it.isNotBlank() } ?: "Unknown student"
    VCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VAvatar(name = childName, src = null, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(childName, style = VTheme.type.bodyStrong.colored(c.ink))
                Spacer(Modifier.height(2.dp))
                val classRoll = buildString {
                    req.className?.takeIf { it.isNotBlank() }?.let { append("Class $it") }
                    req.rollNumber?.takeIf { it.isNotBlank() }?.let {
                        if (isNotEmpty()) append(" • ")
                        append("Roll $it")
                    }
                }
                if (classRoll.isNotBlank()) {
                    Text(classRoll, style = VTheme.type.caption.colored(c.ink3))
                }
                val parentLine = buildString {
                    append("Requested by ")
                    append(req.parentName?.takeIf { it.isNotBlank() } ?: "a parent")
                    req.parentPhone?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
                }
                Spacer(Modifier.height(6.dp))
                Text(parentLine, style = VTheme.type.body.colored(c.ink2))
            }
        }
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
                    enabled = !acting,
                    loading = acting,
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
                    enabled = !acting,
                    loading = acting,
                )
            }
        }
    }
}
