package com.littlebridge.enrollplus.feature.schools.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.schools.data.remote.DiscoveredSchoolDto
import com.littlebridge.enrollplus.feature.schools.data.remote.KtorSchoolApi
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One row in the discovery marketplace list. Mirrors the shape of [DiscoveredSchoolDto]
 * but uses `null`-friendly UI primitives (distance is a pre-formatted string, rating a
 * Float for the SRI pill, etc.). Anything the backend did not send is left blank — we
 * never fabricate (LAW 6).
 */
data class DiscoveredSchool(
    val id: String,
    val name: String,
    val location: String,
    val rating: Double,
    val image: String?,
    /** Pre-formatted "1.8 km" string when the server gave us a `distance_km`, else `null`. */
    val distanceLabel: String?,
    /** "CBSE" / "ICSE" / "UP Board" — real `schools.board` column; null on older servers. */
    val board: String? = null,
    /** "English" / "Hindi" — real `schools.medium` column. */
    val medium: String? = null,
    /** "co_ed" / "boys" / "girls" — real `schools.school_gender` column. */
    val schoolGender: String? = null,
    /** Full street address for the profile Location card; null when the school hasn't set one. */
    val address: String? = null,
)

data class SchoolDiscoveryState(
    val schools: List<DiscoveredSchool> = emptyList(),
    val sortedBy: String = "name",
    val query: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * `SchoolDiscoveryViewModel` — the only consumer of the real
 * `GET /api/v1/parent/schools/discover` endpoint. Drives [com.littlebridge.enrollplus.ui.v2
 * .screens.discovery.DiscoveryScreenV2], replacing the [com.littlebridge.enrollplus.ui.v2
 * .data.MockV2.discoverySchools] seed data.
 *
 * The endpoint is authenticated (any role can browse) — we read the bearer token from
 * [PreferenceRepository] just like every other wired VM (TrackProgressViewModel, …).
 * Today we call the geo-less variant; lat/lng could be plumbed in from the platform later
 * (Phase D) but the endpoint already gracefully falls back to alphabetical when missing,
 * so the screen works end-to-end immediately.
 */
class SchoolDiscoveryViewModel(
    private val api: KtorSchoolApi,
    private val preferences: PreferenceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SchoolDiscoveryState())
    val state: StateFlow<SchoolDiscoveryState> = _state.asStateFlow()

    init {
        load()
    }

    /** Fires the real network call. Public so the screen can wire a "Retry" button. */
    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)

            val token = preferences.getUserToken().first()
            if (token.isNullOrBlank()) {
                // Unauthenticated marketplace browsing isn't supported by the backend route
                // (`call.principalUserId() ?: fail 401`). Surface a clear, actionable message
                // rather than masking the auth gate.
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Please sign in to browse schools.",
                )
                return@launch
            }

            when (val r = api.discoverSchools(token = token)) {
                is NetworkResult.Success -> {
                    val data = r.data.data
                    val schools = data?.schools.orEmpty().map { it.toUi() }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        schools = schools,
                        sortedBy = data?.sortedBy ?: "name",
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SchoolDiscoveryVM", "discoverSchools error: ${r.message}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = r.message,
                    )
                }
                NetworkResult.ConnectionError -> {
                    AppLogger.e("SchoolDiscoveryVM", "discoverSchools connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet.",
                    )
                }
            }
        }
    }

    /** Updates the in-memory search query — filtering is applied by the screen against state. */
    fun setQuery(q: String) {
        _state.value = _state.value.copy(query = q)
    }
}

private fun DiscoveredSchoolDto.toUi(): DiscoveredSchool = DiscoveredSchool(
    id = id,
    name = name,
    location = location,
    rating = rating,
    image = image,
    distanceLabel = distanceKm?.let { "${it} km" },
    board = board,
    medium = medium,
    schoolGender = schoolGender,
    address = address,
)
