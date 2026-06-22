package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherClassBroadcastRequest
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherClassDto
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherClass(
    val id: String,
    val className: String,
    val subject: String,
    val studentCount: Int,
    val isClassTeacher: Boolean,
    val syllabusProgress: Float,
    val avgAttendance: Float,
)

data class TeacherClassesState(
    val classes: List<TeacherClass> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // RA-51: "message class parents" broadcast composer.
    val broadcastClassName: String? = null,   // non-null = composer open for this class
    val broadcasting: Boolean = false,
    val broadcastError: String? = null,
    val broadcastResultCount: Int? = null,     // non-null = success, N parents reached
)

class TeacherClassesViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherClassesState())
    val state: StateFlow<TeacherClassesState> = _state.asStateFlow()

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
            when (val result = repository.getClasses(token)) {
                is NetworkResult.Success -> {
                    val classes = result.data.data.classes.map { it.toUi() }
                    _state.update { it.copy(isLoading = false, classes = classes) }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    // ---------------- RA-51: message class parents ----------------

    fun openBroadcast(className: String) {
        _state.update {
            it.copy(broadcastClassName = className, broadcastError = null, broadcastResultCount = null)
        }
    }

    fun closeBroadcast() {
        _state.update {
            it.copy(broadcastClassName = null, broadcasting = false, broadcastError = null, broadcastResultCount = null)
        }
    }

    /** Send [body] to every parent of the currently-composing class. */
    fun sendBroadcast(body: String) {
        val className = _state.value.broadcastClassName ?: return
        if (body.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(broadcasting = true, broadcastError = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(broadcasting = false, broadcastError = "Not authenticated") }
                return@launch
            }
            val req = TeacherClassBroadcastRequest(className = className, body = body.trim())
            when (val r = repository.broadcastToClass(token, req)) {
                is NetworkResult.Success ->
                    _state.update {
                        it.copy(broadcasting = false, broadcastResultCount = r.data.data?.recipients ?: 0)
                    }
                is NetworkResult.Error -> _state.update { it.copy(broadcasting = false, broadcastError = r.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(broadcasting = false, broadcastError = "Connection error") }
            }
        }
    }
}

private fun TeacherClassDto.toUi() = TeacherClass(
    id = id,
    className = className,
    subject = subject,
    studentCount = studentCount,
    isClassTeacher = isClassTeacher,
    syllabusProgress = syllabusProgress,
    avgAttendance = avgAttendance,
)
