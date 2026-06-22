/*
 * File: AcademicYearViewModel.kt
 * Module: feature.admin.presentation
 *
 * Powers the Academic Year Management screen (replaces the Settings
 * "Academic Year (Coming Soon)" stub). Create / Activate / Archive / view
 * historical years. Exactly one year is active at a time (the server enforces
 * this; the VM just reflects it).
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicYearDto
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicYearStatusC
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateAcademicYearRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateAcademicYearRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.AcademicYearRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AcademicYearState(
    val years: List<AcademicYearDto> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val activeYear: AcademicYearDto? get() = years.firstOrNull { it.isActive }
    val historicalYears: List<AcademicYearDto>
        get() = years.filter { !it.isActive }
    val isEmpty: Boolean get() = years.isEmpty() && !isLoading
}

class AcademicYearViewModel(
    private val repository: AcademicYearRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AcademicYearState())
    val state: StateFlow<AcademicYearState> = _state.asStateFlow()

    private val tag = "AcademicYearVM"

    init { load() }

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = !refresh, isRefreshing = refresh, errorMessage = null)
            val token = token()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
                return@launch
            }
            when (val r = repository.getYears(token)) {
                is NetworkResult.Success ->
                    _state.value = _state.value.copy(
                        years = r.data.data?.years.orEmpty(),
                        isLoading = false,
                        isRefreshing = false
                    )
                is NetworkResult.Error -> {
                    AppLogger.e(tag, "getYears error: ${r.message}")
                    _state.value = _state.value.copy(isLoading = false, isRefreshing = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isLoading = false, isRefreshing = false, errorMessage = "Connection error.")
            }
        }
    }

    fun refresh() = load(refresh = true)

    fun createYear(
        name: String,
        startDate: String,
        endDate: String,
        academicDays: Int? = null,
        holidayDays: Int? = null,
        activate: Boolean = false,
        onCreated: (() -> Unit)? = null
    ) {
        if (name.isBlank() || startDate.isBlank() || endDate.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "Name, start and end dates are required.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            val token = token() ?: run {
                _state.value = _state.value.copy(isMutating = false, errorMessage = "Not signed in"); return@launch
            }
            val req = CreateAcademicYearRequest(name.trim(), startDate, endDate, academicDays, holidayDays, activate)
            when (val r = repository.createYear(token, req)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isMutating = false, infoMessage = "Academic year created")
                    onCreated?.invoke()
                    load()
                }
                is NetworkResult.Error ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error.")
            }
        }
    }

    fun activate(yearId: String) = updateStatus(yearId, AcademicYearStatusC.ACTIVE, "Academic year activated")
    fun archive(yearId: String) = updateStatus(yearId, AcademicYearStatusC.ARCHIVED, "Academic year archived")

    private fun updateStatus(yearId: String, status: String, successMsg: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            val token = token() ?: return@launch
            when (val r = repository.updateYear(token, yearId, UpdateAcademicYearRequest(status = status))) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isMutating = false, infoMessage = successMsg)
                    load()
                }
                is NetworkResult.Error ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error.")
            }
        }
    }

    fun clearMessages() { _state.value = _state.value.copy(errorMessage = null, infoMessage = null) }
}
