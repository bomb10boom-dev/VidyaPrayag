/*
 * File: AcademicCalendarPlatformViewModel.kt
 * Module: feature.admin.presentation
 *
 * The brain behind the premium Academic Calendar platform screen (VP-CAL). It
 * owns:
 *  - the dashboard payload (hero / highlights / timeline / drafts / published /
 *    milestones / analytics)
 *  - the filtered events list (month / status / type) that powers the
 *    interactive calendar + agenda + timeline views
 *  - event lifecycle actions (publish / cancel / delete / duplicate)
 *
 * Mirrors the existing admin ViewModel conventions: a single immutable State
 * exposed as a StateFlow, token fetched from PreferenceRepository, exhaustive
 * NetworkResult handling, and AppLogger for diagnostics.
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicCalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarDashboardDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventStatus
import com.littlebridge.enrollplus.feature.admin.domain.model.DuplicateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.UpdateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.AcademicCalendarPlatformRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The three calendar presentation modes the View Switcher toggles between. */
enum class CalendarViewMode { MONTH, AGENDA, TIMELINE }

data class AcademicCalendarPlatformState(
    val dashboard: CalendarDashboardDto? = null,
    /** All events for the active month/filter (drives calendar + agenda). */
    val events: List<AcademicCalendarEventDto> = emptyList(),
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    /** Active month filter, "YYYY-MM"; null = all. */
    val monthFilter: String? = null,
    val statusFilter: String? = null,
    val typeFilter: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isMutating: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
) {
    val isEmpty: Boolean
        get() = dashboard == null && events.isEmpty() && !isLoading
}

class AcademicCalendarPlatformViewModel(
    private val repository: AcademicCalendarPlatformRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AcademicCalendarPlatformState())
    val state: StateFlow<AcademicCalendarPlatformState> = _state.asStateFlow()

    private val tag = "AcademicCalendarPlatformVM"

    init {
        load()
    }

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = !refresh,
                isRefreshing = refresh,
                errorMessage = null
            )
            val token = token()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
                return@launch
            }
            // Dashboard + events in sequence (cheap; keeps error handling simple).
            when (val dash = repository.getDashboard(token)) {
                is NetworkResult.Success ->
                    _state.value = _state.value.copy(dashboard = dash.data.data)
                is NetworkResult.Error -> {
                    AppLogger.e(tag, "dashboard error: ${dash.message}")
                    _state.value = _state.value.copy(errorMessage = dash.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(errorMessage = "Connection error. Check your internet.")
            }
            loadEvents(token)
            _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
        }
    }

    fun refresh() = load(refresh = true)

    private suspend fun loadEvents(token: String) {
        val s = _state.value
        when (val r = repository.getEvents(token, s.monthFilter, s.statusFilter, s.typeFilter)) {
            is NetworkResult.Success ->
                _state.value = _state.value.copy(events = r.data.data?.events.orEmpty())
            is NetworkResult.Error -> {
                AppLogger.e(tag, "events error: ${r.message}")
                _state.value = _state.value.copy(errorMessage = r.message)
            }
            is NetworkResult.ConnectionError ->
                _state.value = _state.value.copy(errorMessage = "Connection error. Check your internet.")
        }
    }

    fun setViewMode(mode: CalendarViewMode) {
        _state.value = _state.value.copy(viewMode = mode)
    }

    fun setMonthFilter(month: String?) {
        _state.value = _state.value.copy(monthFilter = month)
        reloadEvents()
    }

    fun setStatusFilter(status: String?) {
        _state.value = _state.value.copy(statusFilter = status)
        reloadEvents()
    }

    fun setTypeFilter(type: String?) {
        _state.value = _state.value.copy(typeFilter = type)
        reloadEvents()
    }

    private fun reloadEvents() {
        viewModelScope.launch {
            val token = token() ?: return@launch
            _state.value = _state.value.copy(isLoading = true)
            loadEvents(token)
            _state.value = _state.value.copy(isLoading = false)
        }
    }

    // ── lifecycle actions (overflow menu) ──────────────────────────────────

    fun publish(eventId: String) =
        mutate(eventId, UpdateCalendarEventRequest(status = CalEventStatus.PUBLISHED), "Event published")

    fun cancel(eventId: String) =
        mutate(eventId, UpdateCalendarEventRequest(status = CalEventStatus.CANCELLED), "Event cancelled")

    fun reschedule(eventId: String, startDate: String, endDate: String?) =
        mutate(
            eventId,
            UpdateCalendarEventRequest(startDate = startDate, endDate = endDate ?: startDate),
            "Event rescheduled"
        )

    private fun mutate(eventId: String, request: UpdateCalendarEventRequest, successMsg: String) {
        viewModelScope.launch {
            val token = token() ?: return@launch
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            when (val r = repository.updateEvent(token, eventId, request)) {
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

    fun delete(eventId: String) {
        viewModelScope.launch {
            val token = token() ?: return@launch
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            when (val r = repository.deleteEvent(token, eventId)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isMutating = false, infoMessage = "Event deleted")
                    load()
                }
                is NetworkResult.Error ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error.")
            }
        }
    }

    fun duplicate(
        eventId: String,
        newTitle: String? = null,
        newAudience: String? = null,
        newStartDate: String? = null
    ) {
        viewModelScope.launch {
            val token = token() ?: return@launch
            _state.value = _state.value.copy(isMutating = true, errorMessage = null)
            val req = DuplicateCalendarEventRequest(
                title = newTitle,
                startDate = newStartDate,
                audience = newAudience
            )
            when (val r = repository.duplicateEvent(token, eventId, req)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(isMutating = false, infoMessage = "Event duplicated")
                    load()
                }
                is NetworkResult.Error ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = r.message)
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isMutating = false, errorMessage = "Connection error.")
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }
}
