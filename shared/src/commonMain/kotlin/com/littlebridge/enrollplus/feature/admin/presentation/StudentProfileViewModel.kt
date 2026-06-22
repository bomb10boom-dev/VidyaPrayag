/*
 * File: StudentProfileViewModel.kt
 * Module: feature.admin.presentation
 *
 * RA-45: drives the admin student profile screen. The id is supplied by the
 * screen (caller passes it via load(id) from a LaunchedEffect, matching the
 * codebase's "VM with load()" convention — no Koin parametersOf needed).
 * Three states (loading / error+retry / loaded).
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentProfileDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.StudentsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StudentProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val profile: StudentProfileDto? = null,
    // RA-S17: delete-from-profile (replaces the direct roster Remove button)
    val isRemoving: Boolean = false,
    val removed: Boolean = false,
    val removeError: String? = null
)

class StudentProfileViewModel(
    private val repository: StudentsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StudentProfileUiState())
    val state: StateFlow<StudentProfileUiState> = _state.asStateFlow()

    private var lastId: String? = null

    fun load(studentId: String) {
        lastId = studentId
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, error = "You are not signed in. Please log in again.")
                return@launch
            }
            when (val r = repository.getStudentProfile(token, studentId)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false, error = null, profile = r.data.data)
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StudentProfileVM", "getStudentProfile error: ${r.message}")
                    _state.value = _state.value.copy(isLoading = false, error = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, error = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun retry() { lastId?.let { load(it) } }

    /**
     * RA-S17: soft-delete this student from inside the profile, behind a confirm
     * dialog on the screen. On success [removed] flips true so the screen can pop
     * back to the roster and refresh.
     */
    fun remove(studentId: String) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(removeError = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(isRemoving = true, removeError = null)
            when (val r = repository.deleteStudent(token, studentId)) {
                is NetworkResult.Success ->
                    _state.value = _state.value.copy(isRemoving = false, removed = true)
                is NetworkResult.Error -> {
                    AppLogger.e("StudentProfileVM", "deleteStudent error: ${r.message}")
                    _state.value = _state.value.copy(isRemoving = false, removeError = r.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isRemoving = false, removeError = "Connection error. Check your internet.")
            }
        }
    }

    fun clearRemoveError() { _state.value = _state.value.copy(removeError = null) }
}
