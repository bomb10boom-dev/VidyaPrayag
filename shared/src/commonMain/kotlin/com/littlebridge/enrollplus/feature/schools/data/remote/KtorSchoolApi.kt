package com.littlebridge.enrollplus.feature.schools.data.remote

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.schools.domain.model.School
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Real Ktor client for the schools-discovery endpoint.
 *
 * Previously this class was a stub that returned hard-coded `picsum.photos` school records,
 * isolated from the network entirely (BACKEND_GAPS.md §3 — "the one stubbed API client").
 * This rewrite:
 *
 *  1. Calls the real server route `GET /api/v1/parent/schools/discover` (defined in
 *     `server/.../feature/parent/ParentDashboardRouting.kt`). The endpoint requires a valid
 *     JWT — any authenticated role can browse — so we pass a bearer token through.
 *  2. Decodes the canonical envelope (`success`, `message`, `data { schools, sorted_by }`)
 *     and the [DiscoveredSchoolDto] payload using `@SerialName` to match the server's
 *     snake_case keys (e.g. `distance_km`, `sorted_by`).
 *  3. Wraps the call in `safeApiCall { ... }` so every transport / parse / non-2xx case
 *     funnels through the shared [NetworkResult] sealed type — identical to every other
 *     real API client (`ParentApi`, `TeacherApi`, `AdminApi`, …).
 *
 * The legacy `fetchSchools(): List<School>` API is retained as a thin adapter that maps the
 * new wire DTO onto the existing [School] domain model so [com.littlebridge.enrollplus.feature
 * .schools.data.repository.SchoolRepositoryImpl] and the Room cache continue to work. The
 * marketplace screen consumes [discoverSchools] directly to get the canonical fields.
 */
class KtorSchoolApi(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private fun url(path: String): String {
        val b = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val p = if (path.startsWith("/")) path.substring(1) else path
        return "$b$p"
    }

    /**
     * Real call: `GET /api/v1/parent/schools/discover`. Returns the full envelope so the
     * caller can read `sortedBy` if it wants to display "Showing nearest 12 schools" etc.
     *
     * @param token bearer JWT from `PreferenceRepository.getUserToken()`. The route is
     *              authenticated; an empty token will trigger `NetworkResult.Error(401)`.
     * @param lat   optional latitude — when present (with [lng]), the server returns
     *              nearest-first ordering and populates `distance_km`.
     * @param lng   optional longitude — see [lat].
     * @param radiusKm filter to schools within this many kilometres of [lat]/[lng].
     * @param city  optional city filter (ignored when lat/lng provided).
     * @param limit cap on result size; server clamps to 1..100.
     */
    suspend fun discoverSchools(
        token: String,
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Double? = null,
        city: String? = null,
        limit: Int? = null,
    ): NetworkResult<SchoolDiscoveryEnvelope> {
        return safeApiCall {
            client.get(url("api/v1/parent/schools/discover")) {
                if (lat != null) parameter("lat", lat)
                if (lng != null) parameter("lng", lng)
                if (radiusKm != null) parameter("radius_km", radiusKm)
                if (!city.isNullOrBlank()) parameter("city", city)
                if (limit != null) parameter("limit", limit)
            }
        }
    }

    /**
     * Compatibility shim for the existing [com.littlebridge.enrollplus.feature.schools.data
     * .repository.SchoolRepositoryImpl] / Room cache path. Maps each [DiscoveredSchoolDto] onto
     * the cached [School] model. Fields the new endpoint does not supply (`board`,
     * `description`, `feesRange`) are returned as empty strings so we never fabricate data
     * (LAW 6) — the marketplace screen reads those from the real DTO directly via
     * [discoverSchools] instead of this adapter.
     *
     * The existing repository contract is `suspend fun fetchSchools(): List<School>` with no
     * token, so a caller that wants real data must use the token-bearing overload added here.
     * The no-arg overload is retained but returns an empty list (the old hard-coded mock data
     * is gone — see LAW 6 again).
     */
    suspend fun fetchSchools(token: String): List<School> {
        return when (val r = discoverSchools(token)) {
            is NetworkResult.Success -> r.data.data?.schools.orEmpty().map { it.toDomain() }
            is NetworkResult.Error -> emptyList()
            NetworkResult.ConnectionError -> emptyList()
        }
    }

    /**
     * Token-less overload kept only because the existing [com.littlebridge.enrollplus.feature
     * .schools.domain.repository.SchoolRepository.refreshSchools] contract has no token. The
     * server route is authenticated so this can only return an empty list. Removing the stub
     * data here is intentional — see BACKEND_GAPS.md §3 and LAW 6.
     */
    suspend fun fetchSchools(): List<School> = emptyList()
}

/** Maps a wire DTO to the cached [School] domain model. */
private fun DiscoveredSchoolDto.toDomain(): School = School(
    id = id,
    name = name,
    location = location,
    board = "",            // not provided by /schools/discover — LAW 6: no fabrication
    description = "",      // ditto
    imageUrl = image.orEmpty(),
    sriScore = rating,
    feesRange = "",        // ditto
    isVerified = false,
)

// ───────────────────────────────────────────────────────────────────────────
// Wire DTOs — must mirror server/.../ParentDashboardRouting.kt exactly
// (DiscoveredSchool / SchoolDiscoveryResponse / ApiResponse envelope).
// ───────────────────────────────────────────────────────────────────────────

@Serializable
data class DiscoveredSchoolDto(
    val id: String,
    val name: String,
    val rating: Double,
    val location: String,
    val image: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    // Public-profile fields (board badge, medium chip, co-ed flag, address) —
    // nullable so the client also deserialises responses from older servers.
    val board: String? = null,
    val medium: String? = null,
    @SerialName("school_gender") val schoolGender: String? = null,
    val address: String? = null,
)

@Serializable
data class SchoolDiscoveryDataDto(
    val schools: List<DiscoveredSchoolDto> = emptyList(),
    @SerialName("sorted_by") val sortedBy: String = "name",
)

/** Mirrors `core.ApiResponse<T>` envelope on the server (`success`, `message`, `data`). */
@Serializable
data class SchoolDiscoveryEnvelope(
    val success: Boolean = true,
    val message: String = "",
    val data: SchoolDiscoveryDataDto? = null,
)
