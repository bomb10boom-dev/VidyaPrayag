/*
 * File: StaffViewModel.kt
 * Module: feature.admin.presentation
 *
 * RA-S17: drives the admin Non-teaching-staff roster (People → Staff sub-tab).
 * Loads the live `non_teaching_staff` rows via GET /api/v1/school/staff,
 * supports search (?q=) + department filter, adding a staff member (POST),
 * loading a single profile (GET {id}), editing (PATCH {id}) and removing one
 * (DELETE {id}). Three states (loading / error+retry / loaded, with an explicit
 * empty) so the roster is never a bare list.
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateStaffRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.StaffDto
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateStaffRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.StaffRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StaffRosterState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val staff: List<StaffDto> = emptyList(),
    val query: String = "",
    // add-staff dialog
    val isSaving: Boolean = false,
    val addError: String? = null,
    val infoMessage: String? = null,
    // profile / edit / delete
    val removingIds: Set<String> = emptySet()
)

class StaffViewModel(
    private val repository: StaffRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StaffRosterState())
    val state: StateFlow<StaffRosterState> = _state.asStateFlow()

    init { load() }

    fun load(query: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, error = "You are not signed in. Please log in again.")
                return@launch
            }
            val q = query ?: _state.value.query
            when (val r = repository.getStaff(token, query = q.ifBlank { null })) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false, error = null, staff = r.data.data?.staff.orEmpty())
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StaffVM", "getStaff error: ${r.message}")
                    _state.value = _state.value.copy(isLoading = false, error = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, error = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun onQueryChange(q: String) {
        _state.value = _state.value.copy(query = q)
        load(q)
    }

    fun addStaff(fullName: String, role: String, department: String, phone: String, email: String) {
        if (fullName.isBlank() || role.isBlank()) {
            _state.value = _state.value.copy(addError = "Name and role are required.")
            return
        }
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(addError = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(isSaving = true, addError = null, infoMessage = null)
            val req = CreateStaffRequest(
                fullName = fullName.trim(),
                role = role.trim(),
                department = department.trim().ifBlank { null },
                phone = phone.trim().ifBlank { null },
                email = email.trim().ifBlank { null }
            )
            when (val r = repository.createStaff(token, req)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false, infoMessage = "Staff member added")
                    load()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StaffVM", "createStaff error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, addError = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isSaving = false, addError = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun updateStaff(staffId: String, request: UpdateStaffRequest) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(isSaving = true, addError = null, infoMessage = null)
            when (val r = repository.updateStaff(token, staffId, request)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false, infoMessage = "Staff member updated")
                    load()
                }
                is NetworkResult.Error -> {
                    _state.value = _state.value.copy(isSaving = false, addError = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isSaving = false, addError = "Connection error. Check your internet.")
                }
            }
        }
    }

    /** Soft-delete from the staff profile (RA-S17 — never a direct list-row button). */
    fun removeStaff(staffId: String) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(removingIds = _state.value.removingIds + staffId)
            when (val r = repository.deleteStaff(token, staffId)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        removingIds = _state.value.removingIds - staffId,
                        staff = _state.value.staff.filterNot { it.id == staffId },
                        infoMessage = "Staff member removed"
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StaffVM", "deleteStaff error: ${r.message}")
                    _state.value = _state.value.copy(removingIds = _state.value.removingIds - staffId, error = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(removingIds = _state.value.removingIds - staffId, error = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(addError = null, infoMessage = null)
    }
}
