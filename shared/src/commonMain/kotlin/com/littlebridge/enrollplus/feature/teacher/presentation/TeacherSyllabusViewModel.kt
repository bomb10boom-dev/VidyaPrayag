package com.littlebridge.enrollplus.feature.teacher.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.teacher.domain.model.SyllabusUnitDto
import com.littlebridge.enrollplus.feature.teacher.domain.model.UpdateSyllabusRequest
import com.littlebridge.enrollplus.feature.teacher.domain.repository.TeacherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyllabusUnit(
    val id: String,
    val title: String,
    val isCovered: Boolean,
    val coveredOn: String?,
)

data class TeacherSyllabusState(
    val classId: String = "",
    val subject: String = "",
    val className: String = "",
    val overallProgress: Float = 0f,
    val units: List<SyllabusUnit> = emptyList(),
    val isLoading: Boolean = false,
    val updatingUnitId: String? = null,
    val error: String? = null,
) {
    val coveredCount: Int get() = units.count { it.isCovered }
}

class TeacherSyllabusViewModel(
    private val repository: TeacherRepository,
    private val preferenceRepository: PreferenceRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TeacherSyllabusState())
    val state: StateFlow<TeacherSyllabusState> = _state.asStateFlow()

    /** Load syllabus units for a class + subject. The screen supplies both. */
    fun load(classId: String, subject: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(classId = classId, subject = subject, isLoading = true, error = null)
            }
            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(isLoading = false, error = "Not authenticated") }
                return@launch
            }
            when (val result = repository.getSyllabus(token, classId, subject)) {
                is NetworkResult.Success -> {
                    val d = result.data.data
                    _state.update {
                        it.copy(
                            isLoading = false,
                            className = d.className,
                            overallProgress = d.overallProgress,
                            units = d.units.map { u -> u.toUi() },
                        )
                    }
                }
                is NetworkResult.Error -> _state.update { it.copy(isLoading = false, error = result.message) }
                is NetworkResult.ConnectionError -> _state.update { it.copy(isLoading = false, error = "Connection error") }
            }
        }
    }

    /**
     * Toggle a unit's coverage. Optimistically flips local state, then persists via
     * `updateSyllabus`; reverts on failure. Refreshes overall progress on success.
     */
    fun toggleUnit(unitId: String) {
        viewModelScope.launch {
            val before = _state.value.units
            val target = before.firstOrNull { it.id == unitId } ?: return@launch
            val newValue = !target.isCovered

            // Optimistic local flip.
            _state.update { s ->
                s.copy(
                    updatingUnitId = unitId,
                    error = null,
                    units = s.units.map { if (it.id == unitId) it.copy(isCovered = newValue) else it },
                )
            }

            val token = preferenceRepository.getUserToken().first()
            if (token == null) {
                _state.update { it.copy(updatingUnitId = null, units = before, error = "Not authenticated") }
                return@launch
            }
            val request = UpdateSyllabusRequest(unitId = unitId, isCovered = newValue)
            when (val result = repository.updateSyllabus(token, request)) {
                is NetworkResult.Success -> {
                    _state.update { s ->
                        s.copy(updatingUnitId = null, overallProgress = recomputeProgress(s.units))
                    }
                }
                is NetworkResult.Error ->
                    _state.update { it.copy(updatingUnitId = null, units = before, error = result.message) }
                is NetworkResult.ConnectionError ->
                    _state.update { it.copy(updatingUnitId = null, units = before, error = "Connection error") }
            }
        }
    }

    private fun recomputeProgress(units: List<SyllabusUnit>): Float =
        if (units.isEmpty()) 0f else units.count { it.isCovered }.toFloat() / units.size
}

private fun SyllabusUnitDto.toUi() = SyllabusUnit(id, title, isCovered, coveredOn)
