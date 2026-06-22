package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.auth.domain.model.PersonalDetails
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-facing parent profile. Mirrors the real `personal_details` block of `/api/v1/user/details`. */
data class ParentProfile(
    val id: String,
    val name: String,
    val role: String,
    val email: String,
    val phone: String,
    val photoUrl: String?,
)

data class ParentProfileState(
    val profile: ParentProfile? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * ParentProfileViewModel — drives [ParentProfileScreenV2] off the real
 * `GET /api/v1/user/details` endpoint (audit finding **K**, §7).
 *
 * Before this fix the parent portal had NO profile surface at all — tapping the
 * avatar simply logged you out. This VM gives the avatar a real destination by
 * reusing the same authenticated current-user endpoint the school/teacher portals
 * already consume (`AuthRepository.getUserDetails`). Logout now lives on the
 * profile screen, not behind the avatar.
 */
class ParentProfileViewModel(
    private val authRepository: AuthRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentProfileState())
    val state: StateFlow<ParentProfileState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            when (val result = authRepository.getUserDetails(token)) {
                is NetworkResult.Success -> {
                    val profile = result.data.data.personalDetails.toUi()
                    _state.update { it.copy(isLoading = false, profile = profile) }
                }
                is NetworkResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }
}

private fun PersonalDetails.toUi() = ParentProfile(
    id = id,
    name = name,
    role = role,
    email = email.orEmpty(),
    phone = mobile.orEmpty(),
    photoUrl = profilePic,
)
