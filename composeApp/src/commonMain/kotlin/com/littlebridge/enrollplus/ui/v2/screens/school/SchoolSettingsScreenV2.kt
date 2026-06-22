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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.admin.presentation.InstitutionalProfileState
import com.littlebridge.enrollplus.feature.admin.presentation.InstitutionalProfileViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.components.VProgressRing
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * SchoolSettingsScreenV2 — `Admin.tsx → SettingsScreen`, wired to the real
 * [InstitutionalProfileViewModel] (`UserProfileApi` → `GET /api/v1/user/profile`).
 *
 * The institutional-profile health card (completion %, storage usage, public/private
 * visibility) is rendered from live VM state. The remaining static admin settings rows
 * (academic year, fee structure, notifications, data export) have no dedicated backend
 * endpoint of their own yet, so they keep their descriptive copy and are clearly marked
 * "Coming Soon" rather than fabricating data (LAW 6). No MockV2 in production; the three
 * UI states come from [VStateHost].
 */
@Composable
fun SchoolSettingsScreenV2(
    onLogout: () -> Unit = {},
    onOpenTeachers: () -> Unit = {},
    // RA-47 — open the editable institutional-profile (schools row) screen.
    onOpenProfile: () -> Unit = {},
    // VP-CAL — open the real Academic Year management screen.
    onOpenAcademicYear: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: InstitutionalProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    SchoolSettingsContent(
        state = state,
        onLogout = onLogout,
        onOpenTeachers = onOpenTeachers,
        onOpenProfile = onOpenProfile,
        onOpenAcademicYear = onOpenAcademicYear,
        onRetry = viewModel::load,
        modifier = modifier.statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    )
}

@Composable
private fun SchoolSettingsContent(
    state: InstitutionalProfileState,
    onLogout: () -> Unit,
    onOpenTeachers: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAcademicYear: () -> Unit,
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
        message = "You'll be signed out of the admin console and need to sign in again.",
        confirmLabel = "Log out",
        onConfirm = {
            showLogoutConfirm = false
            onLogout()
        },
        onDismiss = { showLogoutConfirm = false },
        icon = VIcons.AlertTriangle,
    )

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(top = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = VTheme.type.h1.colored(c.ink))

        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            // Settings always has rows to show; the profile card degrades
            // gracefully when empty, so this screen is never "empty".
            isEmpty = false,
            onRetry = onRetry,
        ) {
            // ── Institutional profile health (real VM data) ───────────────────
            InstitutionalProfileHealthCard(state = state, onClick = onOpenProfile)

            // ── Static admin settings rows ─────────────────────────────────────
            // These mirror Admin.tsx → SettingsScreen. Where there's no backend
            // endpoint they're labelled "Coming Soon" instead of showing fake data.
            val rows = listOf(
                // RA-47 — edit the live schools row (name, board, contact,
                // principal, address) instead of leaving it read-only.
                //SettingRow(VIcons.School, "Edit institutional profile", "Name, board, contact, principal & address",false, onClick = onOpenProfile),
                SettingRow(VIcons.Calendar, "Academic year", "Manage term dates & holidays", false, onClick = onOpenAcademicYear),
                SettingRow(VIcons.BookOpen, "Classes & subjects", "Class & subject setup", true),
                SettingRow(VIcons.Users, "Teacher management", "Add, view & remove teachers",false, onClick = onOpenTeachers),
                SettingRow(VIcons.Wallet, "Fee structure", "Edit heads & amounts for next cycle ", true),
                SettingRow(VIcons.Bell, "Notifications", "Channels & quiet hours", true),
                SettingRow(VIcons.Download, "Data export", "CSV / PDF / UDISE", true),
                SettingRow(
                    VIcons.Chat,
                    "Help & support",
                    "Email ${com.littlebridge.enrollplus.ui.v2.screens.auth.SUPPORT_EMAIL}",
                    false,
                    onClick = {
                        runCatching {
                            uriHandler.openUri(
                                "mailto:${com.littlebridge.enrollplus.ui.v2.screens.auth.SUPPORT_EMAIL}" +
                                    "?subject=VidyaSetu%20Support",
                            )
                        }
                    },
                ),
                SettingRow(VIcons.Settings, "Logout", "Sign out of the admin console",false, onClick = { showLogoutConfirm = true }),
            )
            Spacer(Modifier.height(0.dp))
            rows.forEach { row ->
                VCard(
                    onClick = if (row.isComingSoon) null else row.onClick
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.ink.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                row.icon,
                                contentDescription = null,
                                tint = c.ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }


                        Column(
                            Modifier.weight(1f)
                        ) {
                            Text(
                                row.title,
                                style = VTheme.type.bodyStrong.colored(c.ink)
                            )

                            Text(
                                row.sub,
                                style = VTheme.type.caption
                                    .colored(c.ink2)
                                    .copy(fontSize = 11.sp)
                            )
                        }


                        if (row.isComingSoon) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(c.teal.copy(alpha = 0.12f))
                                    .padding(
                                        horizontal = 10.dp,
                                        vertical = 5.dp
                                    )
                            ) {
                                Text(
                                    text = "Coming soon",
                                    style = VTheme.type.caption
                                        .colored(c.tealDeep)
                                        .copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                )
                            }
                        } else {
                            Icon(
                                VIcons.ChevronRight,
                                contentDescription = null,
                                tint = c.ink.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstitutionalProfileHealthCard(
    state: InstitutionalProfileState,
    onClick: () -> Unit,
) {
    val c = VTheme.colors
    val completionTone = if (state.profileCompletion < 60) VBadgeTone.Warning else VBadgeTone.Success
    val storagePercent = (state.storageUsage * 100f).coerceIn(0f, 100f)
    val visibilityTone = if (state.isPublic) VBadgeTone.Success else VBadgeTone.Neutral
    val visibilityLabel = if (state.isPublic) "Public profile" else "Private profile"
    val profileTitle = state.schoolName.ifBlank { "Institutional profile" }
    val nextStep = when {
        state.profileCompletion >= 90 -> "Profile is ready for families to discover."
        state.profileCompletion >= 60 -> "Add media and details to make it stand out."
        else -> "Complete the essentials to improve trust and discovery."
    }

    VCard(
        padding = 0.dp,
        background = c.card,
        onClick = onClick,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.teal.copy(alpha = if (c.isNight) 0.12f else 0.08f))
                .padding(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.tealDeep.copy(alpha = if (c.isNight) 0.24f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        VIcons.School,
                        contentDescription = null,
                        tint = c.tealDeep,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(Modifier.weight(1f)) {
                    Text(
                        profileTitle,
                        style = VTheme.type.bodyStrong.colored(c.ink),
                    )
                    Text(
                        nextStep,
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }

                Icon(
                    VIcons.ChevronRight,
                    contentDescription = null,
                    tint = c.ink3,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VBadge(
                    text = visibilityLabel,
                    tone = visibilityTone,
                    leadingIcon = if (state.isPublic) VIcons.Check else VIcons.Lock,
                )
                if (state.activeTourName.isNotBlank()) {
                    VBadge(text = "Tour live", tone = VBadgeTone.Arctic, leadingIcon = VIcons.Eye)
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                VProgressRing(
                    value = state.profileCompletion.toFloat(),
                    size = 72.dp,
                    strokeWidth = 8.dp,
                    tone = completionTone,
                    label = "${state.profileCompletion}%",
                )

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Profile completion", style = VTheme.type.bodyStrong.colored(c.ink))
                        Text("${state.profileCompletion}%", style = VTheme.type.dataSm.colored(c.ink2))
                    }
                    VProgressBar(
                        value = state.profileCompletion.toFloat(),
                        tone = completionTone,
                        height = 8.dp,
                    )
                    Text("School details, visibility, gallery and tour media.", style = VTheme.type.caption.colored(c.ink3))
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.cream.copy(alpha = if (c.isNight) 0.72f else 1f))
                    .padding(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(VIcons.Upload, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(18.dp))
                            Text("Media storage", style = VTheme.type.bodyStrong.colored(c.ink))
                        }
                        Text(
                            "${state.storageUsedHuman} / ${state.totalStorageHuman}",
                            style = VTheme.type.dataSm.colored(c.ink2),
                        )
                    }
                    VProgressBar(value = storagePercent, tone = VBadgeTone.Arctic, height = 7.dp)
                }
            }

            if (state.learningModel.isNotBlank() || state.primaryLanguage.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (state.learningModel.isNotBlank()) {
                        VBadge(text = state.learningModel, tone = VBadgeTone.Neutral, leadingIcon = VIcons.BookOpen)
                    }
                    if (state.primaryLanguage.isNotBlank()) {
                        VBadge(text = state.primaryLanguage, tone = VBadgeTone.Neutral, leadingIcon = VIcons.Chat)
                    }
                }
            }
        }
    }
}

private data class SettingRow(
    val icon: ImageVector,
    val title: String,
    val sub: String,
    val isComingSoon :Boolean,
    val onClick: (() -> Unit)? = null,
)
