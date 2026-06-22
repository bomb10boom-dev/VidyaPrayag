package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ParentAnnouncement(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val category: String, // "Holidays", "PTM", "Events", "Reminder"
    val isFeatured: Boolean = false,
    val imageUrl: String? = null
)

data class ParentAnnouncementState(
    val announcements: List<ParentAnnouncement> = emptyList(),
    val isWhatsAppSyncEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ParentAnnouncementViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ParentAnnouncementState())
    val state: StateFlow<ParentAnnouncementState> = _state.asStateFlow()

    init {
        loadAnnouncements()
    }

    private fun loadAnnouncements() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            preferenceRepository.getUserToken().collect { token ->
                if (token != null) {
                    when (val result = repository.getAnnouncements(token)) {
                        is NetworkResult.Success -> {
                            val data = result.data.data
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    announcements = data.announcements.map { a ->
                                        ParentAnnouncement(a.id, a.title, a.description, a.date, a.category, a.isFeatured, a.imageUrl)
                                    },
                                    isWhatsAppSyncEnabled = data.isWhatsAppSyncEnabled
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
    }

    fun toggleWhatsAppSync(enabled: Boolean) {
        _state.value = _state.value.copy(isWhatsAppSyncEnabled = enabled)
    }
}
