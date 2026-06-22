package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.CalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.CalendarRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class SyllabusTarget(
    val id: String,
    val subject: String,
    val chapter: String,
    val deadline: String,
    val progress: Float,
    val status: String // "TARGET SET", "UPCOMING", "PENDING"
)

data class AcademicCalendarState(
    val currentMonth: String = "",
    /** Anchor date for the visible month, ISO format YYYY-MM-DD (first of month). */
    val currentDate: String = "",
    val workingDays: Int = 0,
    val holidays: Int = 0,
    val conflicts: Int = 0,
    val calendarEvents: List<CalendarEventDto> = emptyList(),
    val syllabusTargets: List<SyllabusTarget> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class AcademicCalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AcademicCalendarState())
    val state: StateFlow<AcademicCalendarState> = _state.asStateFlow()

    init {
        // Default anchor: load the calendar around today; server fills date if blank.
        loadCalendar()
    }

    /**
     * Loads the calendar for [date] (YYYY-MM-DD). When null the server defaults
     * to today. The resulting date drives the month label + navigation chevrons.
     */
    fun loadCalendar(date: String? = null, viewType: String = "month") {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("AcademicCalendarVM", "No auth token; skipping load")
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }

            when (val result = calendarRepository.getCalendar(token, date, viewType)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    val summary = data?.summary
                    val anchor = date ?: data?.calendarEvents?.firstOrNull()?.date
                    val resolvedAnchor = anchor ?: _state.value.currentDate
                    _state.value = _state.value.copy(
                        calendarEvents = data?.calendarEvents ?: emptyList(),
                        workingDays = summary?.effectiveWorkingDays ?: 0,
                        holidays = (summary?.publicHolidays ?: 0) + (summary?.schoolHolidays ?: 0),
                        conflicts = 0,   // server doesn't return conflicts count yet
                        currentDate = resolvedAnchor,
                        currentMonth = formatMonthLabel(resolvedAnchor),
                        isLoading = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("AcademicCalendarVM", "getCalendar error: ${result.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = result.message
                    )
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("AcademicCalendarVM", "getCalendar connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    /** Steps the anchor date one month back and reloads. */
    fun goToPreviousMonth() {
        val newAnchor = shiftMonth(_state.value.currentDate, -1)
        loadCalendar(date = newAnchor)
    }

    /** Steps the anchor date one month forward and reloads. */
    fun goToNextMonth() {
        val newAnchor = shiftMonth(_state.value.currentDate, +1)
        loadCalendar(date = newAnchor)
    }

    /**
     * Re-pulls the calendar payload. Used as a "Sync Syllabus" affordance until
     * a dedicated bulk-sync endpoint ships.
     */
    fun syncSyllabus() {
        _state.value = _state.value.copy(infoMessage = "Syncing latest syllabus targets…")
        loadCalendar(date = _state.value.currentDate.ifBlank { null })
    }

    /**
     * Filters the in-memory event list to those that look like holidays
     * (title contains "holiday" or "break"). Surfaces a banner if none found.
     * A first-class /holidays endpoint exists server-side and can be wired in a
     * follow-up if a dedicated screen is added.
     */
    fun showHolidaysOnly() {
        val all = _state.value.calendarEvents
        val holidayEvents = all.filter {
            val t = it.eventTitle.lowercase()
            "holiday" in t || "break" in t || "vacation" in t
        }
        _state.value = _state.value.copy(
            calendarEvents = if (holidayEvents.isEmpty()) all else holidayEvents,
            infoMessage = if (holidayEvents.isEmpty()) {
                "No holidays detected in the current month."
            } else {
                "Showing ${holidayEvents.size} holiday entr${if (holidayEvents.size == 1) "y" else "ies"}."
            }
        )
    }

    fun clearMessages() {
        _state.value = _state.value.copy(errorMessage = null, infoMessage = null)
    }

    // ---------------------------------------------------------------------
    // Date helpers (manual to avoid pulling in kotlinx-datetime just for this)
    // ---------------------------------------------------------------------

    /** Parses a "YYYY-MM-DD" string; falls back to a sensible default on failure. */
    private fun parseDate(iso: String): Triple<Int, Int, Int>? {
        val parts = iso.split("-")
        if (parts.size < 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].take(2).toIntOrNull() ?: return null
        if (m !in 1..12) return null
        return Triple(y, m, d)
    }

    /** Shifts the given ISO date by [delta] months. Returns null-safe ISO string. */
    private fun shiftMonth(iso: String, delta: Int): String {
        val (y, m, _) = parseDate(iso) ?: return iso
        var newMonth = m + delta
        var newYear = y
        while (newMonth < 1) { newMonth += 12; newYear -= 1 }
        while (newMonth > 12) { newMonth -= 12; newYear += 1 }
        val mm = newMonth.toString().padStart(2, '0')
        return "$newYear-$mm-01"
    }

    /** "2024-05-12" -> "May 2024". Returns empty string for invalid input. */
    private fun formatMonthLabel(iso: String): String {
        val parsed = parseDate(iso) ?: return ""
        val (y, m, _) = parsed
        val name = MONTH_NAMES.getOrNull(m - 1) ?: return ""
        return "$name $y"
    }

    companion object {
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
    }
}
