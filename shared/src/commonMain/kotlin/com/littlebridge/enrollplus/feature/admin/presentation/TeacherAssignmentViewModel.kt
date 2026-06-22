/*
 * File: TeacherAssignmentViewModel.kt
 * Module: feature.admin.presentation
 *
 * RA-TAM: drives the reusable Teacher Assignment Management screen. The teacher
 * id is supplied via load(id). Holds the overview (summary/assignments/insights/
 * distribution), the selector options, and the transient add-assignment builder
 * (subject → classes → sections → preview → save). Three states (LAW 3).
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignTeacherClassesRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignmentOptionsDto
import com.littlebridge.enrollplus.feature.admin.domain.model.AssignmentTarget
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAssignmentOverviewDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.TeacherAssignmentRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Transient builder state for the add-assignment flow. */
data class AddAssignmentDraft(
    val subjectId: String? = null,
    val subjectName: String? = null,
    // class option ids the admin has selected (multi-select)
    val selectedClassIds: Set<String> = emptySet(),
    // sections (e.g. "A","B") selected for the chosen classes
    val selectedSections: Set<String> = emptySet(),
)

data class TeacherAssignmentUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val overview: TeacherAssignmentOverviewDto? = null,
    val options: AssignmentOptionsDto? = null,

    // add-assignment builder
    val draft: AddAssignmentDraft = AddAssignmentDraft(),
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val lastSaveMessage: String? = null,

    // per-card removal
    val removingId: String? = null,
    val removeError: String? = null,
)

class TeacherAssignmentViewModel(
    private val repository: TeacherAssignmentRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TeacherAssignmentUiState())
    val state: StateFlow<TeacherAssignmentUiState> = _state.asStateFlow()

    private var teacherId: String? = null

    fun load(id: String) {
        teacherId = id
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val token = token() ?: run {
                _state.value = _state.value.copy(isLoading = false, error = NOT_SIGNED_IN)
                return@launch
            }
            // Overview is the primary payload; options are best-effort (selector).
            when (val r = repository.getOverview(token, id)) {
                is NetworkResult.Success ->
                    _state.value = _state.value.copy(isLoading = false, error = null, overview = r.data.data)
                is NetworkResult.Error -> {
                    AppLogger.e(TAG, "getOverview error: ${r.message}")
                    _state.value = _state.value.copy(isLoading = false, error = r.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isLoading = false, error = CONNECTION_ERROR)
            }
            loadOptions(token)
        }
    }

    private suspend fun loadOptions(token: String) {
        when (val r = repository.getOptions(token)) {
            is NetworkResult.Success -> _state.value = _state.value.copy(options = r.data.data)
            is NetworkResult.Error -> AppLogger.e(TAG, "getOptions error: ${r.message}")
            is NetworkResult.ConnectionError -> AppLogger.e(TAG, "getOptions connection error")
        }
    }

    fun retry() { teacherId?.let { load(it) } }

    // ── add-assignment builder ──────────────────────────────────────────────

    fun selectSubject(subjectId: String?, subjectName: String?) {
        _state.value = _state.value.copy(
            draft = _state.value.draft.copy(subjectId = subjectId, subjectName = subjectName),
            saveError = null,
        )
    }

    fun toggleClass(classId: String) {
        val cur = _state.value.draft
        val next = cur.selectedClassIds.toMutableSet().apply {
            if (!add(classId)) remove(classId)
        }
        _state.value = _state.value.copy(draft = cur.copy(selectedClassIds = next), saveError = null)
    }

    fun toggleSection(section: String) {
        val cur = _state.value.draft
        val next = cur.selectedSections.toMutableSet().apply {
            if (!add(section)) remove(section)
        }
        _state.value = _state.value.copy(draft = cur.copy(selectedSections = next), saveError = null)
    }

    fun resetDraft() {
        _state.value = _state.value.copy(draft = AddAssignmentDraft(), saveError = null)
    }

    /** Build the (class+section) targets the current draft would create. */
    fun previewTargets(): List<Pair<String, String>> {
        val s = _state.value
        val opts = s.options ?: return emptyList()
        val draft = s.draft
        val chosenClasses = opts.classes.filter { it.classId in draft.selectedClassIds }
        return chosenClasses.flatMap { cls ->
            val sections = if (draft.selectedSections.isEmpty()) cls.sections
            else cls.sections.filter { it in draft.selectedSections }
            // If a class has no configured sections, fall back to selected ones.
            val effective = sections.ifEmpty { draft.selectedSections.toList() }
            effective.map { cls.name to it }
        }
    }

    fun saveDraft(onDone: () -> Unit = {}) {
        val id = teacherId ?: return
        val s = _state.value
        val draft = s.draft
        val opts = s.options
        if (draft.subjectId == null && draft.subjectName.isNullOrBlank()) {
            _state.value = s.copy(saveError = "Select a subject first.")
            return
        }
        if (draft.selectedClassIds.isEmpty()) {
            _state.value = s.copy(saveError = "Select at least one class.")
            return
        }
        val chosen = opts?.classes?.filter { it.classId in draft.selectedClassIds }.orEmpty()
        val targets = chosen.flatMap { cls ->
            val sections = if (draft.selectedSections.isEmpty()) cls.sections
            else cls.sections.filter { it in draft.selectedSections }
            val effective = sections.ifEmpty { draft.selectedSections.toList().ifEmpty { listOf("A") } }
            effective.map { sec ->
                AssignmentTarget(classId = cls.classId, className = cls.name, section = sec)
            }
        }
        if (targets.isEmpty()) {
            _state.value = s.copy(saveError = "Select at least one section.")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveError = null, lastSaveMessage = null)
            val token = token() ?: run {
                _state.value = _state.value.copy(isSaving = false, saveError = NOT_SIGNED_IN)
                return@launch
            }
            val req = AssignTeacherClassesRequest(
                subjectId = draft.subjectId,
                subjectName = draft.subjectName,
                assignments = targets,
            )
            when (val r = repository.bulkAssign(token, id, req)) {
                is NetworkResult.Success -> {
                    val body = r.data.data
                    val conflicts = body?.conflictCount ?: 0
                    val created = body?.createdCount ?: 0
                    val msg = buildString {
                        append("Added $created assignment${if (created == 1) "" else "s"}.")
                        if (conflicts > 0) append(" $conflicts skipped (duplicate/invalid).")
                    }
                    _state.value = _state.value.copy(
                        isSaving = false,
                        draft = AddAssignmentDraft(),
                        lastSaveMessage = msg,
                    )
                    refreshOverview(token)
                    onDone()
                }
                is NetworkResult.Error -> {
                    AppLogger.e(TAG, "bulkAssign error: ${r.message}")
                    _state.value = _state.value.copy(isSaving = false, saveError = r.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(isSaving = false, saveError = CONNECTION_ERROR)
            }
        }
    }

    fun removeAssignment(assignmentId: String) {
        val id = teacherId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(removingId = assignmentId, removeError = null)
            val token = token() ?: run {
                _state.value = _state.value.copy(removingId = null, removeError = NOT_SIGNED_IN)
                return@launch
            }
            when (val r = repository.removeAssignment(token, id, assignmentId)) {
                is NetworkResult.Success -> {
                    _state.value = _state.value.copy(removingId = null)
                    refreshOverview(token)
                }
                is NetworkResult.Error -> {
                    AppLogger.e(TAG, "removeAssignment error: ${r.message}")
                    _state.value = _state.value.copy(removingId = null, removeError = r.message)
                }
                is NetworkResult.ConnectionError ->
                    _state.value = _state.value.copy(removingId = null, removeError = CONNECTION_ERROR)
            }
        }
    }

    fun clearSaveMessage() { _state.value = _state.value.copy(lastSaveMessage = null) }

    private suspend fun refreshOverview(token: String) {
        val id = teacherId ?: return
        when (val r = repository.getOverview(token, id)) {
            is NetworkResult.Success -> _state.value = _state.value.copy(overview = r.data.data)
            else -> { /* keep existing overview on a refresh failure */ }
        }
    }

    private suspend fun token(): String? =
        preferenceRepository.getUserToken().first()?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "TeacherAssignmentVM"
        const val NOT_SIGNED_IN = "You are not signed in. Please log in again."
        const val CONNECTION_ERROR = "Connection error. Check your internet."
    }
}
