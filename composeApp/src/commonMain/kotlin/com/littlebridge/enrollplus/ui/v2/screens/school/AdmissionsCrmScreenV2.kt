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
import com.littlebridge.enrollplus.feature.admin.domain.model.Enquiry
import com.littlebridge.enrollplus.feature.admin.presentation.AdmissionCRMState
import com.littlebridge.enrollplus.feature.admin.presentation.AdmissionCRMViewModel
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
import kotlin.math.roundToInt

/**
 * AdmissionsCrmScreenV2 — Admissions CRM dashboard overlay.
 *
 * Wired to [AdmissionCRMViewModel] (`GET /api/v1/admissions/enquiries/summary`,
 * `PATCH .../enquiries/{id}/status`).
 *
 * Layout:
 *   • 4 KPI VCards (Total · New · Follow-ups · Conversions)
 *   • Conversion rate VCard with efficiency badge
 *   • Recent enquiries list — each row shows student/parent/class/date + status
 *     pill and a quick-action VButton to advance the funnel.
 *
 * Three states via [VStateHost] (LAW 3).
 */
@Composable
fun AdmissionsCrmScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AdmissionCRMViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val isLoading by viewModel.isLoading.collectAsStateV2()
    val errorMessage by viewModel.errorMessage.collectAsStateV2()

    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Admissions CRM", onBack = onBack)
        AdmissionsCrmContent(
            state = state,
            isLoading = isLoading,
            error = errorMessage,
            onUpdateStatus = viewModel::updateEnquiryStatus,
            onRetry = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AdmissionsCrmContent(
    state: AdmissionCRMState,
    isLoading: Boolean,
    error: String?,
    onUpdateStatus: (String, String) -> Unit,
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
        VStateHost(
            loading = isLoading,
            error = error,
            // Empty = no data AND no recent enquiries (totals==0). Show empty.
            isEmpty = state.totalEnquiries == 0 && state.recentEnquiries.isEmpty(),
            emptyTitle = "No enquiries yet",
            emptyBody = "New admission enquiries from your website / referrals will appear here.",
            emptyIcon = VIcons.Users,
            onRetry = onRetry,
        ) {
            // KPI grid (2x2)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { KpiTile(label = "Total", value = state.totalEnquiries.toString()) }
                Box(Modifier.weight(1f)) { KpiTile(label = "New", value = state.newEnquiries.toString()) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { KpiTile(label = "Follow-ups", value = state.followUps.toString()) }
                Box(Modifier.weight(1f)) { KpiTile(label = "Conversions", value = state.conversions.toString()) }
            }

            // Conversion summary
            VCard {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Conversion rate", style = VTheme.type.label.colored(c.ink3))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.conversionRate.roundToInt()}%",
                            style = VTheme.type.dataLg.colored(c.ink),
                        )
                    }
                    VBadge(text = state.efficiencyLabel, tone = VBadgeTone.Arctic)
                }
            }

            VSectionHeader(title = "RECENT ENQUIRIES")

            if (state.recentEnquiries.isEmpty()) {
                VCard {
                    Text(
                        "No recent enquiries to display.",
                        style = VTheme.type.caption.colored(c.ink3),
                    )
                }
            } else {
                state.recentEnquiries.forEach { e ->
                    EnquiryCard(enquiry = e, onUpdateStatus = onUpdateStatus)
                }
            }
        }
    }
}

@Composable
private fun KpiTile(label: String, value: String) {
    val c = VTheme.colors
    VCard {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(4.dp))
        Text(value, style = VTheme.type.dataLg.colored(c.ink))
    }
}

@Composable
private fun EnquiryCard(
    enquiry: Enquiry,
    onUpdateStatus: (String, String) -> Unit,
) {
    val c = VTheme.colors
    val id = enquiry.id ?: return
    val (badgeText, badgeTone) = when (enquiry.status) {
        Enquiry.STATUS_NEW -> "New" to VBadgeTone.Arctic
        Enquiry.STATUS_FOLLOWUP -> "Follow-up" to VBadgeTone.Warning
        Enquiry.STATUS_CONVERTED -> "Converted" to VBadgeTone.Success
        Enquiry.STATUS_REJECTED -> "Rejected" to VBadgeTone.Danger
        else -> enquiry.status to VBadgeTone.Neutral
    }
    VCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VAvatar(name = enquiry.studentName.ifBlank { "?" }, src = enquiry.profilePic, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        enquiry.studentName,
                        style = VTheme.type.bodyStrong.colored(c.ink),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VBadge(text = badgeText, tone = badgeTone)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${enquiry.parentName} · ${enquiry.className}",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                Spacer(Modifier.height(2.dp))
                Text(enquiry.date, style = VTheme.type.caption.colored(c.ink3))
            }
        }

        // Funnel actions for non-terminal statuses
        if (enquiry.status == Enquiry.STATUS_NEW || enquiry.status == Enquiry.STATUS_FOLLOWUP) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enquiry.status == Enquiry.STATUS_NEW) {
                    Box(Modifier.weight(1f)) {
                        VButton(
                            text = "Follow-up",
                            onClick = { onUpdateStatus(id, Enquiry.STATUS_FOLLOWUP) },
                            full = true,
                            variant = VButtonVariant.Secondary,
                            tone = VButtonTone.Navy,
                            size = VButtonSize.Sm,
                        )
                    }
                }
                Box(Modifier.weight(1f)) {
                    VButton(
                        text = "Convert",
                        onClick = { onUpdateStatus(id, Enquiry.STATUS_CONVERTED) },
                        full = true,
                        variant = VButtonVariant.Primary,
                        tone = VButtonTone.Teal,
                        size = VButtonSize.Sm,
                    )
                }
                Box(Modifier.weight(1f)) {
                    VButton(
                        text = "Reject",
                        onClick = { onUpdateStatus(id, Enquiry.STATUS_REJECTED) },
                        full = true,
                        variant = VButtonVariant.Secondary,
                        tone = VButtonTone.Navy,
                        size = VButtonSize.Sm,
                    )
                }
            }
        }
    }
}
