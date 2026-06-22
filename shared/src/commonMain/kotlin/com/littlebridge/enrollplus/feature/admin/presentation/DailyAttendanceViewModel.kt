package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AttendanceEntryDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.AttendanceRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AttendanceStatus {
    PRESENT, ABSENT, LATE
}

data class Attendee(
    val id: String,
    val name: String,
    val initials: String,
    val status: AttendanceStatus,
    val imageUrl: String? = null
)

data class DailyAttendanceState(
    val attendanceType: String = "Students", // "Faculty" or "Students"
    val selectedClass: String = "Grade 10-A",
    val availableClasses: List<String> = listOf("Grade 10-A", "Grade 10-B", "Grade 11-A", "Grade 12-C"),
    val attendees: List<Attendee> = emptyList(),
    val totalCount: Int = 0,
    val presentCount: Int = 0,
    val attendancePercentage: String = "0%",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DailyAttendanceViewModel(
    private val attendanceRepository: AttendanceRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DailyAttendanceState())
    val state: StateFlow<DailyAttendanceState> = _state.asStateFlow()

    init {
        loadAttendance()
    }

    fun setAttendanceType(type: String) {
        val serverType = if (type.lowercase().contains("faculty")) "faculty" else "student"
        _state.value = _state.value.copy(attendanceType = type)
        loadAttendance(
            type = serverType,
            grade = if (serverType == "student") _state.value.selectedClass else null
        )
    }

    fun selectClass(className: String) {
        _state.value = _state.value.copy(selectedClass = className)
        loadAttendance(type = "student", grade = className)
    }

    private fun loadAttendance(
        type: String = "student",
        grade: String? = _state.value.selectedClass
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("DailyAttendanceVM", "No auth token; skipping load")
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            when (val result = attendanceRepository.getDailyAttendance(token, type, grade)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.value = _state.value.copy(
                        attendees = data?.attendanceList?.map { it.toUiModel() } ?: emptyList(),
                        totalCount = data?.totalCount ?: 0,
                        presentCount = data?.presentCount ?: 0,
                        attendancePercentage = data?.attendancePercentage ?: "0%",
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("DailyAttendanceVM", "getDailyAttendance error: ${result.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("DailyAttendanceVM", "getDailyAttendance connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun updateStatus(attendeeId: String, newStatus: AttendanceStatus) {
        // Local optimistic update; real API write can be added later
        val updated = _state.value.attendees.map {
            if (it.id == attendeeId) it.copy(status = newStatus) else it
        }
        _state.value = _state.value.copy(attendees = updated)
    }

    private fun AttendanceEntryDto.toUiModel(): Attendee {
        val uiStatus = when (status.lowercase()) {
            "present", "half_day" -> AttendanceStatus.PRESENT
            "late" -> AttendanceStatus.LATE
            else -> AttendanceStatus.ABSENT
        }
        val initials = name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
        return Attendee(
            id = id,
            name = name,
            initials = initials,
            status = uiStatus,
            imageUrl = profilePic
        )
    }
}
