package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.ObPayloadKeys
import com.littlebridge.enrollplus.feature.admin.domain.model.ObStepType
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingBasics
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.OnboardingRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * ViewModel for the **first** onboarding step: Institutional Basics.
 *
 * Maintains local form state in [OnboardingBasics] (unchanged so the screen
 * doesn't need a rewrite) and exposes loading / error state alongside it.
 *
 * Calling [submit] persists the current form values to the backend via
 * `POST /api/v1/onboarding/submit` with `ob_step_type = "BASIC"`, and on
 * success invokes the supplied lambda so the screen can navigate forward.
 */
class InstitutionalBasicOBViewModel(
    private val onboardingRepository: OnboardingRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingBasics())
    val state: StateFlow<OnboardingBasics> = _state.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ---------- Form mutations (signatures unchanged) ----------
    fun updateSchoolName(name: String) {
        _state.value = _state.value.copy(schoolName = name)
    }

    fun updateBoard(board: String) {
        _state.value = _state.value.copy(boardAffiliation = board)
    }

    fun updateEmail(email: String) {
        _state.value = _state.value.copy(officialEmail = email)
    }

    fun updateContact(number: String) {
        _state.value = _state.value.copy(contactNumber = number)
    }

    fun updateCountryCode(code: String) {
        _state.value = _state.value.copy(countryCode = code)
    }

    /**
     * Apply a real device location captured via "Use current location"
     * (report §11.2). We update the displayed address from the reverse-geocoded
     * line when available, and stash lat/lng + parsed parts for the BASIC
     * submit payload so the backend can persist schools.latitude/longitude.
     */
    fun applyCapturedLocation(
        latitude: Double,
        longitude: Double,
        fullAddress: String?,
        city: String?,
        district: String?,
        state: String?,
        pincode: String?
    ) {
        val current = _state.value
        _state.value = current.copy(
            latitude = latitude,
            longitude = longitude,
            address = fullAddress?.takeIf { it.isNotBlank() } ?: current.address,
            city = city ?: current.city,
            district = district ?: current.district,
            state = state ?: current.state,
            pincode = pincode ?: current.pincode
        )
    }

    /** Manual address edit (used as a fallback when GPS is unavailable). */
    fun updateAddress(address: String) {
        _state.value = _state.value.copy(address = address)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * POST the current basics to /api/v1/onboarding/submit with
     * ob_step_type = "BASIC", and on success call [onSuccess] (typically a
     * navigation to the next screen).
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

            val current = _state.value
            val payload = JsonObject(
                buildMap {
                    put(ObPayloadKeys.SCHOOL_NAME, JsonPrimitive(current.schoolName.trim()))
                    put(ObPayloadKeys.BOARD, JsonPrimitive(current.boardAffiliation.trim()))
                    // Only send the email when the admin actually entered one, so a
                    // blank Step-1 form never overwrites the real contact email that
                    // was captured at school registration.
                    current.officialEmail.trim().takeIf { it.isNotBlank() }
                        ?.let { put(ObPayloadKeys.CONTACT_EMAIL, JsonPrimitive(it)) }
                    val phone = "${current.countryCode}${current.contactNumber}".trim()
                    phone.takeIf { current.contactNumber.isNotBlank() }
                        ?.let { put(ObPayloadKeys.CONTACT_PHONE, JsonPrimitive(it)) }
                    // The screen currently shows the address as a static string; we
                    // still send whatever is in state so the backend has *something*.
                    put(ObPayloadKeys.FULL_ADDRESS, JsonPrimitive(current.address.trim()))
                    // Geo from "Use current location" — only sent once captured so
                    // we never overwrite a real fix with nulls (report §11.2).
                    current.latitude?.let { put(ObPayloadKeys.LATITUDE, JsonPrimitive(it)) }
                    current.longitude?.let { put(ObPayloadKeys.LONGITUDE, JsonPrimitive(it)) }
                    current.city.takeIf { it.isNotBlank() }
                        ?.let { put(ObPayloadKeys.CITY, JsonPrimitive(it.trim())) }
                    current.district.takeIf { it.isNotBlank() }
                        ?.let { put(ObPayloadKeys.DISTRICT, JsonPrimitive(it.trim())) }
                    current.state.takeIf { it.isNotBlank() }
                        ?.let { put(ObPayloadKeys.STATE, JsonPrimitive(it.trim())) }
                    current.pincode.takeIf { it.isNotBlank() }
                        ?.let { put(ObPayloadKeys.PINCODE, JsonPrimitive(it.trim())) }
                }
            )

            val request = OnboardingSubmitRequest(
                obStepType = ObStepType.BASIC,
                isFinalSubmission = false,
                dataPayload = payload
            )

            when (val result = onboardingRepository.submitStep(token, request)) {
                is NetworkResult.Success -> {
                    AppLogger.d(
                        "OnboardingBasic",
                        "BASIC step submitted. nextStep=${result.data.nextStep} complete=${result.data.isOnboardingComplete}"
                    )
                    _isSubmitting.value = false
                    onSuccess()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("OnboardingBasic", "Submit failed: ${result.message} (code=${result.code})")
                    // A 401 here means the saved token was rejected by the server
                    // (expired, or the app is pointing at a different backend than
                    // the one that issued it). Make the cause explicit instead of
                    // showing the raw server "Session expired" string.
                    _errorMessage.value = if (result.code == 401) {
                        preferenceRepository.clearSession()
                        "Your session expired. Please sign in again before continuing onboarding."
                    } else {
                        result.message
                    }
                    _isSubmitting.value = false
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("OnboardingBasic", "Connection error while submitting BASIC step")
                    _errorMessage.value = "No internet connection. Please try again."
                    _isSubmitting.value = false
                }
            }
        }
    }
}
