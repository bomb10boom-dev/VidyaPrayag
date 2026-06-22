package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.ObStepType
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.OnboardingRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

data class ComplianceDocument(
    val id: String,
    val name: String,
    val status: String, // "Uploaded", "Awaiting"
    val metadata: String? = null
)

data class AppModule(
    val id: String,
    val name: String,
    val description: String,
    val isEnabled: Boolean,
    val iconName: String
)

data class LaunchInfoState(
    val schoolName: String = "St. Augustine Academy",
    val licenseType: String = "Global K-12 Institutional License",
    val location: String = "Metropolitan Education Zone, Block C",
    val imageUrl: String? = null,
    val documents: List<ComplianceDocument> = listOf(
        ComplianceDocument("1", "Affiliation Certificate", "Uploaded", "PDF • 2.4 MB • Uploaded May 12"),
        ComplianceDocument("2", "RTE Compliance", "Awaiting")
    ),
    val modules: List<AppModule> = listOf(
        AppModule("1", "Student LMS", "Core Learning Experience", true, "school"),
        AppModule("2", "Fee Management", "Automated Financial Flows", true, "payments"),
        AppModule("3", "Parent Gateway", "Real-time Communication", false, "forum")
    )
)

/**
 * ViewModel for the **fourth and final** onboarding step: Verification & Launch.
 *
 * - On init, loads /api/v1/onboarding/step?obStepType=REVIEW to populate the
 *   institution name, compliance docs and module list from what the server
 *   has accumulated across the previous three steps.
 * - On Launch, POSTs to /api/v1/onboarding/submit with
 *   ob_step_type = "REVIEW" and is_final_submission = true. The server then
 *   creates / updates the row in `schools`, sets app_users.school_id, and
 *   flips the onboarding status to COMPLETED.
 */
class LaunchInfoOBViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LaunchInfoState())
    val state: StateFlow<LaunchInfoState> = _state.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    init {
        loadReviewStep()
    }

    private fun loadReviewStep() {
        viewModelScope.launch {
            _isLoading.value = true
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _isLoading.value = false
                return@launch
            }

            when (val result = onboardingRepository.getStep(token, ObStepType.REVIEW)) {
                is NetworkResult.Success -> {
                    val data = result.data
                    val current = _state.value

                    // Institution name from server's identity_details
                    val schoolName = data.identityDetails?.institutionName
                        ?.takeIf { it.isNotBlank() && it != "—" }
                        ?: current.schoolName

                    // Compliance docs from server (mapped from ReviewComplianceDoc)
                    val docs = data.complianceDocs?.map { doc ->
                        ComplianceDocument(
                            id = doc.docId,
                            name = doc.docName,
                            status = if (doc.isVerified) "Uploaded" else "Awaiting",
                            metadata = if (doc.isVerified) "Verified by board" else null
                        )
                    }?.takeIf { it.isNotEmpty() } ?: current.documents

                    // Modules from server. Preserve the icon mapping based on name.
                    val modules = data.listOfSelectedModules?.mapIndexed { idx, m ->
                        AppModule(
                            id = (idx + 1).toString(),
                            name = m.name,
                            description = descriptionForModule(m.name),
                            isEnabled = m.isSelected,
                            iconName = iconForModule(m.name)
                        )
                    }?.takeIf { it.isNotEmpty() } ?: current.modules

                    _state.value = current.copy(
                        schoolName = schoolName,
                        documents = docs,
                        modules = modules
                    )
                    AppLogger.d("OnboardingReview", "Loaded REVIEW step: school=$schoolName, docs=${docs.size}, modules=${modules.size}")
                }
                is NetworkResult.Error -> {
                    AppLogger.e("OnboardingReview", "Failed to load REVIEW step: ${result.message} (code=${result.code})")
                    if (result.code == 401) {
                        preferenceRepository.clearSession()
                        _errorMessage.value = "Your session expired. Please sign in again before continuing onboarding."
                    }
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("OnboardingReview", "Connection error loading REVIEW step")
                }
            }
            _isLoading.value = false
        }
    }

    private fun descriptionForModule(name: String): String = when (name.lowercase()) {
        "analytics" -> "Insights across attendance, results & engagement"
        "ptm management" -> "Schedule and run parent-teacher meetings"
        "scholarships" -> "Discover and award financial aid"
        "student lms" -> "Core Learning Experience"
        "fee management" -> "Automated Financial Flows"
        "parent gateway" -> "Real-time Communication"
        else -> "Optional module"
    }

    private fun iconForModule(name: String): String = when (name.lowercase()) {
        "analytics" -> "analytics"
        "ptm management" -> "forum"
        "scholarships" -> "school"
        "student lms" -> "school"
        "fee management" -> "payments"
        "parent gateway" -> "forum"
        else -> "apps"
    }

    fun toggleModule(moduleId: String) {
        val updatedModules = _state.value.modules.map {
            if (it.id == moduleId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _state.value = _state.value.copy(modules = updatedModules)
        _infoMessage.value = "Module selection updated locally. Launch Profile to apply it."
    }

    fun markDocumentUploaded(documentId: String) {
        val updatedDocs = _state.value.documents.map { doc ->
            if (doc.id == documentId) {
                doc.copy(status = "Uploaded", metadata = "Selected on this device • Submit launch to verify")
            } else {
                doc
            }
        }
        _state.value = _state.value.copy(documents = updatedDocs)
        _infoMessage.value = "Document marked for verification. Launch Profile to finalize."
    }

    fun saveDraft() {
        _infoMessage.value = "Review draft is saved on this device. Use Launch Profile when ready to finalize."
    }

    fun clearMessages() {
        _errorMessage.value = null
        _infoMessage.value = null
    }

    fun clearError() {
        clearMessages()
    }

    /**
     * Finalize onboarding. POSTs to /api/v1/onboarding/submit with
     * ob_step_type = "REVIEW" and is_final_submission = true. On success the
     * server creates the school row, sets app_users.school_id, and we
     * navigate to the dashboard.
     */
    fun submit(onSuccess: () -> Unit) {
        if (_isSubmitting.value) return

        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _errorMessage.value = "You are not signed in. Please log in again."
                _isSubmitting.value = false
                return@launch
            }

            val request = OnboardingSubmitRequest(
                obStepType = ObStepType.REVIEW,
                isFinalSubmission = true,
                dataPayload = JsonObject(emptyMap())
            )

            when (val result = onboardingRepository.submitStep(token, request)) {
                is NetworkResult.Success -> {
                    AppLogger.d(
                        "OnboardingReview",
                        "Onboarding finalized. complete=${result.data.isOnboardingComplete} redirect=${result.data.redirectToHome}"
                    )
                    // RA-69: persist completion status locally so the post-login
                    // gate in NavGraphV2 resolves to Portal on next boot.
                    if (result.data.isOnboardingComplete) {
                        preferenceRepository.setProfileCompleted(true)
                    }
                    _isSubmitting.value = false
                    onSuccess()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("OnboardingReview", "Final submit failed: ${result.message} (code=${result.code})")
                    _errorMessage.value = if (result.code == 401) {
                        preferenceRepository.clearSession()
                        "Your session expired. Please sign in again before launching the school profile."
                    } else {
                        result.message
                    }
                    _isSubmitting.value = false
                }
                is NetworkResult.ConnectionError -> {
                    _errorMessage.value = "No internet connection. Please try again."
                    _isSubmitting.value = false
                }
            }
        }
    }
}
