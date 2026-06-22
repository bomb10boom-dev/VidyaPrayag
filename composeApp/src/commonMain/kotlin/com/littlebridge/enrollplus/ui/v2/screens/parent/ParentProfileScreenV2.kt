package com.littlebridge.enrollplus.ui.v2.screens.parent

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
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.ParentProfileState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentProfileViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * ParentProfileScreenV2 — the parent's own profile, wired to the real
 * [ParentProfileViewModel] (`GET /api/v1/user/details` → `personal_details`).
 *
 * Audit finding **K** (§7): previously the parent portal had NO profile screen and
 * the avatar in [ParentPortalV2] was bound to `onLogout`, so tapping your photo
 * logged you out — a behaviour every user reads as a bug. This screen gives the
 * avatar a real destination and moves **Log out** to an explicit button here.
 *
 * Mirrors the existing `TeacherProfileScreenV2` design (frozen design language,
 * RULE-6) and exits with all three states via [VStateHost] (RULE-7).
 */
@Composable
fun ParentProfileScreenV2(
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onLinkChild: () -> Unit = {},
    onDiscoverSchools: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ParentProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    ParentProfileContent(
        state = state,
        onBack = onBack,
        onLogout = onLogout,
        onLinkChild = onLinkChild,
        onDiscoverSchools = onDiscoverSchools,
        onRetry = viewModel::load,
        modifier = modifier.statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    )
}

@Composable
private fun ParentProfileContent(
    state: ParentProfileState,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onLinkChild: () -> Unit,
    onDiscoverSchools: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    // RA-21: logout is destructive — gate it behind a confirmation dialog.
    var showLogoutConfirm by remember { mutableStateOf(false) }

    VConfirmDialog(
        visible = showLogoutConfirm,
        title = "Log out?",
        message = "You'll need to sign in again to follow your child's progress.",
        confirmLabel = "Log out",
        onConfirm = {
            showLogoutConfirm = false
            onLogout()
        },
        onDismiss = { showLogoutConfirm = false },
        icon = VIcons.AlertTriangle,
    )

    Column(modifier.fillMaxSize()
        .statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()
    ) {
        VBackHeader(title = "Profile", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 24.dp),
        ) {
            VStateHost(
                loading = state.isLoading,
                error = state.error,
                isEmpty = state.profile == null,
                emptyTitle = "Profile unavailable",
                emptyBody = "We couldn't load your profile. Please try again.",
                emptyIcon = VIcons.User,
                onRetry = onRetry,
                skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonProfile() },
            ) {
                val me = state.profile!!
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        VAvatar(name = me.name, src = me.photoUrl, size = 88.dp)
                        Text(
                            me.name.ifBlank { "Parent" },
                            style = VTheme.type.h2.colored(c.ink),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        VBadge(
                            text = me.role.lowercase().replaceFirstChar { it.uppercase() },
                            tone = VBadgeTone.Accent
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // RA-S04 (directive): the "Linked children" row is the ONLY place a
                        // parent links a child — opt-in from the profile, never forced after
                        // signup/login. Tapping it opens ParentLinkChildScreenV2.
                        data class ProfileRow(val title: String, val sub: String, val onClick: (() -> Unit)?)
                        val rows = listOf(
                            ProfileRow(
                                "Personal details",
                                listOf(me.phone, me.email).filter { it.isNotBlank() }
                                    .joinToString(" • ").ifBlank { "Mobile, email, photo" },
                                null,
                            ),
                            ProfileRow("Linked children", "Link a child or manage who you follow", onLinkChild),
                            // Marketplace entry for signed-in parents — browse every school on
                            // VidyaPrayag and open full school profiles (Discovery overlay).
                            ProfileRow("Discover schools", "Browse all schools on VidyaPrayag", onDiscoverSchools),
                            ProfileRow("Notification preferences", "Push, WhatsApp, quiet hours", null),
                            ProfileRow("Change password", "Keep your account secure", null),
                            ProfileRow(
                                "Help & support",
                                "Email ${com.littlebridge.enrollplus.ui.v2.screens.auth.SUPPORT_EMAIL}",
                                {
                                    runCatching {
                                        uriHandler.openUri(
                                            "mailto:${com.littlebridge.enrollplus.ui.v2.screens.auth.SUPPORT_EMAIL}" +
                                                "?subject=VidyaSetu%20Support",
                                        )
                                    }
                                },
                            ),
                        )
                        rows.forEach { row ->
                            VCard(onClick = row.onClick) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.title, style = VTheme.type.bodyStrong.colored(c.ink))
                                        Text(
                                            row.sub,
                                            style = VTheme.type.caption.colored(c.ink2)
                                                .copy(fontSize = 11.sp)
                                        )
                                    }
                                    Icon(
                                        VIcons.ChevronRight,
                                        contentDescription = null,
                                        tint = c.ink3,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        VButton(
                            text = "Log out",
                            onClick = { showLogoutConfirm = true },
                            full = true,
                            variant = VButtonVariant.Ghost
                        )
                    }
                }
            }
        }
    }
}
