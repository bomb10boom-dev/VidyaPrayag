package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherLeaveDecisionRequest
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherLeaveDto
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RA-44: backs the teacher leave-requests screen. Lists leave requests routed to
 * the teacher's classes and approves/rejects them (the parent is notified
 * server-side). Three states surfaced per LAW: loading / error / empty.
 */
data class TeacherLeaveState(
    val requests: List<TeacherLeaveDto> = emptyList(),
    val pendingCount: Int = 0,
    val loading: Boolean = false,
    val error: String? = null,
    /** Id currently being decided, so the row can show a spinner. */
    val decidingId: String? = null,
    val decisionError: String? = null,
) {
    val isEmpty: Boolean get() = !loading && error == null && requests.isEmpty()
}

class TeacherLeaveViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherLeaveState())
    val state: StateFlow<TeacherLeaveState> = _state.asStateFlow()

    init {
        load()
    }

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    fun load(status: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val token = token() ?: run {
                _state.update { it.copy(loading = false, error = "Not authenticated") }
                return@launch
            }
            when (val r = repository.getLeaveRequests(token, status)) {
                is NetworkResult.Success -> _state.update {
                    it.copy(loading = false, requests = r.data.data.requests, pendingCount = r.data.data.pendingCount)
                }
                is NetworkResult.Error -> _state.update { it.copy(loading = false, error = r.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(loading = false, error = "Connection error") }
            }
        }
    }

    fun approve(id: String) = decide(id, "Approved")
    fun reject(id: String) = decide(id, "Rejected")

    private fun decide(id: String, status: String) {
        viewModelScope.launch {
            _state.update { it.copy(decidingId = id, decisionError = null) }
            val token = token() ?: run {
                _state.update { it.copy(decidingId = null, decisionError = "Not authenticated") }
                return@launch
            }
            when (val r = repository.decideLeaveRequest(token, id, TeacherLeaveDecisionRequest(status))) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(decidingId = null) }
                    load() // refresh so the decided request reflects its new status
                }
                is NetworkResult.Error -> _state.update { it.copy(decidingId = null, decisionError = r.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(decidingId = null, decisionError = "Connection error") }
            }
        }
    }
}
