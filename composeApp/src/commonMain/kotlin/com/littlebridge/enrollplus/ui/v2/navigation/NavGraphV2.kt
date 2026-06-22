package com.littlebridge.enrollplus.ui.v2.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import com.littlebridge.enrollplus.feature.admin.presentation.OnboardingGate
import com.littlebridge.enrollplus.feature.admin.presentation.OnboardingGateViewModel
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import com.littlebridge.enrollplus.ui.v2.screens.auth.AdminAuthScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.CommonLandingScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.LegalDoc
import com.littlebridge.enrollplus.ui.v2.screens.auth.LegalInfoScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.ParentAuthScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.ParentLinkChildScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.SchoolOnboardingScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.TeacherFirstLoginScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.screens.discovery.DiscoveryScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.parent.ParentPortalV2
import com.littlebridge.enrollplus.ui.v2.screens.school.SchoolPortalV2
import com.littlebridge.enrollplus.ui.v2.screens.teacher.TeacherPortalV2
import com.littlebridge.enrollplus.ui.v2.theme.VMotion
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * NavGraphV2 — the single source of nav truth for the `ui/v2` entry experience (PHASE 7).
 *
 * This is the Compose translation of the target entry flow. Navigation is **state-driven** (no flat
 * NavHost): the persisted session decides auth-vs-portal, and small enum state machines own the
 * unauth funnel and the post-login gate. Every transition is explicit and every post-auth jump pops
 * its predecessor so back-press can never return to splash, landing, or an auth screen (LAW 4).
 *
 *   Splash (in App.kt)
 *     ├─ valid session → [AuthedFlow] → role gate → correct portal
 *     └─ no session    → [UnauthFlow] → CommonLanding → Parent/Admin auth
 *
 * Role is the persisted JWT role; [EntryRole] normalizes it (handles ADMIN / SCHOOL_ADMIN / TEACHER
 * / PARENT) so no decision site hardcodes a raw string.
 */
@Composable
fun NavGraphV2(
    role: String?,
    isAuthenticated: Boolean,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val entryRole = EntryRole.from(role)
    val tone = entryRole.tone(isAuthenticated)

    VTheme(tone = tone) {
        if (isAuthenticated) {
            AuthedFlow(role = entryRole, onLogout = onLogout, modifier = modifier)
        } else {
            UnauthFlow(modifier = modifier)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Role model — one place that turns the persisted string into a typed role.
// ─────────────────────────────────────────────────────────────────────────────

/** Typed app role, parsed once from the persisted JWT role string (LAW: no scattered role literals). */
enum class EntryRole {
    Parent, SchoolAdmin, SuperAdmin, Teacher, Unknown;

    fun tone(isAuthenticated: Boolean): VPortalTone = when {
        !isAuthenticated -> VPortalTone.Light
        this == SchoolAdmin || this == SuperAdmin || this == Teacher -> VPortalTone.Warm
        else -> VPortalTone.Light
    }

    companion object {
        fun from(raw: String?): EntryRole = when (raw?.trim()?.uppercase()) {
            "PARENT" -> Parent
            "ADMIN", "SCHOOL_ADMIN", "SCHOOLADMIN" -> SchoolAdmin
            // Audit §3.5: super_admin was previously unmapped → Unknown → Parent
            // portal. It is an operator/admin role, so it lands on the admin surface.
            "SUPER_ADMIN", "SUPERADMIN" -> SuperAdmin
            "TEACHER" -> Teacher
            else -> Unknown
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Unauthenticated funnel:  CommonLanding → Parent/Admin auth (+ discovery/link/onboard branches)
// ─────────────────────────────────────────────────────────────────────────────

private enum class UnauthRoute { Landing, ParentAuth, AdminAuth, Discovery, ParentLinkChild, SchoolOnboarding, Legal }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UnauthFlow(modifier: Modifier = Modifier) {
    var route by remember { mutableStateOf(UnauthRoute.Landing) }
    // Which legal/info document the Legal route opens on (Privacy / Terms / Help Desk).
    var legalDoc by remember { mutableStateOf(LegalDoc.Privacy) }

    // System back: collapse the funnel toward the landing screen (never exit from a leaf).
    BackHandler(enabled = route != UnauthRoute.Landing) {
        route = when (route) {
            UnauthRoute.ParentAuth -> UnauthRoute.Landing
            UnauthRoute.AdminAuth -> UnauthRoute.Landing
            UnauthRoute.Discovery -> UnauthRoute.ParentAuth
            UnauthRoute.ParentLinkChild -> UnauthRoute.Discovery
            UnauthRoute.SchoolOnboarding -> UnauthRoute.AdminAuth
            // Legal/Support is a leaf reachable from the landing footer — back returns there.
            UnauthRoute.Legal -> UnauthRoute.Landing
            UnauthRoute.Landing -> UnauthRoute.Landing
        }
    }

    AnimatedContent(
        targetState = route,
        // Funnel screens advance "deeper" → subtle forward horizontal momentum + fade.
        transitionSpec = { VMotion.forwardSlide() },
        label = "unauth-flow",
        modifier = modifier,
    ) { current ->
        when (current) {
            // The single landing surface for BOTH roles (PHASE 7). Its two role-entry cards are the
            // only auth CTAs: "I'm a Parent" → [onParent] → OTP funnel; "School / Administration" →
            // [onAdmin] → credential funnel (teachers sign in via the Admin path). A tap on any
            // Featured-Institution card or Portal-access row also funnels into the matching auth
            // screen (a school tap leads families into the parent OTP sign-in). Content (hero copy,
            // featured schools, offerings, portals) is CMS-driven inside the screen itself via
            // LandingViewModel + MainViewModel — both fetch in `init`, so no extra wiring is needed
            // here; this site only supplies the navigation callbacks.
            UnauthRoute.Landing -> CommonLandingScreenV2(
                onParent = { route = UnauthRoute.ParentAuth },
                onAdmin = { route = UnauthRoute.AdminAuth },
                // Footer "Privacy Policy / Terms of Service / Help Desk" + the continue-footnote
                // open the public Legal & Support surface on the requested document.
                onLegal = { doc ->
                    legalDoc = doc
                    route = UnauthRoute.Legal
                },
            )
            UnauthRoute.ParentAuth -> ParentAuthScreenV2(
                // On success the persisted session flips isAuthenticated=true and NavGraphV2
                // recomposes into AuthedFlow, which runs the child-link gate (PHASE 6).
                onAuthSuccess = {},
                onBack = { route = UnauthRoute.Landing },
            )
            UnauthRoute.AdminAuth -> AdminAuthScreenV2(
                // On success the session flips and AuthedFlow runs the onboard / first-login gate.
                onAuthSuccess = {},
                onBack = { route = UnauthRoute.Landing },
            )
            // Browse-first marketplace, reachable from the parent path for new families.
            UnauthRoute.Discovery -> DiscoveryScreenV2(
                onOpenSchool = { _ -> route = UnauthRoute.ParentLinkChild },
            )
            UnauthRoute.ParentLinkChild -> ParentLinkChildScreenV2(
                onDone = { route = UnauthRoute.ParentAuth },
                onBack = { route = UnauthRoute.Discovery },
            )
            UnauthRoute.SchoolOnboarding -> SchoolOnboardingScreenV2(
                onComplete = { route = UnauthRoute.AdminAuth },
                onBack = { route = UnauthRoute.AdminAuth },
            )
            // Public Privacy Policy / Terms of Service / Help Desk surface (minimal, honest copy +
            // live support email). Opens on the document the footer link requested.
            UnauthRoute.Legal -> LegalInfoScreenV2(
                onBack = { route = UnauthRoute.Landing },
                initial = legalDoc,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Authenticated gate:  role → (child-link | onboarding | first-login) → portal
// ─────────────────────────────────────────────────────────────────────────────

private enum class AuthedRoute { Resolving, ParentLinkChild, SchoolOnboarding, TeacherFirstLogin, Portal }

/**
 * AuthedFlow — runs the one-time post-login gate before handing control to the role portal.
 *
 * Decision inputs:
 *  • PARENT       — lands straight on the portal (child-link is opt-in, not forced).
 *  • SCHOOL_ADMIN — decided by SERVER TRUTH via `GET /api/v1/onboarding/status`
 *                   (see [OnboardingGateViewModel]). NOT the local `profile_completed`
 *                   flag. This fixes the class of bug where a manually-inserted /
 *                   hand-edited admin row reported onboarding as complete while its
 *                   `schools` row / classes did not actually exist — the admin would
 *                   land on an empty dashboard with onboarding wrongly skipped. The
 *                   status endpoint derives completion from real persisted data, and
 *                   also yields the first incomplete step so a partially-onboarded
 *                   admin RESUMES at the right place.
 *  • TEACHER      — `profileCompleted == false` → first-login change-password gate, else portal.
 *
 * The change-password gate (RA-54) is backed by a real `POST /auth/change-password` +
 * `must_change_password` flag.
 */
@Composable
private fun AuthedFlow(
    role: EntryRole,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authRepository = koinInject<AuthRepository>()

    var route by remember(role) { mutableStateOf(AuthedRoute.Resolving) }
    // The step the school-onboarding wizard should open on, resolved from the
    // server status (first incomplete step) for a returning/partial admin.
    var onboardingResumeStep by remember(role) { mutableStateOf(com.littlebridge.enrollplus.feature.admin.domain.model.ObStepType.BASIC) }

    // For school roles, the decision is made by the server-truth gate VM below
    // (it sets `route`). The gate is AUTHORITATIVE: OnboardingGateViewModel reads
    // the server /onboarding/status (derived from real persisted school data, not
    // the local profile_completed flag) and yields both the Dashboard/Onboarding
    // decision AND the first incomplete step so a partial/manually-seeded admin
    // RESUMES at the right place instead of being wrongly dropped on an empty
    // dashboard ("shows onboarding completed" bug). For the other roles we resolve
    // locally as before.
    val isSchoolRole = role == EntryRole.SchoolAdmin || role == EntryRole.SuperAdmin

    if (isSchoolRole) {
        val gateVm: OnboardingGateViewModel = org.koin.compose.viewmodel.koinViewModel()
        val gate by gateVm.gate.collectAsStateV2()
        LaunchedEffect(gate) {
            when (val g = gate) {
                is OnboardingGate.Resolving -> route = AuthedRoute.Resolving
                is OnboardingGate.Onboarding -> {
                    onboardingResumeStep = g.resumeStep
                    route = AuthedRoute.SchoolOnboarding
                }
                is OnboardingGate.Dashboard -> route = AuthedRoute.Portal
            }
        }
    } else {
        // Resolve the gate exactly once per authenticated session.
        LaunchedEffect(role) {
            // Local cached flag (set at login from the server's profile_completed).
            // We do NOT default a missing flag to `true` — a missing/false flag
            // means "not completed".
            val localProfileCompleted = runCatching { authRepository.getSession()?.profileCompleted }
                .getOrNull() ?: false
            route = when (role) {
                // RA-S04: a parent is NEVER pushed into the child-link flow after signup/login.
                EntryRole.Parent -> AuthedRoute.Portal
                EntryRole.Teacher -> if (localProfileCompleted) AuthedRoute.Portal else AuthedRoute.TeacherFirstLogin
                EntryRole.Unknown -> AuthedRoute.Portal
                else -> AuthedRoute.Portal
            }
        }
    }

    // Inside a portal, back-press is owned by the portal's own tab logic; the gate screens never
    // allow a back path to auth/splash (the session is already established).
    AnimatedContent(
        targetState = route,
        // Gate steps (link-child / onboarding / first-login) read as modal sheets →
        // vertical rise + fade. The brief Resolving frame uses a quiet cross-fade so
        // the common (already-completed) path never shows a directional slide.
        transitionSpec = {
            if (initialState == AuthedRoute.Resolving || targetState == AuthedRoute.Resolving) {
                VMotion.quietFade()
            } else {
                VMotion.modalRise()
            }
        },
        label = "authed-flow",
        modifier = modifier,
    ) { current ->
        when (current) {
            // Brief resolving frame — themed background only, no spinner flash for the common
            // (already-completed) case which resolves on the first composition.
            AuthedRoute.Resolving -> androidx.compose.foundation.layout.Box(
                Modifier.then(modifier),
            ) {}

            AuthedRoute.ParentLinkChild -> ParentLinkChildScreenV2(
                onDone = { route = AuthedRoute.Portal },
                onBack = { route = AuthedRoute.Portal },
            )
            AuthedRoute.SchoolOnboarding -> SchoolOnboardingScreenV2(
                resumeStep = onboardingResumeStep,
                onComplete = { route = AuthedRoute.Portal },
                onBack = { route = AuthedRoute.Portal },
            )
            AuthedRoute.TeacherFirstLogin -> TeacherFirstLoginScreenV2(
                onDone = { route = AuthedRoute.Portal },
            )
            AuthedRoute.Portal -> RolePortal(role = role, onLogout = onLogout)
        }
    }
}

/** Maps the typed role to its self-contained, tabbed portal (the role's "dashboard"). */
@Composable
private fun RolePortal(role: EntryRole, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    when (role) {
        // super_admin currently shares the school-admin operator surface (no
        // dedicated super-admin portal exists yet) — but it is no longer
        // silently dropped into the parent UI (audit §3.5).
        EntryRole.SchoolAdmin, EntryRole.SuperAdmin -> SchoolPortalV2(onLogout = onLogout, modifier = modifier)
        EntryRole.Teacher -> TeacherPortalV2(onLogout = onLogout, modifier = modifier)
        EntryRole.Parent -> ParentPortalV2(onLogout = onLogout, modifier = modifier)
        // Authenticated but role unknown → safest default is the parent surface.
        EntryRole.Unknown -> ParentPortalV2(onLogout = onLogout, modifier = modifier)
    }
}
