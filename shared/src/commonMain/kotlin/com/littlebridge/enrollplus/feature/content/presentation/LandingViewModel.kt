package com.littlebridge.enrollplus.feature.content.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.domain.util.UiState
import com.littlebridge.enrollplus.feature.content.domain.model.LandingData
import com.littlebridge.enrollplus.feature.content.domain.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LandingViewModel(
    private val repository: ContentRepository
) : ViewModel() {

    private val _landingState = MutableStateFlow<UiState<LandingData>>(UiState.Loading)
    val landingState: StateFlow<UiState<LandingData>> = _landingState.asStateFlow()

    init {
        fetchLandingContent()
    }

    fun fetchLandingContent() {
        viewModelScope.launch {
            _landingState.value = UiState.Loading
            when (val result = repository.getLandingContent()) {
                is NetworkResult.Success -> {
                    _landingState.value = UiState.Success(result.data)
                }
                is NetworkResult.Error -> {
                    _landingState.value = UiState.Error(result.message)
                }
                is NetworkResult.ConnectionError -> {
                    _landingState.value = UiState.Error("Please check your internet connection")
                }
            }
        }
    }
}
