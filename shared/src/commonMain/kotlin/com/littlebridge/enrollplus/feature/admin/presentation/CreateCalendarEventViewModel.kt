/*
 * File: CreateCalendarEventViewModel.kt
 * Module: feature.admin.presentation
 *
 * Drives the dedicated 7-step CreateEventScreen (no dialogs):
 *   1. Event Type     (Exam / Holiday / PTM / School Event / Activity / Administrative)
 *   2. Basic Details  (title, description, banner, icon)
 *   3. Schedule       (start/end, all-day, multi-day)
 *   4. Audience       (multi-select: Entire School / Grades / Classes / Sections /
 *                      Teachers / Parents / Students)
 *   5. Notifications  (students / parents / teachers)
 *   6. Preview        (read-only review + conflict warnings)
 *   7. Save Draft OR Publish
 *
 * The ViewModel keeps the whole draft in one immutable State so the wizard can
 * move freely backward/forward without losing input.
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicCalendarEventDto
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventAudience
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventStatus
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventType
import com.littlebridge.enrollplus.feature.admin.domain.model.CreateCalendarEventRequest
import com.littlebridge.enrollplus.feature.admin.domain.repository.AcademicCalendarPlatformRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CreateEventForm(
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val bannerUrl: String = "",
    val icon: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val allDay: Boolean = true,
    /** Selected audience scopes (multi-select). */
    val audiences: Set<String> = setOf(CalEventAudience.ALL_SCHOOL),
    val classIds: List<String> = emptyList(),
    val sectionIds: List<String> = emptyList(),
    val notifyStudents: Boolean = false,
    val notifyParents: Boolean = false,
    val notifyTeachers: Boolean = false,
    val isMilestone: Boolean = false
)

data class CreateCalendarEventState(
    val step: Int = 1,                       // 1..7
    val form: CreateEventForm = CreateEventForm(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    /** Set when the event was created; the screen pops on observing this. */
    val created: AcademicCalendarEventDto? = null
) {
    val totalSteps: Int get() = 7
    val canGoNext: Boolean
        get() = when (step) {
            1 -> form.type.isNotBlank()
            2 -> form.title.isNotBlank()
            3 -> form.startDate.isNotBlank() &&
                (form.endDate.isBlank() || form.endDate >= form.startDate)
            4 -> form.audiences.isNotEmpty()
            else -> true
        }
}

class CreateCalendarEventViewModel(
    private val repository: AcademicCalendarPlatformRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CreateCalendarEventState())
    val state: StateFlow<CreateCalendarEventState> = _state.asStateFlow()

    private val tag = "CreateCalendarEventVM"

    // ── wizard navigation ──────────────────────────────────────────────────
    fun next() {
        val s = _state.value
        if (s.canGoNext && s.step < s.totalSteps) {
            _state.value = s.copy(step = s.step + 1, errorMessage = null)
        }
    }

    fun back() {
        val s = _state.value
        if (s.step > 1) _state.value = s.copy(step = s.step - 1, errorMessage = null)
    }

    fun goToStep(step: Int) {
        if (step in 1.._state.value.totalSteps) _state.value = _state.value.copy(step = step)
    }

    // ── field updates ───────────────────────────────────────────────────────
    private fun update(block: (CreateEventForm) -> CreateEventForm) {
        _state.value = _state.value.copy(form = block(_state.value.form))
    }

    fun setType(type: String) = update {
        // Picking the Milestone type pre-flags isMilestone for convenience.
        it.copy(type = type, isMilestone = type == CalEventType.MILESTONE || it.isMilestone)
    }
    fun setTitle(v: String) = update { it.copy(title = v) }
    fun setDescription(v: String) = update { it.copy(description = v) }
    fun setBannerUrl(v: String) = update { it.copy(bannerUrl = v) }
    fun setIcon(v: String) = update { it.copy(icon = v) }
    fun setStartDate(v: String) = update {
        // Keep end >= start; default a blank end to the start (single day).
        val end = if (it.endDate.isBlank() || it.endDate < v) v else it.endDate
        it.copy(startDate = v, endDate = end)
    }
    fun setEndDate(v: String) = update { it.copy(endDate = v) }
    fun setAllDay(v: Boolean) = update { it.copy(allDay = v) }
    fun setMilestone(v: Boolean) = update { it.copy(isMilestone = v) }

    fun toggleAudience(scope: String) = update {
        val current = it.audiences.toMutableSet()
        if (scope == CalEventAudience.ALL_SCHOOL) {
            // Entire School is exclusive — selecting it clears the rest.
            it.copy(audiences = setOf(CalEventAudience.ALL_SCHOOL))
        } else {
            current.remove(CalEventAudience.ALL_SCHOOL)
            if (current.contains(scope)) current.remove(scope) else current.add(scope)
            it.copy(audiences = if (current.isEmpty()) setOf(CalEventAudience.ALL_SCHOOL) else current)
        }
    }

    fun setNotify(students: Boolean? = null, parents: Boolean? = null, teachers: Boolean? = null) = update {
        it.copy(
            notifyStudents = students ?: it.notifyStudents,
            notifyParents = parents ?: it.notifyParents,
            notifyTeachers = teachers ?: it.notifyTeachers
        )
    }

    // ── save ────────────────────────────────────────────────────────────────
    fun saveDraft() = save(CalEventStatus.DRAFT)
    fun publish() = save(CalEventStatus.PUBLISHED)

    private fun save(status: String) {
        val form = _state.value.form
        if (form.type.isBlank()) { _state.value = _state.value.copy(errorMessage = "Pick an event type"); return }
        if (form.title.isBlank()) { _state.value = _state.value.copy(errorMessage = "Title is required"); return }
        if (form.startDate.isBlank()) { _state.value = _state.value.copy(errorMessage = "Start date is required"); return }

        // The primary audience scope persisted server-side (the platform stores a
        // single audience column + class/section id lists). We send the most
        // specific selected scope, defaulting to ALL_SCHOOL.
        val primaryAudience = when {
            form.audiences.contains(CalEventAudience.ALL_SCHOOL) -> CalEventAudience.ALL_SCHOOL
            else -> form.audiences.firstOrNull() ?: CalEventAudience.ALL_SCHOOL
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isSaving = false, errorMessage = "Not signed in")
                return@launch
            }
            val request = CreateCalendarEventRequest(
                title = form.title.trim(),
                description = form.description.trim(),
                type = form.type,
                status = status,
                startDate = form.startDate,
                endDate = form.endDate.takeIf { it.isNotBlank() } ?: form.startDate,
                allDay = form.allDay,
                bannerUrl = form.bannerUrl.trim().takeIf { it.isNotBlank() },
                icon = form.icon.trim().takeIf { it.isNotBlank() },
                audience = primaryAudience,
                classIds = form.classIds,
                sectionIds = form.sectionIds,
                notifyStudents = form.notifyStudents,
                notifyParents = form.notifyParents,
                notifyTeachers = form.notifyTeachers,
                isMilestone = form.isMilestone
            )
            when (val r = repository.createEvent(token, request)) {
                is NetworkResult.Success ->
                    _state.value = _state.value.copy(isSaving = false, created = r.data.data)
                is NetworkResult.Error -> {
                    AppLogger.e(tag, "create error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, errorMessage = r.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isSaving = false, errorMessage = "Connection error.")
            }
        }
    }

    fun reset() { _state.value = CreateCalendarEventState() }
    fun clearError() { _state.value = _state.value.copy(errorMessage = null) }
}
