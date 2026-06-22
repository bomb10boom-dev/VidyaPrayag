package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * TeacherFirstLoginScreenV2 — the one-time "set a new password" gate, translated from
 * `Auth.tsx → TeacherFirstLogin`.
 *
 * **RA-54 (resolved):** `POST /api/v1/auth/change-password` now exists and the teacher is
 * provisioned with `must_change_password=true` / `profile_completed=false`. This screen performs
 * local validation (new ≥ 8 chars, confirmation matches) AND calls the real
 * [AuthRepository.changePassword]; on success the server flips `profile_completed=true` and clears
 * `must_change_password`, the client persists `profileCompleted=true`, and [onDone] advances to the
 * portal — so the gate never reappears on cold start. No server write is faked.
 */
@Composable
fun TeacherFirstLoginScreenV2(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    teacherName: String? = "Mr. Vikram",
    authRepository: AuthRepository = koinInject(),
) = VTheme(tone = VPortalTone.Night) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .background(VTheme.colors.background)
            // §11 cross-platform — respect the status bar, IME, and gesture bar
            // on both Android and iOS. Order matters: statusBars first, then IME
            // (so the keyboard lifts the content), then navigationBars (so the
            // bottom CTA never sits under the gesture bar).
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(VTheme.dimens.xxl))
        // §5: React `Label` component = labelStrong (11/700/0.10em UPPER), ink3.
        Text(
            if (teacherName.isNullOrBlank()) "Welcome" else "Welcome, $teacherName",
            style = VTheme.type.labelStrong.colored(VTheme.colors.ink3),
        )
        Spacer(Modifier.height(VTheme.dimens.xs))
        // §5: React heading is <h1> (32/800), not h2.
        Text("Set a new password", style = VTheme.type.h1.colored(VTheme.colors.ink))
        Text(
            "For your security, choose a fresh password before continuing. You'll only do this once.",
            style = VTheme.type.body.colored(VTheme.colors.ink2),
            modifier = Modifier.padding(top = VTheme.dimens.xs),
        )

        Spacer(Modifier.height(VTheme.dimens.xl))
        VInput(current, { current = it; error = null }, label = "Current temporary password", placeholder = "••••••••", isPassword = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(VTheme.dimens.md))
        VInput(newPassword, { newPassword = it; error = null }, label = "New password", placeholder = "At least 8 characters", isPassword = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(VTheme.dimens.md))
        VInput(confirm, { confirm = it; error = null }, label = "Confirm new password", placeholder = "Re-enter", isPassword = true, modifier = Modifier.fillMaxWidth())

        if (error != null) {
            Spacer(Modifier.height(VTheme.dimens.sm))
            Text(error!!, style = VTheme.type.caption.colored(VTheme.colors.dangerInk))
        }

        Spacer(Modifier.height(VTheme.dimens.xl))
        VButton(
            text = "Update & continue",
            loading = submitting,
            onClick = {
                error = validate(current, newPassword, confirm)
                if (error == null && !submitting) {
                    submitting = true
                    scope.launch {
                        // The teacher is in the forced first-change flow, so the
                        // server does not require the (generated) old password to
                        // match; we still pass it through for audit completeness.
                        when (val r = authRepository.changePassword(current.ifBlank { null }, newPassword)) {
                            is NetworkResult.Success -> { submitting = false; onDone() }
                            is NetworkResult.Error -> { submitting = false; error = r.message }
                            is NetworkResult.ConnectionError -> { submitting = false; error = "Connection error. Please try again." }
                        }
                    }
                }
            },
            full = true,
            size = VButtonSize.Lg,
            tone = VButtonTone.Teal,
            soft = false,
            // §5: React places ArrowRight AFTER the label (trailing), not leading.
            trailing = { Icon(VIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        Spacer(Modifier.height(VTheme.dimens.md))
        VButton(
            text = "Need help signing in?",
            onClick = {},
            full = true,
            variant = VButtonVariant.Ghost,
            tone = VButtonTone.Navy,
        )
        Spacer(Modifier.height(VTheme.dimens.xl))
    }
}

/**
 * Client-side pre-validation before the real change-password call. The current
 * (generated) password is optional in the forced first-change flow — the server
 * does not require it to match — so we only enforce the new-password rules here.
 */
private fun validate(current: String, newPassword: String, confirm: String): String? = when {
    newPassword.length < 8 -> "New password must be at least 8 characters."
    newPassword != confirm -> "Passwords don't match."
    else -> null
}
