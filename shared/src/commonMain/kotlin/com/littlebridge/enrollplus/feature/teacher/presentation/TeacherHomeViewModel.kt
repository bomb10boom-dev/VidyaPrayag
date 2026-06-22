package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherPeriodDto
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherTaskDto
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherPeriod(
    val id: String,
    val time: String,
    val className: String,
    val subject: String,
    val room: String,
    val status: String,
)

data class TeacherTask(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val className: String,
    val isDone: Boolean,
)

data class TeacherHomeState(
    val teacherName: String = "",
    val schoolName: String = "",
    val classesToday: Int = 0,
    val pendingAttendance: Int = 0,
    val pendingMarks: Int = 0,
    val homeworkDue: Int = 0,
    val periods: List<TeacherPeriod> = emptyList(),
    val tasks: List<TeacherTask> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class TeacherHomeViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherHomeState())
    val state: StateFlow<TeacherHomeState> = _state.asStateFlow()

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
            when (val result = repository.getHome(token)) {
                is NetworkResult.Success -> {
                    val d = result.data.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            teacherName = d.teacherName,
                            schoolName = d.schoolName,
                            classesToday = d.classesToday,
                            pendingAttendance = d.pendingAttendance,
                            pendingMarks = d.pendingMarks,
                            homeworkDue = d.homeworkDue,
                            periods = d.todayPeriods.map { p -> p.toUi() },
                            tasks = d.tasks.map { t -> t.toUi() },
                        )
                    }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }
}

private fun TeacherPeriodDto.toUi() = TeacherPeriod(id, time, className, subject, room, status)
private fun TeacherTaskDto.toUi() = TeacherTask(id, title, subtitle, type, className, isDone)
