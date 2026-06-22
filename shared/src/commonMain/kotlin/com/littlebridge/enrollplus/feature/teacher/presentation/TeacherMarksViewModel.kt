package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.MarkScoreDto
import com.littlebridge.enrollplus.feature.teacher.domain.model.MarksEntryDto
import com.littlebridge.enrollplus.feature.teacher.domain.model.SubmitMarksRequest
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentMark(
    val studentId: String,
    val name: String,
    val rollNo: String,
    val marks: Float?,
)

data class TeacherMarksState(
    val classId: String = "",
    val examId: String = "",
    val className: String = "",
    val subject: String = "",
    val examName: String = "",
    val maxMarks: Int = 0,
    val students: List<StudentMark> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitSuccess: Boolean = false,
    // RA-S18: load errors drive VStateHost (full-screen retry); submit errors are surfaced inline
    // so a failed save never wipes the marks the teacher is mid-way through entering.
    val error: String? = null,        // load-path error (VStateHost)
    val submitError: String? = null,  // submit-path error (inline)
) {
    val enteredCount: Int get() = students.count { it.marks != null }
    val allEntered: Boolean get() = students.isNotEmpty() && students.all { it.marks != null }
}

class TeacherMarksViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherMarksState())
    val state: StateFlow<TeacherMarksState> = _state.asStateFlow()

    /** Load the student list for a class + exam. The screen supplies both ids. */
    fun load(classId: String, examId: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(classId = classId, examId = examId, isLoading = true, error = null, submitSuccess = false)
            }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not authenticated") }
                return@launch
            }
            when (val result = repository.getMarks(token, classId, examId)) {
                is NetworkResult.Success -> {
                    val d = result.data.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            className = d.className,
                            subject = d.subject,
                            examName = d.examName,
                            maxMarks = d.maxMarks,
                            students = d.students.map { e -> e.toUi() },
                        )
                    }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    /**
     * Local edit — update a single student's mark. Out-of-range values are clamped
     * to [0, maxMarks]; pass `null` to clear the field.
     */
    fun setMark(studentId: String, marks: Float?) {
        // RA-S18: editing a mark after a confirmed save clears the success state so the Save
        // button re-enables for the new edit (the button's "Saved" check is result-driven).
        _state.update { s ->
            val clamped = marks?.coerceIn(0f, s.maxMarks.toFloat())
            s.copy(
                students = s.students.map { if (it.studentId == studentId) it.copy(marks = clamped) else it },
                submitSuccess = false,
            )
        }
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
            val request = SubmitMarksRequest(
                classId = current.classId,
                examId = current.examId,
                entries = current.students.mapNotNull { s ->
                    s.marks?.let { MarkScoreDto(s.studentId, it) }
                },
            )
            when (val result = repository.submitMarks(token, request)) {
                is NetworkResult.Success -> _state.update { it.copy(isSubmitting = false, submitSuccess = true) }
                is NetworkResult.Error -> _state.update { it.copy(isSubmitting = false, submitError = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isSubmitting = false, submitError = "Connection error") }
            }
        }
    }
}

private fun MarksEntryDto.toUi() = StudentMark(studentId, name, rollNo, marks)
