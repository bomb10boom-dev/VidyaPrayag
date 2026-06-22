package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.LeaveRequestDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.LeaveRequestsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LeaveRequestItem(
    val id: String,
    val requesterName: String,
    val dateRange: String,
    val reason: String,
    val imageUrl: String,
    val status: String = "Pending"
)

data class LeaveRequestsState(
    val requestType: String = "Student", // "Student" or "Teacher"
    val approvalRate: Int = 0,
    val weeklyCount: Int = 0,
    val requests: List<LeaveRequestItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class LeaveRequestsViewModel(
    private val leaveRequestsRepository: LeaveRequestsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LeaveRequestsState())
    val state: StateFlow<LeaveRequestsState> = _state.asStateFlow()

    init {
        loadRequests()
    }

    fun setRequestType(type: String) {
        _state.value = _state.value.copy(requestType = type)
        loadRequests(type)
    }

    fun loadRequests(type: String = _state.value.requestType) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("LeaveRequestsVM", "No auth token; skipping load")
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            val serverType = if (type.lowercase() == "teacher") "teacher" else "student"
            when (val result = leaveRequestsRepository.getLeaveRequests(token, serverType)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.value = _state.value.copy(
                        requests = data?.requests?.map { it.toUiModel() } ?: emptyList(),
                        approvalRate = data?.approvalRate ?: 0,
                        weeklyCount = data?.weeklyCount ?: 0,
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("LeaveRequestsVM", "getLeaveRequests error: ${result.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("LeaveRequestsVM", "getLeaveRequests connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    fun approveRequest(id: String) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            when (leaveRequestsRepository.updateLeaveStatus(token, id, "Approved")) {
                is NetworkResult.Success -> loadRequests()
                else -> AppLogger.e("LeaveRequestsVM", "Failed to approve request $id")
            }
        }
    }

    fun rejectRequest(id: String) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            when (leaveRequestsRepository.updateLeaveStatus(token, id, "Rejected")) {
                is NetworkResult.Success -> loadRequests()
                else -> AppLogger.e("LeaveRequestsVM", "Failed to reject request $id")
            }
        }
    }

    private fun LeaveRequestDto.toUiModel() = LeaveRequestItem(
        id = id,
        requesterName = requesterName,
        dateRange = dateRange,
        reason = reason,
        imageUrl = imageUrl ?: "",
        status = status
    )
}
