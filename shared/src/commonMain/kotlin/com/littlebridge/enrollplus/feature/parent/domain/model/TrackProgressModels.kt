package com.littlebridge.enrollplus.feature.parent.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// ─────────────────────────────────────────────────────────────────────────────
// RA-PP-FIX: the client models here now mirror the REAL server contract emitted by
// server/.../feature/parent/TrackProgressRouting.kt → TrackProgressResponse,
// field-for-field. The previous models were written against an imagined shape
// (flat child_name/overall_progress + `emotional_intelligence: Map<String,Float>`),
// which made kotlinx.serialization try to parse the EI `description` STRING as a
// Float and crash the Academics → Overview tab with:
//   "Failed to parse type 'float' … emotional_intelligence['description']".
//
// Real wire shape (envelope from call.ok):
//   { success, message, data: {
//       hero_section: { progress_percentage:Int, level_label:String, journey_description:String },
//       badges: [ { title, icon, is_locked, colors:[String] } ],
//       academic_core: { label:String, competencies:[ { title, progress:Double, icon } ] },
//       emotional_intelligence: { description:String, metrics:{ String->Double } },
//       play_discovery: [ { title, description, image?:String, status: MET|IN_PROGRESS|LOCKED } ],
//       last_updated: String
//   } }
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class TrackProgressResponse(
    val success: Boolean = true,
    val message: String = "",
    val data: TrackProgressData
)

@Serializable
data class TrackProgressData(
    @SerialName("hero_section") val heroSection: TrackHeroSection,
    val badges: List<AchievementBadgeData> = emptyList(),
    @SerialName("academic_core") val academicCore: AcademicCoreData = AcademicCoreData(),
    @SerialName("emotional_intelligence") val emotionalIntelligence: EmotionalIntelligenceData =
        EmotionalIntelligenceData(),
    @SerialName("play_discovery") val playDiscovery: List<PlayIndicatorData> = emptyList(),
    @SerialName("last_updated") val lastUpdated: String = ""
)

@Serializable
data class TrackHeroSection(
    @SerialName("progress_percentage") val progressPercentage: Int = 0,
    @SerialName("level_label") val levelLabel: String = "",
    @SerialName("journey_description") val journeyDescription: String = ""
)

@Serializable
data class AchievementBadgeData(
    val title: String = "",
    // Server emits `icon` (Material symbol name) — not `icon_name`.
    val icon: String = "",
    @SerialName("is_locked") val isLocked: Boolean = false,
    // Server emits `colors` as hex strings (e.g. ["#B6C7EB", "#006C49"]) — not Long ARGB.
    val colors: List<String> = emptyList()
)

@Serializable
data class AcademicCoreData(
    val label: String = "",
    val competencies: List<AcademicCompetencyData> = emptyList()
)

@Serializable
data class AcademicCompetencyData(
    val title: String = "",
    // Server emits a Double in 0..1; the icon key is `icon` (not `icon_name`).
    val progress: Double = 0.0,
    val icon: String = ""
)

@Serializable
data class EmotionalIntelligenceData(
    val description: String = "",
    val metrics: Map<String, Double> = emptyMap()
)

@Serializable
data class PlayIndicatorData(
    val title: String = "",
    val description: String = "",
    val image: String? = null,
    // MET | IN_PROGRESS | LOCKED
    val status: String = "IN_PROGRESS"
)
