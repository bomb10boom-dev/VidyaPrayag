package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.CreateHomeworkRequest
import com.littlebridge.enrollplus.feature.teacher.domain.model.HomeworkDto
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Homework(
    val id: String,
    val title: String,
    val description: String,
    val className: String,
    val subject: String,
    val dueDate: String,
    val submittedCount: Int,
    val totalCount: Int,
) {
    val submissionRatio: Float get() = if (totalCount == 0) 0f else submittedCount.toFloat() / totalCount
}

data class TeacherHomeworkState(
    val items: List<Homework> = emptyList(),
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val createSuccess: Boolean = false,
    val error: String? = null,
)

class TeacherHomeworkViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherHomeworkState())
    val state: StateFlow<TeacherHomeworkState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not authenticated") }
                return@launch
            }
            when (val result = repository.getHomework(token)) {
                is NetworkResult.Success -> {
                    val items = result.data.data.items.map { it.toUi() }
                    _state.update { it.copy(isLoading = false, items = items) }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    /** Create a homework assignment, then refresh the list on success. */
    fun create(classId: String, title: String, description: String, dueDate: String) {
        viewModelScope.launch {
            _state.update { it.copy(isCreating = true, error = null, createSuccess = false) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isCreating = false, error = "Not authenticated") }
                return@launch
            }
            val request = CreateHomeworkRequest(
                classId = classId,
                title = title,
                description = description,
                dueDate = dueDate,
            )
            when (val result = repository.createHomework(token, request)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(isCreating = false, createSuccess = true) }
                    load()
                }
                is NetworkResult.Error -> _state.update { it.copy(isCreating = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isCreating = false, error = "Connection error") }
            }
        }
    }

    /** Reset the one-shot success flag after the UI has consumed it. */
    fun consumeCreateSuccess() {
        _state.update { it.copy(createSuccess = false) }
    }
}

private fun HomeworkDto.toUi() = Homework(
    id = id,
    title = title,
    description = description,
    className = className,
    subject = subject,
    dueDate = dueDate,
    submittedCount = submittedCount,
    totalCount = totalCount,
)
