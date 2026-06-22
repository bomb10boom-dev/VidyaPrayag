/*
 * File: ParentDashboardRouting.kt
 * Module: feature.parent
 *
 * Endpoint: GET /api/v1/parent/dashboard   (JWT)
 *
 * Spec ref: parent_api_spec.artifact.md §Module: Core Dashboard & Progress
 *                                       §Screen: Parent Dashboard (Home)
 *
 * The primary "handshake" API. Drives the home screen:
 *   - greeting           : time-of-day + parent first-name (from JWT.name claim)
 *   - child_summary      : first active child (kept for backward compatibility)
 *   - children           : ALL active children of the parent (RA-31 multi-child)
 *   - alerts             : OVERDUE fees → CRITICAL alert; INFO fallback from CMS
 *   - featured_schools   : top 5 active schools from the SchoolsTable
 *   - curation_logic     : pulled from app_config (CMS string)
 *
 * Falls back gracefully when the parent has no child yet (returns null
 * child_summary but still responds 200 so the UI can show an empty-state).
 */
package com.littlebridge.enrollplus.feature.parent

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalName
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.AppUsersTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.FeeRecordsTable
import com.littlebridge.enrollplus.db.SchoolsTable
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalTime
import java.util.UUID

@Serializable
data class ChildSummary(
    val id: String,
    val name: String,
    @SerialName("overall_progress") val overallProgress: Double,
    @SerialName("current_level") val currentLevel: Int,
    @SerialName("attendance_status") val attendanceStatus: String,
    @SerialName("profile_pic") val profilePic: String? = null
)

@Serializable
data class DashboardAlert(
    val id: String,
    val title: String,
    val value: String,
    val type: String  // CRITICAL | INFO | WARNING
)

@Serializable
data class FeaturedSchool(
    val id: String,
    val name: String,
    val rating: Double,
    val location: String,
    val image: String? = null
)

@Serializable
data class DashboardResponse(
    val greeting: String,
    // RA-31: `child_summary` is kept as the FIRST active child for backward
    // compatibility; `children` carries ALL active children so a parent with
    // 2+ linked kids can switch between them. Clients should prefer `children`.
    @SerialName("child_summary") val childSummary: ChildSummary? = null,
    val children: List<ChildSummary> = emptyList(),
    val alerts: List<DashboardAlert>,
    @SerialName("featured_schools") val featuredSchools: List<FeaturedSchool>,
    @SerialName("curation_logic") val curationLogic: String
)

@Serializable
data class DiscoveredSchool(
    val id: String,
    val name: String,
    val rating: Double,
    val location: String,
    val image: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("distance_km") val distanceKm: Double? = null,
    // Public-profile fields the marketplace cards need (board badge, medium chip,
    // co-ed flag, full address on the profile screen). All real columns on
    // `schools` — nullable so old clients deserialise unchanged.
    val board: String? = null,
    val medium: String? = null,
    @SerialName("school_gender") val schoolGender: String? = null,
    val address: String? = null
)

@Serializable
data class SchoolDiscoveryResponse(
    val schools: List<DiscoveredSchool>,
    @SerialName("sorted_by") val sortedBy: String  // "distance" | "city" | "name"
)

/** Great-circle distance (km) between two lat/lng points (Haversine). */
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0 // Earth radius km
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
        Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

private fun timeOfDayGreeting(): String {
    val hour = LocalTime.now().hour
    return when {
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        else      -> "Good Evening"
    }
}

private fun firstName(full: String?): String = full?.trim()?.split(" ")?.firstOrNull().orEmpty()

private fun formatMoney(amount: Double, currency: String): String {
    // RA-25: India-first product — default to ₹ (INR) for unknown codes.
    val symbol = when (currency.uppercase()) {
        "USD" -> "$"
        "INR" -> "₹"
        "EUR" -> "€"
        "GBP" -> "£"
        else  -> "₹"
    }
    val rounded = amount.toLong()
    val withCommas = "%,d".format(rounded)
    return "$symbol$withCommas"
}

fun Route.parentDashboardRouting() {
    authenticate("jwt") {
        route("/api/v1/parent") {
            get("/dashboard") {
                val uid = call.principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val nameClaim = call.principalName()

                val payload = dbQuery {
                    val user = AppUsersTable.selectAll()
                        .where { AppUsersTable.id eq uid }
                        .singleOrNull()
                    val displayFirstName = firstName(nameClaim ?: user?.get(AppUsersTable.fullName))
                    val greeting = if (displayFirstName.isBlank()) timeOfDayGreeting()
                                   else "${timeOfDayGreeting()}, $displayFirstName"

                    // ----- children (RA-31: ALL active children, oldest first) -----
                    val children = ChildrenTable.selectAll()
                        .where { (ChildrenTable.parentId eq uid) and (ChildrenTable.isActive eq true) }
                        .orderBy(ChildrenTable.createdAt, SortOrder.ASC)
                        .map {
                            ChildSummary(
                                id = it[ChildrenTable.id].value.toString(),
                                name = it[ChildrenTable.childName],
                                overallProgress = it[ChildrenTable.overallProgress],
                                currentLevel = it[ChildrenTable.currentLevel],
                                attendanceStatus = it[ChildrenTable.attendanceStatus],
                                profilePic = it[ChildrenTable.profilePic]
                            )
                        }
                    // First child mirrored into child_summary for backward compatibility.
                    val childSummary = children.firstOrNull()

                    // ----- alerts: overdue fees -----
                    val alerts = mutableListOf<DashboardAlert>()
                    val overdueRows = FeeRecordsTable.selectAll()
                        .where { (FeeRecordsTable.parentId eq uid) and (FeeRecordsTable.status eq "OVERDUE") }
                        .toList()
                    if (overdueRows.isNotEmpty()) {
                        val totalOverdue = overdueRows.sumOf { it[FeeRecordsTable.amount] }
                        val currency = overdueRows.first()[FeeRecordsTable.currency]
                        alerts += DashboardAlert(
                            id = "fees_overdue",
                            title = "Fees Due",
                            value = formatMoney(totalOverdue, currency),
                            type = "CRITICAL"
                        )
                    }

                    // INFO alerts can be seeded in app_config under "parent_dashboard_info_alerts"
                    // (JSON array of {id,title,value,type}). Safe noop when absent.
                    val infoAlertsRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_dashboard_info_alerts" }
                        .singleOrNull()
                        ?.get(AppConfigTable.value)
                    if (!infoAlertsRaw.isNullOrBlank()) {
                        runCatching {
                            val parsed = kotlinx.serialization.json.Json.decodeFromString(
                                kotlinx.serialization.builtins.ListSerializer(DashboardAlert.serializer()),
                                infoAlertsRaw
                            )
                            alerts.addAll(parsed)
                        }
                    }

                    // ----- featured schools -----
                    val schools = SchoolsTable.selectAll()
                        .where { SchoolsTable.isActive eq true }
                        .limit(5)
                        .map {
                            FeaturedSchool(
                                id = it[SchoolsTable.id].value.toString(),
                                name = it[SchoolsTable.name],
                                rating = 4.5, // operational rating column is in :supplementary_schema; default for now
                                location = it[SchoolsTable.city],
                                image = it[SchoolsTable.logoUrl]
                            )
                        }

                    // ----- curation logic CMS string -----
                    val curationLogic = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_dashboard_curation_logic" }
                        .singleOrNull()
                        ?.get(AppConfigTable.value)
                        ?.trim('"')
                        ?: "Curation aligned with NEP 2020 developmental milestones."

                    DashboardResponse(
                        greeting = greeting,
                        childSummary = childSummary,
                        children = children,
                        alerts = alerts,
                        featuredSchools = schools,
                        curationLogic = curationLogic
                    )
                }

                call.ok(payload, message = "Dashboard fetched successfully")
            }

            // ----- school discovery by location -----
            // GET /api/v1/parent/schools/discover?lat=..&lng=..&radius_km=..&city=..&limit=..
            //
            // When lat/lng are supplied we compute Haversine distance to each
            // active school that has coordinates, optionally filter to a radius,
            // and sort nearest-first. Schools without coordinates are appended
            // after geo-located ones (or filtered out when a radius is given).
            // When lat/lng are absent we fall back to city match then name sort,
            // matching the previous "active schools by city" behaviour but
            // without the hard-coded 5-school cap.
            get("/schools/discover") {
                // Caller must at least be authenticated; any role can browse.
                call.principalUserId() ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }
                val q = call.request.queryParameters
                val lat = q["lat"]?.toDoubleOrNull()
                val lng = q["lng"]?.toDoubleOrNull()
                val radiusKm = q["radius_km"]?.toDoubleOrNull()
                val cityFilter = q["city"]?.takeIf { it.isNotBlank() }
                val limit = (q["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 100)

                val (schools, sortedBy) = dbQuery {
                    val rows = SchoolsTable.selectAll()
                        .where { SchoolsTable.isActive eq true }
                        .toList()

                    val mapped = rows.map { row ->
                        val sLat = row[SchoolsTable.latitude]
                        val sLng = row[SchoolsTable.longitude]
                        val dist = if (lat != null && lng != null && sLat != null && sLng != null)
                            haversineKm(lat, lng, sLat, sLng) else null
                        DiscoveredSchool(
                            id = row[SchoolsTable.id].value.toString(),
                            name = row[SchoolsTable.name],
                            rating = 4.5, // operational rating column lives in supplementary schema
                            location = row[SchoolsTable.city],
                            image = row[SchoolsTable.logoUrl],
                            latitude = sLat,
                            longitude = sLng,
                            distanceKm = dist?.let { kotlin.math.round(it * 100) / 100.0 },
                            board = row[SchoolsTable.board].takeIf { it.isNotBlank() },
                            medium = row[SchoolsTable.medium].takeIf { it.isNotBlank() },
                            schoolGender = row[SchoolsTable.schoolGender].takeIf { it.isNotBlank() },
                            address = row[SchoolsTable.fullAddress]
                        )
                    }

                    if (lat != null && lng != null) {
                        // Geo mode: keep those within radius (if given), nearest first.
                        val located = mapped.filter { it.distanceKm != null }
                        val withinRadius = if (radiusKm != null)
                            located.filter { it.distanceKm!! <= radiusKm } else located
                        val result = withinRadius.sortedBy { it.distanceKm }
                            .let { if (radiusKm == null) it + mapped.filter { s -> s.distanceKm == null } else it }
                            .take(limit)
                        result to "distance"
                    } else if (cityFilter != null) {
                        val result = mapped
                            .filter { it.location.equals(cityFilter, ignoreCase = true) }
                            .sortedBy { it.name }
                            .take(limit)
                        result to "city"
                    } else {
                        val result = mapped.sortedBy { it.name }.take(limit)
                        result to "name"
                    }
                }

                call.ok(
                    SchoolDiscoveryResponse(schools = schools, sortedBy = sortedBy),
                    message = "Discovered ${schools.size} school(s)"
                )
            }
        }
    }
}
