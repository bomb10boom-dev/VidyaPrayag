package com.littlebridge.enrollplus.ui.v2.screens.teacher

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
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherProfileState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherProfileViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * TeacherProfileScreenV2 — `Teacher.tsx → Profile`, wired to the real [TeacherProfileViewModel]
 * (`UserProfileApi` → `GET /api/v1/profile`).
 *
 * Centered avatar + name + username + subject badges, a stack of setting rows (using real
 * contact details), and a log-out button. No MockV2 in production; the three UI states come
 * from [VStateHost].
 */
@Composable
fun TeacherProfileScreenV2(
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    TeacherProfileContent(
        state = state,
        onLogout = onLogout,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun TeacherProfileContent(
    state: TeacherProfileState,
    onLogout: () -> Unit,
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
        message = "You'll need to sign in again to manage your classes.",
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
                        me.name,
                        style = VTheme.type.h2.colored(c.ink),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    Text(
                        me.username,
                        style = VTheme.type.dataSm.colored(c.ink2).copy(fontSize = 12.sp)
                    )
                    if (me.schoolName.isNotBlank()) {
                        Text(me.schoolName, style = VTheme.type.caption.colored(c.ink3))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        me.subjects.forEach { VBadge(text = it, tone = VBadgeTone.Arctic) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data class ProfileRow(val title: String, val sub: String, val onClick: (() -> Unit)?)
                    val rows = listOfNotNull(
                        ProfileRow(
                            "Personal details",
                            listOfNotNull(
                                me.phone.ifBlank { null },
                                me.email.ifBlank { null },
                            ).joinToString(" • ").ifBlank { "Mobile, email, photo" },
                            null,
                        ),
                        ProfileRow(
                            "Classes",
                            me.classes.joinToString(", ").ifBlank { "—" },
                            null,
                        ).takeIf { me.classes.isNotEmpty() },
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
                                horizontalArrangement = Arrangement.SpaceBetween
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
