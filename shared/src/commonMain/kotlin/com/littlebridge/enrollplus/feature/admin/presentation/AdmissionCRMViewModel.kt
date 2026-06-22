/*
 * File: AdmissionCRMViewModel.kt
 * Module: feature.admin.presentation
 *
 * Drives the AdmissionCRM dashboard. On init we hit
 *   GET /api/v1/admissions/enquiries/summary
 * to populate the four KPI cards + the recent-enquiries list, and we expose:
 *   - [state] : a single immutable [AdmissionCRMState] (totals, list, derived rate)
 *   - [isLoading], [errorMessage] : standard loading state
 *   - [refresh] : explicit re-pull
 *   - [updateEnquiryStatus] : optimistic patch + PATCH .../{id}/status
 *
 * Server contract is in:
 *   server/.../feature/admissions/AdmissionRouting.kt
 */
package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.model.Enquiry
import com.littlebridge.enrollplus.feature.admin.domain.model.EnquirySummary
import com.littlebridge.enrollplus.feature.admin.domain.repository.AdmissionRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * UI state for the AdmissionCRM dashboard.
 *
 * Initialised to all-zeroes until the summary endpoint resolves; the UI keys
 * its loading state off [AdmissionCRMViewModel.isLoading] rather than off
 * "are these zeroes".
 *
 * `conversionRate` is derived locally from `summaryCount` (converted /
 * (converted + followUps) * 100). The server also returns an `efficiency`
 * string ("85%") which we keep separately for display.
 */
data class AdmissionCRMState(
    val totalEnquiries: Int = 0,
    val newEnquiries: Int = 0,
    val followUps: Int = 0,
    val conversions: Int = 0,
    val conversionRate: Float = 0f,
    val efficiencyLabel: String = "0%",
    val recentEnquiries: List<Enquiry> = emptyList()
)

class AdmissionCRMViewModel(
    private val admissionRepository: AdmissionRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdmissionCRMState())
    val state: StateFlow<AdmissionCRMState> = _state.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("AdmissionCRMVM", "No auth token in prefs; skipping refresh")
                _isLoading.value = false
                return@launch
            }

            when (val result = admissionRepository.getSummary(token)) {
                is NetworkResult.Success -> applySummary(result.data)
                is NetworkResult.Error -> {
                    _errorMessage.value = result.message
                    AppLogger.e("AdmissionCRMVM", "getSummary failed: ${result.message}")
                }
                is NetworkResult.ConnectionError -> {
                    _errorMessage.value = "Connection error"
                    AppLogger.e("AdmissionCRMVM", "getSummary connection error")
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Optimistically patch the local copy of an enquiry's status, then PATCH
     * the server. If the server call fails, we revert and surface the error.
     *
     * `newStatus` must be one of [Enquiry.ALLOWED_STATUSES] - we guard against
     * accidental UI typos to avoid a 400 round-trip.
     */
    fun updateEnquiryStatus(enquiryId: String, newStatus: String) {
        if (newStatus !in Enquiry.ALLOWED_STATUSES) {
            AppLogger.e(
                "AdmissionCRMVM",
                "Refusing to update with unknown status: '$newStatus' (allowed: ${Enquiry.ALLOWED_STATUSES})"
            )
            _errorMessage.value = "Unsupported status: $newStatus"
            return
        }

        val previous = _state.value
        val patched = previous.copy(
            recentEnquiries = previous.recentEnquiries.map {
                if (it.id == enquiryId) it.copy(status = newStatus) else it
            }
        )
        _state.value = patched

        viewModelScope.launch {
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                AppLogger.d("AdmissionCRMVM", "No auth token in prefs; reverting optimistic update")
                _state.value = previous
                _errorMessage.value = "Not signed in"
                return@launch
            }

            when (val result = admissionRepository.updateEnquiryStatus(token, enquiryId, newStatus)) {
                is NetworkResult.Success -> {
                    AppLogger.d("AdmissionCRMVM", "Updated enquiry $enquiryId -> $newStatus")
                    // Re-pull to keep summary counts in sync with the new status.
                    refresh()
                }
                is NetworkResult.Error -> {
                    AppLogger.e("AdmissionCRMVM", "updateEnquiryStatus failed: ${result.message}")
                    _state.value = previous
                    _errorMessage.value = result.message
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("AdmissionCRMVM", "updateEnquiryStatus connection error")
                    _state.value = previous
                    _errorMessage.value = "Connection error"
                }
            }
        }
    }

    /** Lets the screen dismiss a transient error toast/snackbar. */
    fun clearError() {
        _errorMessage.value = null
    }

    private fun applySummary(summary: EnquirySummary) {
        val c = summary.summaryCount
        val rate = computeConversionRate(c.converted, c.followUps)
        _state.value = AdmissionCRMState(
            totalEnquiries = c.total,
            newEnquiries = c.new,
            followUps = c.followUps,
            conversions = c.converted,
            conversionRate = rate,
            efficiencyLabel = summary.efficiency.ifBlank { formatPercent(rate) },
            recentEnquiries = summary.recentEnquiries
        )
    }

    private fun computeConversionRate(converted: Int, followUps: Int): Float {
        val denom = converted + followUps
        return if (denom == 0) 0f else (converted.toFloat() / denom.toFloat()) * 100f
    }

    private fun formatPercent(value: Float): String {
        // Single-decimal so "22.5%" reads cleanly in the insight card.
        val rounded = (value * 10).toInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}%" else "$rounded%"
    }
}
