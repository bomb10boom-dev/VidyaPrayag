package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardActivity
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardAnalytics
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardOverview
import com.littlebridge.enrollplus.feature.admin.domain.model.AdminDashboardSummary
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStep
import com.littlebridge.enrollplus.feature.admin.domain.repository.AdminDashboardRepository
import com.littlebridge.enrollplus.feature.auth.domain.model.OnboardingStepData
import com.littlebridge.enrollplus.feature.auth.domain.model.UserDetailsData
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * High-level onboarding state of the logged-in school admin, as known by the
 * server. Mirrors the values the API returns under
 * `data.onboarding_details.onboarding_status`.
 */
enum class DashboardOnboardingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    /** Returned when the response hasn't loaded yet, or didn't match a known value. */
    UNKNOWN;

    companion object {
        fun fromServer(value: String?): DashboardOnboardingStatus = when (value?.uppercase()) {
            "NOT_STARTED" -> NOT_STARTED
            "IN_PROGRESS" -> IN_PROGRESS
            "COMPLETED" -> COMPLETED
            else -> UNKNOWN
        }
    }
}

/**
 * Drives the SchoolDashboard. Pulls `GET /api/v1/user/details` on init and
 * exposes:
 *
 *  - [steps]: the four onboarding steps with real per-step `status`
 *  - [progress]: completed / total (0f..1f)
 *  - [onboardingStatus]: high-level state (NOT_STARTED / IN_PROGRESS / COMPLETED)
 *  - [adminName]: who to greet in the welcome card
 *  - [isLoading], [errorMessage]: standard loading state
 *
 * The screen uses these to decide whether to show:
 *  (a) The "Welcome, Admin — let's onboard" hero with real progress + a
 *      Start/Continue button that jumps to the first PENDING step, OR
 *  (b) The "All set, your campus is live" hero (when COMPLETED).
 */
class SchoolDashboardViewModel(
    private val authRepository: AuthRepository,
    private val preferenceRepository: PreferenceRepository,
    private val dashboardRepository: AdminDashboardRepository
) : ViewModel() {

    private val _steps = MutableStateFlow<List<OnboardingStep>>(DEFAULT_STEPS)
    val steps: StateFlow<List<OnboardingStep>> = _steps.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _onboardingStatus = MutableStateFlow(DashboardOnboardingStatus.UNKNOWN)
    val onboardingStatus: StateFlow<DashboardOnboardingStatus> = _onboardingStatus.asStateFlow()

    private val _adminName = MutableStateFlow("Admin")
    val adminName: StateFlow<String> = _adminName.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---- Redesigned home dashboard payloads (summary / analytics / activity) ----
    // Each is null until its endpoint resolves so the UI can fall back to a
    // skeleton / honest empty state and never renders fabricated numbers.
    private val _summary = MutableStateFlow<AdminDashboardSummary?>(null)
    val summary: StateFlow<AdminDashboardSummary?> = _summary.asStateFlow()

    private val _analytics = MutableStateFlow<AdminDashboardAnalytics?>(null)
    val analytics: StateFlow<AdminDashboardAnalytics?> = _analytics.asStateFlow()

    private val _activity = MutableStateFlow<AdminDashboardActivity?>(null)
    val activity: StateFlow<AdminDashboardActivity?> = _activity.asStateFlow()

    // ---- Consolidated command-center overview (the redesigned home's canonical
    // source). Null until the /overview endpoint resolves so the UI shows a
    // skeleton and never renders fabricated numbers. ----
    private val _overview = MutableStateFlow<AdminDashboardOverview?>(null)
    val overview: StateFlow<AdminDashboardOverview?> = _overview.asStateFlow()

    init {
        refresh()
    }

    /**
     * Re-fetch everything the home screen needs:
     *   1. `/user/details`             — onboarding progress + greeting (existing)
     *   2. `/api/admin/dashboard/`    — summary, analytics, activity (new)
     *
     * Called on init and any time we want the dashboard to re-sync (screen
     * resume or post-onboarding navigation). The three dashboard reads are
     * best-effort: a failure on any one of them logs and leaves that section in
     * its previous (null/empty) state without blocking the others or surfacing a
     * blocking error — onboarding/user-details remains the source of the
     * top-level loading + error state.
     */

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("SchoolDashboardVM", "No auth token in prefs; skipping refresh")
                _isLoading.value = false
                return@launch
            }

            when (val result = authRepository.getUserDetails(token)) {
                is NetworkResult.Success -> applyUserDetails(result.data.data)
                is NetworkResult.Error -> {
                    _errorMessage.value = result.message
                    AppLogger.e("SchoolDashboardVM", "getUserDetails failed: ${result.message}")
                }
                is NetworkResult.ConnectionError -> {
                    _errorMessage.value = "Connection error"
                    AppLogger.e("SchoolDashboardVM", "getUserDetails connection error")
                }
            }

            loadDashboard(token)

            _isLoading.value = false
        }
    }

    /**
     * Best-effort fetch of the three dashboard endpoints. Each result is applied
     * independently; a failure on one does not clear the others. The admin name
     * from the summary header takes precedence over the user-details name when
     * present (it's the same person but the summary is the home's canonical
     * source).
     */
    private suspend fun loadDashboard(token: String) {
        // The consolidated overview is the redesigned home's canonical source.
        // It is fetched first and, when present, supplies the admin greeting name.
        when (val r = dashboardRepository.getOverview(token)) {
            is NetworkResult.Success -> {
                r.data.data?.let { o ->
                    _overview.value = o
                    o.header.adminName.takeIf { it.isNotBlank() }?.let { _adminName.value = it }
                }
            }
            is NetworkResult.Error -> AppLogger.e("SchoolDashboardVM", "getOverview failed: ${r.message}")
            is NetworkResult.ConnectionError -> AppLogger.e("SchoolDashboardVM", "getOverview connection error")
        }

        when (val r = dashboardRepository.getSummary(token)) {
            is NetworkResult.Success -> {
                r.data.data?.let { s ->
                    _summary.value = s
                    s.admin.name.takeIf { it.isNotBlank() }?.let { _adminName.value = it }
                }
            }
            is NetworkResult.Error -> AppLogger.e("SchoolDashboardVM", "getSummary failed: ${r.message}")
            is NetworkResult.ConnectionError -> AppLogger.e("SchoolDashboardVM", "getSummary connection error")
        }

        when (val r = dashboardRepository.getAnalytics(token)) {
            is NetworkResult.Success -> r.data.data?.let { _analytics.value = it }
            is NetworkResult.Error -> AppLogger.e("SchoolDashboardVM", "getAnalytics failed: ${r.message}")
            is NetworkResult.ConnectionError -> AppLogger.e("SchoolDashboardVM", "getAnalytics connection error")
        }

        when (val r = dashboardRepository.getActivity(token)) {
            is NetworkResult.Success -> r.data.data?.let { _activity.value = it }
            is NetworkResult.Error -> AppLogger.e("SchoolDashboardVM", "getActivity failed: ${r.message}")
            is NetworkResult.ConnectionError -> AppLogger.e("SchoolDashboardVM", "getActivity connection error")
        }
    }

    /**
     * The first step the user still needs to complete. Used by the
     * "Start/Continue Onboarding" button. Returns null when everything is
     * COMPLETED or the data hasn't loaded yet.
     */
    fun firstPendingStep(): OnboardingStep? =
        _steps.value.firstOrNull { it.status.equals(OnboardingStep.STATUS_PENDING, ignoreCase = true) }
            ?: _steps.value.firstOrNull { !it.status.equals(OnboardingStep.STATUS_COMPLETED, ignoreCase = true) }

    private fun applyUserDetails(data: UserDetailsData) {
        _adminName.value = data.personalDetails.name.takeIf { it.isNotBlank() } ?: "Admin"

        val ob = data.onboardingDetails
        _onboardingStatus.value = DashboardOnboardingStatus.fromServer(ob.onboardingStatus)

        // Map server step list → UI model. We use the order the server gives
        // us; that order is canonical (BASIC, BRANDING, ACADEMIC, REVIEW).
        val mapped = ob.listOfSteps.mapIndexed { idx, s -> s.toUiStep(idx + 1) }

        // Belt-and-braces: if the server's per-step status disagrees with the
        // top-level onboardingStatus = COMPLETED, treat all steps as completed
        // for the dashboard. This protects the user experience while the
        // server-side status-rollup quirk is fixed (the response we observed
        // had onboardingStatus=COMPLETED but BRANDING=PENDING and
        // ACADEMIC=LOCKED, which would otherwise force the user back into
        // onboarding).
        val finalSteps = if (_onboardingStatus.value == DashboardOnboardingStatus.COMPLETED) {
            mapped.map { it.copy(status = OnboardingStep.STATUS_COMPLETED, isEnabled = true) }
        } else {
            mapped
        }

        _steps.value = if (finalSteps.isNotEmpty()) finalSteps else DEFAULT_STEPS
        _progress.value = computeProgress(_steps.value)
    }

    private fun computeProgress(steps: List<OnboardingStep>): Float {
        if (steps.isEmpty()) return 0f
        val completed = steps.count { it.isCompleted }
        return completed.toFloat() / steps.size
    }

    private companion object {
        /** Shown until `/user/details` resolves. Same titles the server uses. */
        val DEFAULT_STEPS: List<OnboardingStep> = listOf(
            OnboardingStep(
                id = 1,
                serverKey = OnboardingStep.SERVER_KEY_BASIC,
                title = "Institutional Basics",
                description = "School name, location, and IDs.",
                status = OnboardingStep.STATUS_PENDING,
                isEnabled = true
            ),
            OnboardingStep(
                id = 2,
                serverKey = OnboardingStep.SERVER_KEY_BRANDING,
                title = "Branding & Identity",
                description = "Upload logos and color themes.",
                status = OnboardingStep.STATUS_LOCKED,
                isEnabled = false
            ),
            OnboardingStep(
                id = 3,
                serverKey = OnboardingStep.SERVER_KEY_ACADEMIC,
                title = "Academic Setup",
                description = "Classes, subjects, and teachers.",
                status = OnboardingStep.STATUS_LOCKED,
                isEnabled = false
            ),
            OnboardingStep(
                id = 4,
                serverKey = OnboardingStep.SERVER_KEY_REVIEW,
                title = "Final Launch",
                description = "Verify and go live.",
                status = OnboardingStep.STATUS_LOCKED,
                isEnabled = false
            )
        )
    }
}

/**
 * Convert the server's per-step description into the UI model. We infer the
 * canonical [OnboardingStep.serverKey] from the server's display name (the
 * server doesn't return the key directly on this endpoint).
 */
private fun OnboardingStepData.toUiStep(id: Int): OnboardingStep {
    val key = when {
        name.contains("Basic", ignoreCase = true) -> OnboardingStep.SERVER_KEY_BASIC
        name.contains("Brand", ignoreCase = true) -> OnboardingStep.SERVER_KEY_BRANDING
        name.contains("Academic", ignoreCase = true) -> OnboardingStep.SERVER_KEY_ACADEMIC
        name.contains("Launch", ignoreCase = true) ||
                name.contains("Review", ignoreCase = true) -> OnboardingStep.SERVER_KEY_REVIEW
        else -> when (id) {
            1 -> OnboardingStep.SERVER_KEY_BASIC
            2 -> OnboardingStep.SERVER_KEY_BRANDING
            3 -> OnboardingStep.SERVER_KEY_ACADEMIC
            else -> OnboardingStep.SERVER_KEY_REVIEW
        }
    }
    return OnboardingStep(
        id = id,
        serverKey = key,
        title = name,
        description = description,
        status = status.uppercase(),
        // We deliberately drop the server's `icon` URL here — the lh3
        // googleusercontent links are private and 403 on device. The screen
        // uses a Material icon fallback.
        iconUrl = null,
        isEnabled = isEnabled
    )
}
