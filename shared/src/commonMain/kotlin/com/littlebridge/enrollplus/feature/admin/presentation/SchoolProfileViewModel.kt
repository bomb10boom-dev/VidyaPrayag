/*
 * File: SchoolProfileViewModel.kt
 * Module: feature.admin.presentation
 *
 * RA-47: drives the editable institutional-profile screen. Loads the schools
 * row via GET /api/v1/school/profile, lets the admin edit the core fields, and
 * persists via PUT /api/v1/school/profile (server enforces school-admin + JWT
 * school scoping). Three states (loading / error+retry / loaded) so the screen
 * is never a bare happy-path form.
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateSchoolProfileRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.SchoolProfileRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SchoolProfileState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val loadError: String? = null,    // fatal load failure → show retry
    val errorMessage: String? = null, // transient save error → toast
    val infoMessage: String? = null,
    val saved: Boolean = false,

    // Editable fields
    val name: String = "",
    val board: String = "",
    val medium: String = "",
    val schoolGender: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val principalName: String = "",
    val principalPhone: String = "",
    val principalEmail: String = "",
    val fullAddress: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val pincode: String = "",
    val logoUrl: String = "",
    val brandColor: String = "#2563EB"
)

class SchoolProfileViewModel(
    private val repository: SchoolProfileRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolProfileState())
    val state: StateFlow<SchoolProfileState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, loadError = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, loadError = "You are not signed in. Please log in again.")
                return@launch
            }
            when (val r = repository.getProfile(token)) {
                is NetworkResult.Success -> {
                    val d = r.data.data
                    if (d == null) {
                        _state.value = _state.value.copy(isLoading = false, loadError = "School profile not found.")
                        return@launch
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        loadError = null,
                        name = d.name,
                        board = d.board,
                        medium = d.medium,
                        schoolGender = d.schoolGender,
                        contactPhone = d.contactPhone.orEmpty(),
                        contactEmail = d.contactEmail.orEmpty(),
                        principalName = d.principalName.orEmpty(),
                        principalPhone = d.principalPhone.orEmpty(),
                        principalEmail = d.principalEmail.orEmpty(),
                        fullAddress = d.fullAddress.orEmpty(),
                        city = d.city,
                        district = d.district,
                        state = d.state,
                        pincode = d.pincode.orEmpty(),
                        logoUrl = d.logoUrl.orEmpty(),
                        brandColor = d.brandColor
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolProfileVM", "getProfile error: ${r.message}")
                    _state.value = _state.value.copy(isLoading = false, loadError = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, loadError = "Connection error. Check your internet.")
                }
            }
        }
    }

    // -------- field editors --------
    fun onName(v: String) { _state.value = _state.value.copy(name = v) }
    fun onBoard(v: String) { _state.value = _state.value.copy(board = v) }
    fun onMedium(v: String) { _state.value = _state.value.copy(medium = v) }
    fun onSchoolGender(v: String) { _state.value = _state.value.copy(schoolGender = v) }
    fun onContactPhone(v: String) { _state.value = _state.value.copy(contactPhone = v) }
    fun onContactEmail(v: String) { _state.value = _state.value.copy(contactEmail = v) }
    fun onPrincipalName(v: String) { _state.value = _state.value.copy(principalName = v) }
    fun onPrincipalPhone(v: String) { _state.value = _state.value.copy(principalPhone = v) }
    fun onPrincipalEmail(v: String) { _state.value = _state.value.copy(principalEmail = v) }
    fun onFullAddress(v: String) { _state.value = _state.value.copy(fullAddress = v) }
    fun onCity(v: String) { _state.value = _state.value.copy(city = v) }
    fun onDistrict(v: String) { _state.value = _state.value.copy(district = v) }
    fun onState(v: String) { _state.value = _state.value.copy(state = v) }
    fun onPincode(v: String) { _state.value = _state.value.copy(pincode = v) }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null, saved = false)
    }

    fun save() {
        val s = _state.value
        if (s.name.isBlank() || s.city.isBlank() || s.district.isBlank()) {
            _state.value = s.copy(errorMessage = "Name, city and district are required.")
            return
        }
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(errorMessage = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(isSaving = true, errorMessage = null, infoMessage = null, saved = false)
            val req = UpdateSchoolProfileRequest(
                name = s.name.trim(),
                board = s.board.trim(),
                medium = s.medium.trim(),
                schoolGender = s.schoolGender.trim(),
                contactPhone = s.contactPhone.trim(),
                contactEmail = s.contactEmail.trim(),
                principalName = s.principalName.trim(),
                principalPhone = s.principalPhone.trim(),
                principalEmail = s.principalEmail.trim(),
                fullAddress = s.fullAddress.trim(),
                city = s.city.trim(),
                district = s.district.trim(),
                state = s.state.trim(),
                pincode = s.pincode.trim()
            )
            when (val r = repository.updateProfile(token, req)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false, infoMessage = "Institutional profile updated", saved = true)
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolProfileVM", "updateProfile error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isSaving = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }
}
