/*
 * File: SchoolRecordsViewModel.kt
 * Module: feature.admin.presentation
 *
 * RA-52: drives the admin Records rollups (attendance / marks / fees). Each
 * rollup is an independent sub-state with its own loading / error+retry so a
 * tab can fail and retry without affecting the others (LAW 3). Lazy: a tab
 * loads on first view via ensure*().
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.AttendanceSummaryDto
import com.littlebridge.enrollplus.feature.admin.domain.model.FeeLedgerDto
import com.littlebridge.enrollplus.feature.admin.domain.model.MarksSummaryDto
import com.littlebridge.enrollplus.feature.admin.domain.repository.RecordsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AttendanceSummaryUi(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val data: AttendanceSummaryDto? = null
)

data class MarksSummaryUi(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val data: MarksSummaryDto? = null
)

data class FeeLedgerUi(
    val isLoading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false,
    val data: FeeLedgerDto? = null
)

data class SchoolRecordsState(
    val attendance: AttendanceSummaryUi = AttendanceSummaryUi(),
    val marks: MarksSummaryUi = MarksSummaryUi(),
    val fees: FeeLedgerUi = FeeLedgerUi()
)

class SchoolRecordsViewModel(
    private val repository: RecordsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolRecordsState())
    val state: StateFlow<SchoolRecordsState> = _state.asStateFlow()

    private suspend fun token(): String? = preferenceRepository.getUserToken().first()

    // ── Attendance ────────────────────────────────────────────────────────────
    fun ensureAttendance() {
        if (_state.value.attendance.loaded || _state.value.attendance.isLoading) return
        loadAttendance()
    }

    fun loadAttendance() {
        viewModelScope.launch {
            _state.value = _state.value.copy(attendance = _state.value.attendance.copy(isLoading = true, error = null))
            val t = token()
            if (t.isNullOrBlank()) {
                _state.value = _state.value.copy(attendance = _state.value.attendance.copy(isLoading = false, error = "You are not signed in. Please log in again."))
                return@launch
            }
            when (val r = repository.getAttendanceSummary(t)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(attendance = AttendanceSummaryUi(isLoading = false, error = null, loaded = true, data = r.data.data))
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolRecordsVM", "attendance error: ${r.message}")
                    _state.value = _state.value.copy(attendance = _state.value.attendance.copy(isLoading = false, error = r.message))
                }
                is NetworkResult.ConnectionError -> _state.value = _state.value.copy(attendance = _state.value.attendance.copy(isLoading = false, error = "Connection error. Check your internet."))
            }
        }
    }

    // ── Marks ─────────────────────────────────────────────────────────────────
    fun ensureMarks() {
        if (_state.value.marks.loaded || _state.value.marks.isLoading) return
        loadMarks()
    }

    fun loadMarks() {
        viewModelScope.launch {
            _state.value = _state.value.copy(marks = _state.value.marks.copy(isLoading = true, error = null))
            val t = token()
            if (t.isNullOrBlank()) {
                _state.value = _state.value.copy(marks = _state.value.marks.copy(isLoading = false, error = "You are not signed in. Please log in again."))
                return@launch
            }
            when (val r = repository.getMarksSummary(t)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(marks = MarksSummaryUi(isLoading = false, error = null, loaded = true, data = r.data.data))
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolRecordsVM", "marks error: ${r.message}")
                    _state.value = _state.value.copy(marks = _state.value.marks.copy(isLoading = false, error = r.message))
                }
                is NetworkResult.ConnectionError -> _state.value = _state.value.copy(marks = _state.value.marks.copy(isLoading = false, error = "Connection error. Check your internet."))
            }
        }
    }

    // ── Fees ──────────────────────────────────────────────────────────────────
    fun ensureFees() {
        if (_state.value.fees.loaded || _state.value.fees.isLoading) return
        loadFees()
    }

    fun loadFees() {
        viewModelScope.launch {
            _state.value = _state.value.copy(fees = _state.value.fees.copy(isLoading = true, error = null))
            val t = token()
            if (t.isNullOrBlank()) {
                _state.value = _state.value.copy(fees = _state.value.fees.copy(isLoading = false, error = "You are not signed in. Please log in again."))
                return@launch
            }
            when (val r = repository.getFeeLedger(t)) {
                is NetworkResult.Success -> _state.value = _state.value.copy(fees = FeeLedgerUi(isLoading = false, error = null, loaded = true, data = r.data.data))
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolRecordsVM", "fees error: ${r.message}")
                    _state.value = _state.value.copy(fees = _state.value.fees.copy(isLoading = false, error = r.message))
                }
                is NetworkResult.ConnectionError -> _state.value = _state.value.copy(fees = _state.value.fees.copy(isLoading = false, error = "Connection error. Check your internet."))
            }
        }
    }
}
