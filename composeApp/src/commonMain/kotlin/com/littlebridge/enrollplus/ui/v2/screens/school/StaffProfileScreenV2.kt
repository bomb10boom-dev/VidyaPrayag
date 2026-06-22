package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.StaffDto
import com.littlebridge.enrollplus.feature.admin.presentation.StaffViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-S17: StaffProfileScreenV2 — a single non-teaching-staff member's record for
 * the admin. The [staffId] is passed by the caller (portal overlay). The roster
 * VM already holds the staff list; we resolve the row from it so this screen
 * does not need its own fetch. Deletion lives here (RA-S17 — not a list-row
 * button), behind a confirm dialog. Three states via [VStateHost].
 */
@Composable
fun StaffProfileScreenV2(
    staffId: String,
    onBack: () -> Unit = {},
    onRemoved: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    viewModel: StaffViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val member = state.staff.firstOrNull { it.id == staffId }
    // When the VM removes this member from the list, leave the profile.
    androidx.compose.runtime.LaunchedEffect(state.staff) {
        if (state.staff.none { it.id == staffId } && !state.isLoading) onRemoved()
    }

    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Staff", onBack = onBack)
        StaffProfileContent(
            member = member,
            isLoading = state.isLoading,
            error = state.error,
            isRemoving = state.removingIds.contains(staffId),
            onRetry = viewModel::load,
            onRemove = { viewModel.removeStaff(staffId) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun StaffProfileContent(
    member: StaffDto?,
    isLoading: Boolean,
    error: String?,
    isRemoving: Boolean,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var confirmRemove by remember { mutableStateOf(false) }

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
            isEmpty = member == null && !isLoading && error == null,
            emptyTitle = "No profile",
            emptyBody = "This staff member's record could not be found.",
            emptyIcon = VIcons.User,
            onRetry = onRetry,
        ) {
            val m = member ?: return@VStateHost

            VCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VAvatar(name = m.fullName, src = m.photoUrl, size = 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(m.fullName, style = VTheme.type.h3.colored(c.ink))
                        Text(m.role, style = VTheme.type.caption.colored(c.ink2))
                    }
                    m.department?.takeIf { it.isNotBlank() }?.let {
                        VBadge(text = it, tone = VBadgeTone.Neutral)
                    }
                }
            }

            VSectionHeader(title = "CONTACT")
            VCard {
                val phone = m.phone?.takeIf { it.isNotBlank() }
                val email = m.email?.takeIf { it.isNotBlank() }
                if (phone == null && email == null) {
                    Text("No contact details on file.", style = VTheme.type.body.colored(c.ink2))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        phone?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(VIcons.Phone, contentDescription = null, tint = c.ink3, modifier = Modifier.size(16.dp))
                                Text(it, style = VTheme.type.body.colored(c.ink))
                            }
                        }
                        email?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(VIcons.Mail, contentDescription = null, tint = c.ink3, modifier = Modifier.size(16.dp))
                                Text(it, style = VTheme.type.body.colored(c.ink))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            VButton(
                text = "Remove from school",
                onClick = { confirmRemove = true },
                variant = VButtonVariant.Destructive,
                full = true,
                enabled = !isRemoving,
                loading = isRemoving,
                leading = { Icon(VIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
            )
        }
    }

    VConfirmDialog(
        visible = confirmRemove,
        title = "Remove staff member",
        message = "Remove ${member?.fullName ?: "this staff member"} from your school? " +
            "Their record will be hidden. This can be reversed by re-adding them.",
        confirmLabel = "Remove",
        icon = VIcons.AlertTriangle,
        onConfirm = { confirmRemove = false; onRemove() },
        onDismiss = { confirmRemove = false },
    )
}
