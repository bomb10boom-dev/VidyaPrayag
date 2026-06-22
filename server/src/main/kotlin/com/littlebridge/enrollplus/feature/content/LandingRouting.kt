/*
 * File: LandingRouting.kt
 * Module: feature.content
 * Endpoints implemented:
 *   GET /api/v1/content/landing      (public — no JWT required)
 *
 * Spec ref: vidya_prayag_api_spec.artifact.md §Screen: Common Landing Page
 *
 * Data source: cms_landing_content (LandingContentTable) — a key/value
 *              store seeded by Seed.kt on first boot. Each "object" field
 *              (parent_info, school_info, list_of_offerings, list_of_portals)
 *              is stored as raw JSON in `value` and parsed back into a typed
 *              DTO before responding.
 *
 * Caching: response is cheap to compute (one full table scan, <20 rows) so we
 *          serve fresh data on every request. A future Redis cache layer can
 *          wrap this — see spec "Caching Strategy: Redis (TTL 24 Hours)".
 *
 * Used by mobile UI: composeApp/.../presentation/landing/CommonLandingScreen.kt
 */
package com.littlebridge.enrollplus.feature.content

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import com.littlebridge.enrollplus.db.LandingContentTable
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import org.jetbrains.exposed.sql.selectAll

@Serializable
data class LandingSection(
    @SerialName("top_tagline") val topTagline: String,
    @SerialName("sub_tagline") val subTagline: String,
    @SerialName("list_of_features") val listOfFeatures: List<String>,
    @SerialName("list_of_sub_features") val listOfSubFeatures: List<String>
)

@Serializable
data class LandingItem(
    @SerialName("icon_url") val iconUrl: String,
    val heading: String,
    val description: String,
    @SerialName("is_live") val isLive: Boolean
)

@Serializable
data class LandingResponse(
    @SerialName("top_tagline") val topTagline: String,
    @SerialName("sub_tagline") val subTagline: String,
    @SerialName("parent_info") val parentInfo: LandingSection,
    @SerialName("school_info") val schoolInfo: LandingSection,
    @SerialName("list_of_offerings") val listOfOfferings: List<LandingItem>,
    @SerialName("list_of_portals") val listOfPortals: List<LandingItem>,
    @SerialName("login_modes") val loginModes: List<String>,
    @SerialName("tos_link") val tosLink: String,
    @SerialName("privacy_policy_link") val privacyPolicyLink: String
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Route.landingRouting() {
    route("/api/v1/content") {
        get("/landing") {
            try {
                val kv: Map<String, String> = dbQuery {
                    LandingContentTable.selectAll().associate {
                        it[LandingContentTable.key] to it[LandingContentTable.value]
                    }
                }

                val loginModes: List<String> = kv["login_modes"]?.let {
                    lenientJson.parseToJsonElement(it).jsonArray.map { e -> e.toString().trim('"') }
                } ?: listOf("EMAIL", "MOBILE")

                val response = LandingResponse(
                    topTagline = kv["top_tagline"]?.trim('"') ?: "Education with Trust.",
                    subTagline = kv["sub_tagline"]?.trim('"') ?: "Progress with Purpose.",
                    parentInfo = kv["parent_info"]?.let {
                        lenientJson.decodeFromString(LandingSection.serializer(), it)
                    } ?: LandingSection("FOR PARENTS", "Find schools", emptyList(), emptyList()),
                    schoolInfo = kv["school_info"]?.let {
                        lenientJson.decodeFromString(LandingSection.serializer(), it)
                    } ?: LandingSection("FOR SCHOOLS", "Scale excellence", emptyList(), emptyList()),
                    listOfOfferings = decodeList(kv["list_of_offerings"]),
                    listOfPortals = decodeList(kv["list_of_portals"]),
                    loginModes = loginModes,
                    tosLink = kv["tos_link"]?.trim('"') ?: "https://vidyaprayag.com/terms",
                    privacyPolicyLink = kv["privacy_policy_link"]?.trim('"') ?: "https://vidyaprayag.com/privacy"
                )
                call.ok(response, message = "Landing page content fetched successfully")
            } catch (e: Exception) {
                System.err.println("API_ERROR: Failed to fetch landing content!")
                e.printStackTrace()
                throw e // Let the global exception handler return 500
            }
        }
    }
}

private fun decodeList(raw: String?): List<LandingItem> {
    if (raw.isNullOrBlank()) return emptyList()
    return lenientJson.decodeFromString(
        kotlinx.serialization.builtins.ListSerializer(LandingItem.serializer()),
        raw
    )
}
