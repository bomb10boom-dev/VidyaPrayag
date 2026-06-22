package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.ObStepType
import com.littlebridge.enrollplus.feature.admin.domain.repository.OnboardingRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Resolution of the post-login onboarding gate for a school admin.
 *
 *  - [Resolving]  — the /status round-trip is in flight (show a quiet frame).
 *  - [Onboarding] — server reports the school is NOT fully set up; the wizard
 *                   should open at [resumeStep] (first incomplete step).
 *  - [Dashboard]  — server reports onboarding is complete; open the portal.
 */
sealed class OnboardingGate {
    data object Resolving : OnboardingGate()
    data class Onboarding(val resumeStep: String, val completionPercent: Int) : OnboardingGate()
    data object Dashboard : OnboardingGate()
}

/**
 * Drives the post-login decision **from server truth**, not a local flag.
 *
 * THE BUG THIS FIXES: the previous gate trusted only the locally-persisted
 * `profile_completed` flag (written from the login response). A school account
 * inserted directly into the DB — or whose `profile_completed`/`onboarded_at`
 * was hand-set — would therefore be reported as fully onboarded even when its
 * `schools` row / classes did not actually exist, dropping the admin straight
 * onto an empty dashboard with onboarding wrongly marked done.
 *
 * This ViewModel calls `GET /api/v1/onboarding/status`, whose result is DERIVED
 * from real persisted data (school row, branding, classes, onboarded_at). So
 * the gate now reflects what the database actually contains, and resumes a
 * partially-onboarded admin at the correct step instead of skipping it.
 *
 * Fail-safe: on a network/connection error we DO NOT silently send the admin to
 * a possibly-empty dashboard. We fall back to the locally-persisted flag only
 * when it says "completed" (so an offline returning admin who genuinely finished
 * still gets in); otherwise we route to onboarding at BASIC.
 */
class OnboardingGateViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val _gate = MutableStateFlow<OnboardingGate>(OnboardingGate.Resolving)
    val gate: StateFlow<OnboardingGate> = _gate.asStateFlow()

    /** Re-run the resolution (also called once automatically on creation). */
    fun resolve() {
        viewModelScope.launch {
            _gate.value = OnboardingGate.Resolving
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                // No token at all → cannot be authenticated; safest is onboarding.
                _gate.value = OnboardingGate.Onboarding(ObStepType.BASIC, 0)
                return@launch
            }

            when (val result = onboardingRepository.getStatus(token)) {
                is NetworkResult.Success -> {
                    val status = result.data
                    AppLogger.d(
                        "OnboardingGate",
                        "status: complete=${status.isComplete} percent=${status.completionPercent} resume=${status.resumeStep}"
                    )
                    // Keep the local flag in lock-step with server truth so other
                    // call sites that still read it stay consistent.
                    preferenceRepository.setProfileCompleted(status.isComplete)
                    _gate.value = if (status.isComplete) {
                        OnboardingGate.Dashboard
                    } else {
                        OnboardingGate.Onboarding(status.resumeStep, status.completionPercent)
                    }
                }
                is NetworkResult.Error -> {
                    AppLogger.e("OnboardingGate", "status failed: ${result.message} (code=${result.code})")
                    // Fall back to the local flag, but only TRUST a "completed"
                    // value — never fabricate completion from an error.
                    val localCompleted = preferenceRepository.getProfileCompleted().first() ?: false
                    _gate.value = if (localCompleted) OnboardingGate.Dashboard
                    else OnboardingGate.Onboarding(ObStepType.BASIC, 0)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("OnboardingGate", "status connection error")
                    val localCompleted = preferenceRepository.getProfileCompleted().first() ?: false
                    _gate.value = if (localCompleted) OnboardingGate.Dashboard
                    else OnboardingGate.Onboarding(ObStepType.BASIC, 0)
                }
            }
        }
    }

    init {
        resolve()
    }
}
