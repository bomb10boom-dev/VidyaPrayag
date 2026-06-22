package com.littlebridge.enrollplus.feature.parent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.state.SelectedChildHolder
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardAlertDto
import com.littlebridge.enrollplus.feature.parent.domain.model.DashboardChildSummary
import com.littlebridge.enrollplus.feature.parent.domain.model.FeaturedSchoolDto
import com.littlebridge.enrollplus.feature.parent.domain.repository.ParentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI-facing parent-home state, sourced from the real `GET /api/v1/parent/dashboard`. */
data class ParentHomeState(
    val greeting: String = "",
    /** All active children (RA-31). May be empty (no child) or have 2+ entries. */
    val children: List<DashboardChildSummary> = emptyList(),
    /** Id of the child currently shown in the hero; null until children load. */
    val selectedChildId: String? = null,
    val alerts: List<DashboardAlertDto> = emptyList(),
    val featuredSchools: List<FeaturedSchoolDto> = emptyList(),
    val curationLogic: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    /** The child currently selected (falls back to the first child). */
    val childSummary: DashboardChildSummary?
        get() = children.firstOrNull { it.id == selectedChildId } ?: children.firstOrNull()
}

/**
 * ParentHomeViewModel — drives [ParentHomeScreenV2] off the real
 * `GET /api/v1/parent/dashboard` endpoint (audit findings **J** §8.1 + **SHAREDVM** §5.4).
 *
 * Before this fix the Home tab borrowed `TrackProgressViewModel` (the *academics* VM,
 * also used by the Academics tab) as a stand-in, so the richest, genuinely-real parent
 * endpoint — child summary + overdue-fee alerts + featured schools + greeting — was
 * orphaned (never called by any client), and Home/Academics shared one VM/state.
 * This VM gives Home its own real source of truth and unshares the two tabs.
 */
class ParentHomeViewModel(
    private val repository: ParentRepository,
    private val preferenceRepository: PreferenceRepository,
    // RA-S05: shared selected-child source of truth across all parent tabs.
    private val selectedChildHolder: SelectedChildHolder,
) : ViewModel() {
    private val _state = MutableStateFlow(ParentHomeState())
    val state: StateFlow<ParentHomeState> = _state.asStateFlow()

    init {
        load()
        // RA-S05: mirror the shared selection into local state so a child picked
        // on another tab (Academics/Fees) is reflected in the Home hero too.
        viewModelScope.launch {
            selectedChildHolder.selectedChildId.collect { shared ->
                if (shared != null && shared != _state.value.selectedChildId) {
                    _state.update { it.copy(selectedChildId = shared) }
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not signed in") }
                return@launch
            }
            when (val result = repository.getDashboard(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    // RA-31: prefer the `children` array; fall back to the single
                    // `child_summary` when talking to an older server build.
                    val children = data.children.ifEmpty { listOfNotNull(data.childSummary) }
                    _state.update {
                        // Preserve the current selection if it's still present,
                        // otherwise default to the first child.
                        // RA-S05: prefer the shared selection, then the local one,
                        // then default to the first child — and publish the result
                        // back to the shared holder so the other tabs converge.
                        val sharedSel = selectedChildHolder.selectedChildId.value
                            ?.takeIf { id -> children.any { c -> c.id == id } }
                        val keepSelected = sharedSel
                            ?: it.selectedChildId?.takeIf { id -> children.any { c -> c.id == id } }
                        val resolved = keepSelected ?: children.firstOrNull()?.id
                        selectedChildHolder.selectIfUnset(resolved)
                        it.copy(
                            isLoading = false,
                            greeting = data.greeting,
                            children = children,
                            selectedChildId = resolved,
                            alerts = data.alerts,
                            featuredSchools = data.featuredSchools,
                            curationLogic = data.curationLogic,
                        )
                    }
                }
                is NetworkResult.Error ->
                    _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    /** RA-31: switch the child shown in the hero (no network round-trip). */
    fun selectChild(childId: String) {
        _state.update { it.copy(selectedChildId = childId) }
        // RA-S05: broadcast so Academics/Fees/Leave follow this selection.
        selectedChildHolder.select(childId)
    }
}
