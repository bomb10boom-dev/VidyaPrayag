package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.auth.presentation.AuthStep
import com.littlebridge.enrollplus.feature.auth.presentation.AuthViewModel
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VDivider
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * AdminAuthScreenV2 — the credential sign-in for **school staff** (PHASE 4 & 5).
 *
 * Handles BOTH `SCHOOL_ADMIN` and `TEACHER` from one screen: the role is resolved server-side from
 * the JWT, not chosen here. The user enters an email / school-ID / teacher-credential, then a
 * password. Copy is "School Administration Login" with zero mention of parents (LAW 3: no role
 * leakage) — there is no portal selector and no OTP affordance.
 *
 * Wiring: binds to the real [AuthViewModel]. On mount we set the role hint to ADMIN so the
 * credential (email/password) flow is selected by the repository's `checkUser`. `onContinue()`
 * advances to the password step; `onSubmit()` performs the login. The actual landing role
 * (SCHOOL_ADMIN vs TEACHER) is then decided by NavGraphV2 from the persisted JWT role (PHASE 7).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAuthScreenV2(
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    var passwordVisible by remember { mutableStateOf(false) }

    // Clear any state left over from a prior session (the AuthViewModel is reused across a
    // logout → landing → login round-trip) so the email/password fields start blank, then pin
    // to the ADMIN credential flow; the real role comes from the JWT post-login.
    LaunchedEffect(Unit) {
        viewModel.reset()
        viewModel.onRoleChanged("ADMIN")
    }
    LaunchedEffect(state.isAuthSuccessful) { if (state.isAuthSuccessful) onAuthSuccess() }

    AuthScaffoldV2(
        title = "School Administration",
        subtitle = "Sign in with your staff credentials to manage your institution.",
        error = state.error,
        onBack = onBack,
        modifier = modifier,
    ) {
        val c = VTheme.colors
        when (state.step) {
            AuthStep.Identifier -> {
                VInput(
                    value = state.identifier,
                    onValueChange = viewModel::onIdentifierChanged,
                    label = "Email or staff ID",
                    placeholder = "office@svm.edu.in  ·  SVM001.T07",
                    leadingIcon = VIcons.Mail,
                    keyboardType = KeyboardType.Email,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            AuthStep.LoginPassword -> {
                VInput(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = "Password",
                    placeholder = "••••••••",
                    leadingIcon = VIcons.Lock,
                    isPassword = true,
                    passwordVisible = passwordVisible,
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        Icon(
                            VIcons.Eye,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = c.ink3,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { passwordVisible = !passwordVisible },
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Forgot Password?",
                    style = VTheme.type.caption.colored(c.tealDeep).copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.align(Alignment.End),
                )
            }
            AuthStep.SignupDetails -> {
                if (state.isRegisterSchool) {
                    // ── "Onboard your school" — self-registration form ──────────
                    // RA-53 is preserved: this does NOT hit the anonymous /signup
                    // (which is locked to `parent`). It calls the dedicated
                    // /auth/register-school endpoint that mints a school_admin +
                    // a *pending* school, then routes into the onboarding wizard.
                    VInput(
                        value = state.name,
                        onValueChange = viewModel::onNameChanged,
                        label = "Your name",
                        placeholder = "Dr. Anita Verma",
                        leadingIcon = VIcons.User,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    // Email/identifier — REQUIRED. Without this the form validated
                    // an empty identifier and failed with "enter a valid email"
                    // even though there was nowhere to type one (the user reached
                    // this form directly from the first step's "Onboard with us
                    // now" CTA, bypassing the Identifier input).
                    VInput(
                        value = state.identifier,
                        onValueChange = viewModel::onIdentifierChanged,
                        label = "Work email",
                        placeholder = "office@svm.edu.in",
                        leadingIcon = VIcons.Mail,
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    VInput(
                        value = state.schoolName,
                        onValueChange = viewModel::onSchoolNameChanged,
                        label = "School name",
                        placeholder = "Saraswati Vidya Mandir",
                        leadingIcon = VIcons.School,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("BOARD", style = VTheme.type.labelStrong.colored(c.ink3))
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("CBSE", "ICSE", "UP State", "Other").forEach { b ->
                            VTag(text = b, active = state.board == b, onClick = { viewModel.onBoardChanged(b) })
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    VInput(
                        value = state.city,
                        onValueChange = viewModel::onCityChanged,
                        label = "City (optional)",
                        placeholder = "Lucknow",
                        leadingIcon = VIcons.MapPin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    VInput(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = "Create a password",
                        placeholder = "At least 8 characters",
                        leadingIcon = VIcons.Lock,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        modifier = Modifier.fillMaxWidth(),
                        trailing = {
                            Icon(
                                VIcons.Eye,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                tint = c.ink3,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { passwordVisible = !passwordVisible },
                            )
                        },
                    )
                } else {
                    // RA-53 🔴 — No anonymous staff account creation. We offer the
                    // sanctioned "Onboard your school" path (creates a school_admin
                    // + a pending school) and otherwise direct provisioned staff to
                    // their administrator.
                    Text(
                        "No account exists for this email.",
                        style = VTheme.type.bodyStrong.colored(c.ink),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "New to VidyaPrayag? Register your school below to set it up and " +
                            "create your administrator account. Teachers and additional staff " +
                            "are added by your school administrator after onboarding.",
                        style = VTheme.type.body.colored(c.ink3),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            // Staff never use OTP — keep the branch exhaustive but safe.
            AuthStep.Otp -> {
                VInput(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChanged,
                    label = "Password",
                    placeholder = "••••••••",
                    leadingIcon = VIcons.Lock,
                    isPassword = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // The SignupDetails step is no longer a dead-end: it offers the
        // sanctioned "Onboard your school" registration (school_admin + pending
        // school) — never the anonymous /signup escalation path (RA-53).
        when (state.step) {
            AuthStep.SignupDetails -> {
                if (state.isRegisterSchool) {
                    VButton(
                        text = "Register & continue",
                        onClick = viewModel::registerSchool,
                        full = true,
                        size = VButtonSize.Lg,
                        tone = VButtonTone.Teal,
                        loading = state.isLoading,
                        enabled = !state.isLoading,
                        trailing = { Icon(VIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    AuthBackLink(onClick = viewModel::cancelRegisterSchool, modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    VButton(
                        text = "Onboard your school",
                        onClick = viewModel::startRegisterSchool,
                        full = true,
                        size = VButtonSize.Lg,
                        tone = VButtonTone.Teal,
                        loading = state.isLoading,
                        leading = { Icon(VIcons.School, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                    Spacer(Modifier.height(8.dp))
                    AuthBackLink(onClick = viewModel::goBack, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            else -> {
                val ctaLabel = if (state.step == AuthStep.Identifier) "Continue" else "Sign In"
                VButton(
                    text = ctaLabel,
                    onClick = { if (state.step == AuthStep.Identifier) viewModel.onContinue() else viewModel.onSubmit() },
                    full = true,
                    size = VButtonSize.Lg,
                    tone = VButtonTone.Teal,
                    loading = state.isLoading,
                    trailing = { Icon(VIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
                if (state.step != AuthStep.Identifier) {
                    Spacer(Modifier.height(8.dp))
                    AuthBackLink(onClick = viewModel::goBack, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }

        if (state.step is AuthStep.Identifier) {
            SchoolRegistrationPrompt(
                onRegisterSchool = viewModel::startRegisterSchoolDirect
            )

        }
    }
}



@Composable
fun SchoolRegistrationPrompt(
    onRegisterSchool: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Spacer(Modifier.height(18.dp))

        VDivider()

        Spacer(Modifier.height(16.dp))


        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(c.card)
                .border(
                    width = 1.dp,
                    color = c.ink3.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(c.teal.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = VIcons.School,
                    contentDescription = null,
                    tint = c.tealDeep,
                    modifier = Modifier.size(22.dp)
                )
            }


            Spacer(Modifier.height(10.dp))


            Text(
                text = "Are you setting up a school?",
                style = VTheme.type.bodyStrong.colored(c.ink),
                textAlign = TextAlign.Center
            )


            Spacer(Modifier.height(5.dp))


            Text(
                text = "Create your administrator account and bring your school onto VidyaPrayag.",
                style = VTheme.type.caption.colored(c.ink3),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )


            Spacer(Modifier.height(12.dp))


            TextButton(
                onClick = onRegisterSchool
            ) {
                Text(
                    text = "Register my school →",
                    style = VTheme.type.bodyStrong.colored(c.tealDeep)
                )
            }
        }
    }
}