package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.LinkRequestDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.LinkRequestsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * RA-48: drives the school-admin link-request queue
 * ([com.littlebridge.enrollplus.ui.v2.screens.school.LinkRequestsScreenV2]) off
 * the real GET /api/v1/school/link-requests + approve/reject endpoints. Every
 * request shown is scoped server-side to the admin's own school.
 */
data class LinkRequestsState(
    val requests: List<LinkRequestDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    // ids currently being approved/rejected (per-row spinner / disable).
    val actingIds: Set<String> = emptySet(),
)

class LinkRequestsViewModel(
    private val repository: LinkRequestsRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LinkRequestsState())
    val state: StateFlow<LinkRequestsState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, error = "Not signed in")
                return@launch
            }
            when (val result = repository.getLinkRequests(token, "pending")) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        requests = result.data.data?.requests ?: emptyList(),
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("LinkRequestsVM", "getLinkRequests error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, error = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    _state.value = _state.value.copy(isLoading = false, error = "Connection error. Check your internet.")
                }
            }
        }
    }

    fun approve(id: String) = decide(id, approve = true)
    fun reject(id: String) = decide(id, approve = false)

    private fun decide(id: String, approve: Boolean) {
        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first() ?: return@launch
            _state.value = _state.value.copy(actingIds = _state.value.actingIds + id)
            val result = if (approve) repository.approve(token, id) else repository.reject(token, id)
            _state.value = _state.value.copy(actingIds = _state.value.actingIds - id)
            when (result) {
                is NetworkResult.Success -> {
                    // Optimistically drop the decided row; it leaves the pending list.
                    _state.value = _state.value.copy(
                        requests = _state.value.requests.filterNot { it.id == id }
                    )
                }
                is NetworkResult.Error ->
                    _state.value = _state.value.copy(error = result.message)
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(error = "Connection error. Check your internet.")
            }
        }
    }
}
