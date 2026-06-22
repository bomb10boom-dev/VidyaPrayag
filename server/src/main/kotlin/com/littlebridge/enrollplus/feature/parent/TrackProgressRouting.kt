/*
 * File: TrackProgressRouting.kt
 * Module: feature.parent
 *
 * Endpoint: GET /api/v1/parent/track-progress  (JWT)
 *
 * Spec ref: parent_api_spec.artifact.md §Screen: Track Progress (Holistic Growth)
 *
 * Drives the parent-facing "Holistic Growth" view (hero stats, badges,
 * NEP-aligned academic competencies, emotional intelligence radar,
 * play & discovery cards).
 *
 * Data sources:
 *   - children            : current_level + overall_progress for the hero band.
 *   - app_config (CMS)    : the visual scaffold (badges, competencies, EI metrics,
 *                           play & discovery cards, journey description). The
 *                           per-child progress numbers are injected into the
 *                           CMS template at response time.
 *
 * Why CMS-driven?  Per spec: "All UI strings, statistics, and configurations
 * are backend-driven." Operators tweak badge titles, NEP labels, EI metric
 * descriptions, etc., from Supabase without redeploying.
 */
package com.littlebridge.enrollplus.feature.parent

import com.littlebridge.enrollplus.core.fail
import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.core.principalUserId
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.ChildrenTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Serializable
data class HeroSection(
    @SerialName("progress_percentage") val progressPercentage: Int,
    @SerialName("level_label") val levelLabel: String,
    @SerialName("journey_description") val journeyDescription: String
)

@Serializable
data class Badge(
    val title: String,
    val icon: String,
    @SerialName("is_locked") val isLocked: Boolean,
    val colors: List<String>
)

@Serializable
data class Competency(
    val title: String,
    val progress: Double,
    val icon: String
)

@Serializable
data class AcademicCore(
    val label: String,
    val competencies: List<Competency>
)

@Serializable
data class EmotionalIntelligence(
    val description: String,
    val metrics: Map<String, Double>
)

@Serializable
data class PlayDiscoveryItem(
    val title: String,
    val description: String,
    val image: String? = null,
    val status: String  // MET | IN_PROGRESS | LOCKED
)

@Serializable
data class TrackProgressResponse(
    @SerialName("hero_section") val heroSection: HeroSection,
    val badges: List<Badge>,
    @SerialName("academic_core") val academicCore: AcademicCore,
    @SerialName("emotional_intelligence") val emotionalIntelligence: EmotionalIntelligence,
    @SerialName("play_discovery") val playDiscovery: List<PlayDiscoveryItem>,
    @SerialName("last_updated") val lastUpdated: String
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

private val DEFAULT_BADGES = listOf(
    Badge("Social Star", "workspace_premium", false, listOf("#B6C7EB", "#006C49"))
)
private val DEFAULT_COMPETENCIES = listOf(
    Competency("Literacy", 0.85, "translate"),
    Competency("Numeracy", 0.78, "calculate"),
    Competency("Creativity", 0.72, "palette")
)
private val DEFAULT_EI_METRICS = mapOf(
    "Empathy"    to 0.8,
    "Resilience" to 0.7,
    "Social"     to 0.9
)
private val DEFAULT_PLAY_DISCOVERY = listOf(
    PlayDiscoveryItem("Agility", "Gross motor met", null, "MET"),
    PlayDiscoveryItem("Curiosity", "Exploration in progress", null, "IN_PROGRESS")
)

fun Route.trackProgressRouting() {
    authenticate("jwt") {
        route("/api/v1/parent") {
            get("/track-progress") {
                val uid = call.principalUserId()?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: run {
                    call.fail("Invalid token", HttpStatusCode.Unauthorized); return@get
                }

                val payload = dbQuery {
                    val child = ChildrenTable.selectAll()
                        .where { (ChildrenTable.parentId eq uid) and (ChildrenTable.isActive eq true) }
                        .orderBy(ChildrenTable.createdAt, SortOrder.ASC)
                        .firstOrNull()

                    val progressPct = ((child?.get(ChildrenTable.overallProgress) ?: 0.0) * 100).toInt()
                    val level = child?.get(ChildrenTable.currentLevel) ?: 1
                    val grade = child?.get(ChildrenTable.currentGrade)

                    val journeyTemplate = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_journey_description" }
                        .singleOrNull()
                        ?.get(AppConfigTable.value)
                        ?.trim('"')
                        ?: "On track for next grade transition"

                    val journey = if (grade != null) "On track for $grade transition" else journeyTemplate

                    val hero = HeroSection(
                        progressPercentage = progressPct,
                        levelLabel = "LEVEL $level REACHED",
                        journeyDescription = journey
                    )

                    val badgesRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_badges" }
                        .singleOrNull()?.get(AppConfigTable.value)
                    val badges: List<Badge> = badgesRaw?.let {
                        runCatching {
                            lenientJson.decodeFromString(ListSerializer(Badge.serializer()), it)
                        }.getOrNull()
                    } ?: DEFAULT_BADGES

                    val competenciesRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_academic_competencies" }
                        .singleOrNull()?.get(AppConfigTable.value)
                    val competencies: List<Competency> = competenciesRaw?.let {
                        runCatching {
                            lenientJson.decodeFromString(ListSerializer(Competency.serializer()), it)
                        }.getOrNull()
                    } ?: DEFAULT_COMPETENCIES
                    val academicLabel = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_academic_label" }
                        .singleOrNull()?.get(AppConfigTable.value)?.trim('"') ?: "NEP ALIGNED"

                    val eiDesc = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_ei_description" }
                        .singleOrNull()?.get(AppConfigTable.value)?.trim('"')
                        ?: "Significant growth in Social Interaction this month."
                    val eiMetricsRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_ei_metrics" }
                        .singleOrNull()?.get(AppConfigTable.value)
                    val eiMetrics: Map<String, Double> = eiMetricsRaw?.let {
                        runCatching {
                            lenientJson.decodeFromString(
                                MapSerializer(String.serializer(), Double.serializer()), it
                            )
                        }.getOrNull()
                    } ?: DEFAULT_EI_METRICS

                    val playRaw = AppConfigTable.selectAll()
                        .where { AppConfigTable.key eq "parent_track_play_discovery" }
                        .singleOrNull()?.get(AppConfigTable.value)
                    val play: List<PlayDiscoveryItem> = playRaw?.let {
                        runCatching {
                            lenientJson.decodeFromString(
                                ListSerializer(PlayDiscoveryItem.serializer()), it
                            )
                        }.getOrNull()
                    } ?: DEFAULT_PLAY_DISCOVERY

                    val lastUpdated = ZonedDateTime.now(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("'Today,' h:mm a", Locale.ENGLISH))

                    TrackProgressResponse(
                        heroSection = hero,
                        badges = badges,
                        academicCore = AcademicCore(label = academicLabel, competencies = competencies),
                        emotionalIntelligence = EmotionalIntelligence(description = eiDesc, metrics = eiMetrics),
                        playDiscovery = play,
                        lastUpdated = lastUpdated
                    )
                }

                call.ok(payload, message = "Holistic progress fetched successfully")
            }
        }
    }
}
