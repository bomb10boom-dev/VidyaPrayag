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

data class Scholarship(
    val id: String,
    val title: String,
    val description: String,
    val amount: String,
    val timeLeft: String,
    val category: String, // "Full Funding", "Merit Based", "International"
    val isCritical: Boolean = false
)

data class ScholarshipApplication(
    val id: String,
    val institution: String,
    val program: String,
    val status: String, // "Shortlisted", "Under Review", "Received"
    val iconName: String
)

data class ScholarshipsState(
    val scholarships: List<Scholarship> = emptyList(),
    val applications: List<ScholarshipApplication> = emptyList(),
    val profileStrength: Int = 0,
    val streakDays: Int = 0,
    val currentLevel: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class ScholarshipsViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ScholarshipsState())
    val state: StateFlow<ScholarshipsState> = _state.asStateFlow()

    init {
        loadScholarships()
    }

    private fun loadScholarships() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            preferenceRepository.getUserToken().collect { token ->
                if (token != null) {
                    when (val result = repository.getScholarships(token)) {
                        is NetworkResult.Success -> {
                            val data = result.data.data
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    scholarships = data.scholarships.map { s ->
                                        Scholarship(s.id, s.title, s.description, s.amount, s.timeLeft, s.category, s.isCritical)
                                    },
                                    applications = data.applications.map { a ->
                                        ScholarshipApplication(a.id, a.institution, a.program, a.status, a.iconName)
                                    },
                                    profileStrength = data.profileStrength,
                                    streakDays = data.streakDays,
                                    currentLevel = data.currentLevel
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
}
