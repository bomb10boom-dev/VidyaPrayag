package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.ResultStudentDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.ResultsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class StudentResult(
    val id: String,
    val name: String,
    val imageUrl: String,
    val attendance: String,
    val score: String,
    val status: String,
    val trend: String
)

data class ResultsState(
    val selectedTest: String = "",
    val selectedClass: String = "",
    val selectedSubject: String = "",
    val availableTests: List<String> = emptyList(),
    val availableClasses: List<String> = emptyList(),
    val availableSubjects: List<String> = emptyList(),
    val classAverage: String = "0",
    val averageTrend: String = "0%",
    val exceedingCount: Int = 0,
    val meetingCount: Int = 0,
    val belowCount: Int = 0,
    val students: List<StudentResult> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ResultsViewModel(
    private val resultsRepository: ResultsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ResultsState())
    val state: StateFlow<ResultsState> = _state.asStateFlow()

    init { loadResults() }

    fun loadResults(test: String? = null, className: String? = null, subject: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false); return@launch
            }
            when (val result = resultsRepository.getResults(token, test, className, subject)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    val filters = data?.filters
                    val summary = data?.summary
                    _state.value = _state.value.copy(
                        selectedTest = filters?.selectedTest ?: "",
                        selectedClass = filters?.selectedClass ?: "",
                        selectedSubject = filters?.selectedSubject ?: "",
                        availableTests = filters?.availableTests ?: emptyList(),
                        availableClasses = filters?.availableClasses ?: emptyList(),
                        availableSubjects = filters?.availableSubjects ?: emptyList(),
                        classAverage = summary?.classAverage ?: "0",
                        averageTrend = summary?.averageTrend ?: "0%",
                        exceedingCount = summary?.exceedingCount ?: 0,
                        meetingCount = summary?.meetingCount ?: 0,
                        belowCount = summary?.belowCount ?: 0,
                        students = data?.students?.map { it.toUiModel() } ?: emptyList(),
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("ResultsVM", "getResults error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("ResultsVM", "getResults connection error")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun selectTest(test: String) { _state.value = _state.value.copy(selectedTest = test); loadResults(test, _state.value.selectedClass, _state.value.selectedSubject) }
    fun selectClass(className: String) { _state.value = _state.value.copy(selectedClass = className); loadResults(_state.value.selectedTest, className, _state.value.selectedSubject) }
    fun selectSubject(subject: String) { _state.value = _state.value.copy(selectedSubject = subject); loadResults(_state.value.selectedTest, _state.value.selectedClass, subject) }

    private fun ResultStudentDto.toUiModel() = StudentResult(id, name, imageUrl ?: "", attendance, score, status, trend)
}
