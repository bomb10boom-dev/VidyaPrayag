package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.TeacherProfileData
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeacherProfile(
    val id: String,
    val name: String,
    val username: String,
    val schoolName: String,
    val subjects: List<String>,
    val classes: List<String>,
    val photoUrl: String?,
    val email: String,
    val phone: String,
)

data class TeacherProfileState(
    val profile: TeacherProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class TeacherProfileViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherProfileState())
    val state: StateFlow<TeacherProfileState> = _state.asStateFlow()

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
            when (val result = repository.getProfile(token)) {
                is NetworkResult.Success -> {
                    val profile = result.data.data.toUi()
                    _state.update { it.copy(isLoading = false, profile = profile) }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }
}

private fun TeacherProfileData.toUi() = TeacherProfile(
    id = id,
    name = name,
    username = username,
    schoolName = schoolName,
    subjects = subjects,
    classes = classes,
    photoUrl = photoUrl,
    email = email,
    phone = phone,
)
