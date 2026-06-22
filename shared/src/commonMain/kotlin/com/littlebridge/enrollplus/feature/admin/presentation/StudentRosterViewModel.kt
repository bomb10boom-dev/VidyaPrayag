/*
 * File: StudentRosterViewModel.kt
 * Module: feature.admin.presentation
 *
 * RA-45: drives the admin student roster (People tab). Loads the live
 * `students` rows via GET /api/v1/school/students, supports adding a student
 * (POST) and removing one (DELETE). Three states (loading / error+retry /
 * loaded, with an explicit empty) so the roster is never a bare list.
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.BulkImportStudentsRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateStudentRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.StudentDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.StudentsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StudentRosterState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val students: List<StudentDto> = emptyList(),
    // add-student dialog
    val isSaving: Boolean = false,
    val addError: String? = null,
    val infoMessage: String? = null,
    val removingIds: Set<String> = emptySet(),
    // bulk import dialog (manual multi-add + CSV)
    val isImporting: Boolean = false,
    val importError: String? = null
)

class StudentRosterViewModel(
    private val repository: StudentsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StudentRosterState())
    val state: StateFlow<StudentRosterState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, error = "You are not signed in. Please log in again.")
                return@launch
            }
            when (val r = repository.getStudents(token)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isLoading = false, error = null, students = r.data.data?.students.orEmpty())
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StudentRosterVM", "getStudents error: ${r.message}")
                    _state.value = _state.value.copy(isLoading = false, error = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, error = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun addStudent(fullName: String, className: String, section: String, rollNumber: String) {
        if (fullName.isBlank() || className.isBlank() || rollNumber.isBlank()) {
            _state.value = _state.value.copy(addError = "Name, class and roll number are required.")
            return
        }
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(addError = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(isSaving = true, addError = null, infoMessage = null)
            val req = CreateStudentRequest(
                fullName = fullName.trim(),
                className = className.trim(),
                section = section.trim().ifBlank { null },
                rollNumber = rollNumber.trim()
            )
            when (val r = repository.createStudent(token, req)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isSaving = false, infoMessage = "Student added")
                    load()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StudentRosterVM", "createStudent error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, addError = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isSaving = false, addError = "Connection error. Check your internet.")
                }
            }
        }
    }

    /**
     * Bulk import via CSV text. The server parses the header and reports a
     * per-row result; we surface a concise summary in [StudentRosterState.infoMessage]
     * and reload the roster so imported students appear immediately.
     */
    fun importStudentsCsv(csv: String) {
        if (csv.isBlank()) {
            _state.value = _state.value.copy(importError = "Paste or upload CSV content first.")
            return
        }
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(importError = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(isImporting = true, importError = null, infoMessage = null)
            when (val r = repository.importStudents(token, BulkImportStudentsRequest(csv = csv))) {
                is NetworkResult.Success -> {
                    val res = r.data.data
                    val summary = if (res != null) {
                        if (res.failed == 0) "Imported ${res.inserted} students"
                        else "Imported ${res.inserted} of ${res.total} (${res.failed} skipped)"
                    } else "Students imported"
                    _state.value = _state.value.copy(isImporting = false, infoMessage = summary)
                    load()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StudentRosterVM", "importStudents error: ${r.message}")
                    _state.value = _state.value.copy(isImporting = false, importError = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isImporting = false, importError = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun removeStudent(studentId: String) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "You are not signed in. Please log in again.")
                return@launch
            }
            _state.value = _state.value.copy(removingIds = _state.value.removingIds + studentId)
            when (val r = repository.deleteStudent(token, studentId)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        removingIds = _state.value.removingIds - studentId,
                        students = _state.value.students.filterNot { it.id == studentId },
                        infoMessage = "Student removed"
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StudentRosterVM", "deleteStudent error: ${r.message}")
                    _state.value = _state.value.copy(removingIds = _state.value.removingIds - studentId, error = r.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(removingIds = _state.value.removingIds - studentId, error = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(addError = null, infoMessage = null, importError = null)
    }
}
