package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.AttendanceEntryDto
import com.littlebridge.enrollplus.feature.teacher.domain.model.AttendanceMarkDto
import com.littlebridge.enrollplus.feature.teacher.domain.model.SubmitAttendanceRequest
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Attendance status values mirrored from the server contract. */
object AttendanceStatus {
    const val PRESENT = "present"
    const val ABSENT = "absent"
    const val LATE = "late"
}

data class StudentAttendance(
    val studentId: String,
    val name: String,
    val rollNo: String,
    val status: String,
)

data class TeacherAttendanceState(
    val classId: String = "",
    val className: String = "",
    val date: String = "",
    val students: List<StudentAttendance> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    // RA-S18: load errors drive VStateHost (full-screen retry); submit errors are surfaced inline
    // so a failed save never wipes the marked roster the teacher is mid-way through.
    val error: String? = null,        // load-path error (VStateHost)
    val submitError: String? = null,  // submit-path error (inline)
) {
    val presentCount: Int get() = students.count { it.status == AttendanceStatus.PRESENT }
    val absentCount: Int get() = students.count { it.status == AttendanceStatus.ABSENT }
    val lateCount: Int get() = students.count { it.status == AttendanceStatus.LATE }
}

class TeacherAttendanceViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherAttendanceState())
    val state: StateFlow<TeacherAttendanceState> = _state.asStateFlow()

    /** Load the roster for a given class + date. The screen supplies both. */
    fun load(classId: String, date: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(classId = classId, date = date, isLoading = true, error = null, submitSuccess = false)
            }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not authenticated") }
                return@launch
            }
            when (val result = repository.getAttendance(token, classId, date)) {
                is NetworkResult.Success -> {
                    val d = result.data.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            className = d.className,
                            date = d.date,
                            students = d.students.map { e -> e.toUi() },
                        )
                    }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    /** Local edit — update a single student's status without a network call. */
    fun setStatus(studentId: String, status: String) {
        // RA-S18: editing the roster after a confirmed submit clears the success state so the
        // Submit button re-enables for the new edit (the button's "Submitted" check is result-driven).
        _state.update { s ->
            s.copy(
                students = s.students.map { if (it.studentId == studentId) it.copy(status = status) else it },
                submitSuccess = false,
            )
        }
    }

    /** Bulk helper used by "Mark all present". */
    fun markAll(status: String) {
        _state.update { s -> s.copy(students = s.students.map { it.copy(status = status) }, submitSuccess = false) }
    }

    fun submit() {
        viewModelScope.launch {
            val current = _state.value
            _state.update { it.copy(isSubmitting = true, submitError = null, submitSuccess = false) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isSubmitting = false, submitError = "Not authenticated") }
                return@launch
            }
            val request = SubmitAttendanceRequest(
                classId = current.classId,
                date = current.date,
                entries = current.students.map { AttendanceMarkDto(it.studentId, it.status) },
            )
            when (val result = repository.submitAttendance(token, request)) {
                is NetworkResult.Success -> _state.update { it.copy(isSubmitting = false, submitSuccess = true) }
                is NetworkResult.Error -> _state.update { it.copy(isSubmitting = false, submitError = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isSubmitting = false, submitError = "Connection error") }
            }
        }
    }
}

private fun AttendanceEntryDto.toUi() = StudentAttendance(studentId, name, rollNo, status)
