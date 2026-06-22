package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.state.SelectedChildHolder
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardChildSummary
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentAttendanceData
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentMarksData
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentSyllabusData
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RA-43 + RA-56: backs ParentAcademicsScreenV2's Attendance / Marks / Syllabus
 * tabs with REAL child-scoped data. Holds the children list + the selected child
 * (RA-56 child switcher) and the three academic datasets for that child. Loads
 * lazily per tab so we never fetch data the parent isn't looking at.
 */
data class ParentAcademicsState(
    val children: List<DashboardChildSummary> = emptyList(),
    val selectedChildId: String? = null,
    val childrenLoading: Boolean = false,
    val childrenError: String? = null,

    val attendance: ParentAttendanceData? = null,
    val attendanceLoading: Boolean = false,
    val attendanceError: String? = null,

    val marks: ParentMarksData? = null,
    val marksLoading: Boolean = false,
    val marksError: String? = null,

    val syllabus: ParentSyllabusData? = null,
    val syllabusLoading: Boolean = false,
    val syllabusError: String? = null,
) {
    val selectedChild: DashboardChildSummary?
        get() = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()
}

class ParentAcademicsViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository,
    // RA-S05: shared selected-child source of truth across all parent tabs.
    private val selectedChildHolder: SelectedChildHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentAcademicsState())
    val state: StateFlow<ParentAcademicsState> = _state.asStateFlow()

    init {
        loadChildren()
        // RA-S05: when another tab switches the child, reflect it here and
        // refresh this tab's datasets for the newly-selected child.
        viewModelScope.launch {
            selectedChildHolder.selectedChildId.collect { shared ->
                if (shared != null && shared != _state.value.selectedChildId) {
                    applyExternalSelection(shared)
                }
            }
        }
    }

    /** RA-S05: adopt a child selection that originated on another tab. */
    private fun applyExternalSelection(childId: String) {
        _state.update {
            it.copy(
                selectedChildId = childId,
                attendance = null, marks = null, syllabus = null,
            )
        }
        loadAttendance(childId)
    }

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    /** Load the children list (RA-56 switcher) from the dashboard endpoint. */
    fun loadChildren() {
        viewModelScope.launch {
            _state.update { it.copy(childrenLoading = true, childrenError = null) }
            val token = token() ?: run {
                _state.update { it.copy(childrenLoading = false, childrenError = "Not authenticated") }
                return@launch
            }
            when (val result = repository.getDashboard(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    val children = data.children.ifEmpty { listOfNotNull(data.childSummary) }
                    _state.update {
                        // RA-S05: prefer the shared selection (set by whichever tab
                        // loaded first), then the local one, then the first child.
                        val sharedSel = selectedChildHolder.selectedChildId.value
                            ?.takeIf { id -> children.any { c -> c.id == id } }
                        val keep = sharedSel
                            ?: it.selectedChildId?.takeIf { id -> children.any { c -> c.id == id } }
                        it.copy(
                            childrenLoading = false,
                            children = children,
                            selectedChildId = keep ?: children.firstOrNull()?.id,
                        )
                    }
                    // RA-S05: seed the shared holder so other tabs converge.
                    selectedChildHolder.selectIfUnset(_state.value.selectedChildId)
                    // Eagerly load the first tab the parent is most likely to open.
                    _state.value.selectedChild?.id?.let { loadAttendance(it) }
                }
                is NetworkResult.Error -> _state.update { it.copy(childrenLoading = false, childrenError = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(childrenLoading = false, childrenError = "Connection error") }
            }
        }
    }

    /** RA-56: switch the active child and refresh all loaded datasets for them. */
    fun selectChild(childId: String) {
        if (childId == _state.value.selectedChildId) return
        _state.update {
            it.copy(
                selectedChildId = childId,
                // Drop the previous child's data so stale rows never flash.
                attendance = null, marks = null, syllabus = null,
            )
        }
        // RA-S05: broadcast so Home/Fees/Leave follow this selection.
        selectedChildHolder.select(childId)
        loadAttendance(childId)
    }

    fun loadAttendance(childId: String? = null) {
        val resolvedChildId = childId ?: currentChildId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(attendanceLoading = true, attendanceError = null) }
            val token = token() ?: run {
                _state.update { it.copy(attendanceLoading = false, attendanceError = "Not authenticated") }; return@launch
            }
            when (val r = repository.getChildAttendance(token, resolvedChildId)) {
                is NetworkResult.Success -> _state.update { it.copy(attendanceLoading = false, attendance = r.data.data) }
                is NetworkResult.Error -> _state.update { it.copy(attendanceLoading = false, attendanceError = r.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(attendanceLoading = false, attendanceError = "Connection error") }
            }
        }
    }

    fun loadMarks(childId: String? = null) {
        val resolvedChildId = childId ?: currentChildId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(marksLoading = true, marksError = null) }
            val token = token() ?: run {
                _state.update { it.copy(marksLoading = false, marksError = "Not authenticated") }; return@launch
            }
            when (val r = repository.getChildMarks(token, resolvedChildId)) {
                is NetworkResult.Success -> _state.update { it.copy(marksLoading = false, marks = r.data.data) }
                is NetworkResult.Error -> _state.update { it.copy(marksLoading = false, marksError = r.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(marksLoading = false, marksError = "Connection error") }
            }
        }
    }

    fun loadSyllabus(childId: String? = null) {
        val resolvedChildId = childId ?: currentChildId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(syllabusLoading = true, syllabusError = null) }
            val token = token() ?: run {
                _state.update { it.copy(syllabusLoading = false, syllabusError = "Not authenticated") }; return@launch
            }
            when (val r = repository.getChildSyllabus(token, resolvedChildId)) {
                is NetworkResult.Success -> _state.update { it.copy(syllabusLoading = false, syllabus = r.data.data) }
                is NetworkResult.Error -> _state.update { it.copy(syllabusLoading = false, syllabusError = r.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(syllabusLoading = false, syllabusError = "Connection error") }
            }
        }
    }

    private fun currentChildId(): String? = _state.value.selectedChild?.id
}
