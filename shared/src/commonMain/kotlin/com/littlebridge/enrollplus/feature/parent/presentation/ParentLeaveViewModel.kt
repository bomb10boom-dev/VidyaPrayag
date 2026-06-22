package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.state.SelectedChildHolder
import com.littlebridge.enrollplus.feature.parent.domain.model.CreateParentLeaveRequest
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardChildSummary
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentLeaveDto
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * RA-44: backs the parent leave screen. Loads the children list (RA-56 switcher)
 * + the parent's own submitted requests, and submits a new leave application for
 * the selected child. Three states per LAW: loading / error / empty are all
 * surfaced so the screen never shows only the happy path.
 */
data class ParentLeaveState(
    val children: List<DashboardChildSummary> = emptyList(),
    val selectedChildId: String? = null,

    val requests: List<ParentLeaveDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,

    // apply-form transient state
    val submitting: Boolean = false,
    val submitError: String? = null,
    val submittedOk: Boolean = false,
) {
    val selectedChild: DashboardChildSummary?
        get() = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()

    val isEmpty: Boolean get() = !loading && error == null && requests.isEmpty()
}

class ParentLeaveViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository,
    // RA-S05: shared selected-child source of truth across all parent tabs.
    private val selectedChildHolder: SelectedChildHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentLeaveState())
    val state: StateFlow<ParentLeaveState> = _state.asStateFlow()

    init {
        load()
        // RA-S05: adopt a child selection made on another tab (no data reload
        // needed here — the leave list is parent-wide; only the apply-form
        // selector follows the shared child).
        viewModelScope.launch {
            selectedChildHolder.selectedChildId.collect { shared ->
                if (shared != null && shared != _state.value.selectedChildId) {
                    _state.update { it.copy(selectedChildId = shared) }
                }
            }
        }
    }

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    /** Load children (for the apply selector) + the parent's existing requests. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val token = token() ?: run {
                _state.update { it.copy(loading = false, error = "Not authenticated") }
                return@launch
            }
            // Children for the picker (best-effort; failure here is non-fatal).
            when (val dash = repository.getDashboard(token)) {
                is NetworkResult.Success -> {
                    val data = dash.data.data
                    val children = data.children.ifEmpty { listOfNotNull(data.childSummary) }
                    _state.update {
                        // RA-S05: prefer the shared selection, then local, then first.
                        val sharedSel = selectedChildHolder.selectedChildId.value
                            ?.takeIf { id -> children.any { c -> c.id == id } }
                        val keep = sharedSel
                            ?: it.selectedChildId?.takeIf { id -> children.any { c -> c.id == id } }
                        it.copy(children = children, selectedChildId = keep ?: children.firstOrNull()?.id)
                    }
                    selectedChildHolder.selectIfUnset(_state.value.selectedChildId)
                }
                else -> { /* keep going; the request list is the primary content */ }
            }
            // The parent's own leave requests.
            when (val r = repository.getLeaveRequests(token)) {
                is NetworkResult.Success ->
                    _state.update { it.copy(loading = false, requests = r.data.data.requests) }
                is NetworkResult.Error ->
                    _state.update { it.copy(loading = false, error = r.message) }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(loading = false, error = "Connection error") }
            }
        }
    }

    fun selectChild(childId: String) {
        _state.update { it.copy(selectedChildId = childId) }
        // RA-S05: broadcast so Home/Academics/Fees follow this selection.
        selectedChildHolder.select(childId)
    }

    /** Submit a leave application for the selected child. */
    fun apply(dateFrom: String, dateTo: String, reason: String) {
        val childId = _state.value.selectedChild?.id ?: run {
            _state.update { it.copy(submitError = "Select a child first") }
            return
        }
        if (dateFrom.isBlank() || dateTo.isBlank()) {
            _state.update { it.copy(submitError = "Choose the leave dates") }; return
        }
        if (reason.isBlank()) {
            _state.update { it.copy(submitError = "Add a reason for the leave") }; return
        }
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, submitError = null, submittedOk = false) }
            val token = token() ?: run {
                _state.update { it.copy(submitting = false, submitError = "Not authenticated") }
                return@launch
            }
            val request = CreateParentLeaveRequest(
                childId = childId,
                dateFrom = dateFrom,
                dateTo = dateTo,
                reason = reason.trim(),
            )
            when (val r = repository.applyLeave(token, request)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(submitting = false, submittedOk = true) }
                    load() // refresh the list with the new pending request
                }
                is NetworkResult.Error ->
                    _state.update { it.copy(submitting = false, submitError = r.message) }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(submitting = false, submitError = "Connection error") }
            }
        }
    }

    /** Reset the one-shot submit flags after the UI has consumed them. */
    fun consumeSubmitResult() {
        _state.update { it.copy(submittedOk = false, submitError = null) }
    }
}
