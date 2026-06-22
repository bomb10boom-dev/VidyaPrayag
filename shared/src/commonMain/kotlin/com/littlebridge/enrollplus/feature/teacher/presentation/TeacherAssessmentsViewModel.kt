package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.CreateAssessmentRequest
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherAssessmentDto
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RA-40: backs the exam selector on the teacher Marks plane. The marks screen
 * requires a valid `exam_id`; this VM lists the exams a teacher can mark for a
 * chosen class and lets them create one inline when none exist yet.
 */
data class TeacherAssessmentsState(
    val classId: String = "",
    val assessments: List<TeacherAssessmentDto> = emptyList(),
    val selectedExamId: String = "",
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
)

class TeacherAssessmentsViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherAssessmentsState())
    val state: StateFlow<TeacherAssessmentsState> = _state.asStateFlow()

    /** Load the exams for [classId]. No-op for a blank class (nothing to scope to). */
    fun load(classId: String) {
        if (classId.isBlank()) {
            _state.update { TeacherAssessmentsState() }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(classId = classId, isLoading = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not authenticated") }
                return@launch
            }
            when (val result = repository.getAssessments(token, classId)) {
                is NetworkResult.Success -> {
                    val items = result.data.data.assessments
                    _state.update {
                        it.copy(
                            isLoading = false,
                            assessments = items,
                            // Keep current selection if still present, else default to first.
                            selectedExamId = items.firstOrNull { a -> a.id == it.selectedExamId }?.id
                                ?: items.firstOrNull()?.id.orEmpty(),
                        )
                    }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    fun selectExam(examId: String) {
        _state.update { it.copy(selectedExamId = examId) }
    }

    /** Create an exam for the current class, then select it. */
    fun createAssessment(classId: String, name: String, maxMarks: Int?, examDate: String?) {
        if (classId.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isCreating = false, error = "Not authenticated") }
                return@launch
            }
            val req = CreateAssessmentRequest(classId = classId, name = name.trim(), maxMarks = maxMarks, examDate = examDate)
            when (val result = repository.createAssessment(token, req)) {
                is NetworkResult.Success -> {
                    val created = result.data.data
                    _state.update {
                        val next = if (created != null) listOf(created) + it.assessments else it.assessments
                        it.copy(
                            isCreating = false,
                            assessments = next,
                            selectedExamId = created?.id ?: it.selectedExamId,
                        )
                    }
                    // Refresh from server so ordering/state stays canonical.
                    load(classId)
                }
                is NetworkResult.Error -> _state.update { it.copy(isCreating = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isCreating = false, error = "Connection error") }
            }
        }
    }
}
