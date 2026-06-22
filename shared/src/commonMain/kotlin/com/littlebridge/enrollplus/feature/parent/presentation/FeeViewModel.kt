package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.state.SelectedChildHolder
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeeAnnouncement(
    val id: String,
    val title: String,
    val time: String,
    val description: String,
    val openRate: String,
    val engagement: String,
    val type: String // "Campaign", "Emergency", "Payment"
)

data class FeeState(
    val totalCollected: String = "$0",
    val collectionProgress: Float = 0f,
    val outstandingFees: String = "$0",
    val overdueCount: Int = 0,
    val announcements: List<FeeAnnouncement> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FeeViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository,
    // RA-S05: shared selected-child source of truth across all parent tabs.
    private val selectedChildHolder: SelectedChildHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(FeeState())
    val state: StateFlow<FeeState> = _state.asStateFlow()

    init {
        // RA-S05: re-fetch the fee stats whenever the shared selected child
        // changes (including the initial null → first-child seed from another
        // tab), so the Fees tab always shows the same child as Home/Academics.
        viewModelScope.launch {
            selectedChildHolder.selectedChildId.collect { childId ->
                loadFees(childId)
            }
        }
    }

    /** RA-S05: load fees scoped to [childId] (null = all of the parent's records). */
    private fun loadFees(childId: String?) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            when (val result = repository.getFees(token, childId)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            totalCollected = data.totalCollected,
                            collectionProgress = data.collectionProgress,
                            outstandingFees = data.outstandingFees,
                            overdueCount = data.overdueCount,
                            announcements = data.announcements.map { a ->
                                FeeAnnouncement(a.id, a.title, a.time, a.description, a.openRate, a.engagement, a.type)
                            }
                        )
                    }
                }
                is NetworkResult.Error -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
                }
                is NetworkResult.ConnectionError -> {
                    _state.update { it.copy(isLoading = false, error = "Connection error") }
                }
            }
        }
    }
}
