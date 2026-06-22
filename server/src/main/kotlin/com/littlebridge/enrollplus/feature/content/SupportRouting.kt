/*
 * File: SupportRouting.kt
 * Module: feature.content
 *
 * Endpoint: GET /api/v1/content/support   (public — no JWT required)
 *
 * Spec ref: parent_api_spec.artifact.md §Module: Drawer & Background
 *                                       §Screen: Support & Legal
 *
 * Drives the contact information and support categories dynamically.
 * Backed by app_config["parent_support_config"] (CMS) — see ParentCmsSeed.
 */
package com.littlebridge.enrollplus.feature.content

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll

@Serializable
data class SupportConfigResponse(
    @SerialName("support_contact") val supportContact: String,
    val categories: List<String>,
    @SerialName("help_center_url") val helpCenterUrl: String
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Route.supportRouting() {
    route("/api/v1/content") {
        get("/support") {
            val raw = dbQuery {
                AppConfigTable.selectAll()
                    .where { AppConfigTable.key eq "parent_support_config" }
                    .singleOrNull()
                    ?.get(AppConfigTable.value)
            }

            val response = raw?.let {
                runCatching { lenientJson.decodeFromString(SupportConfigResponse.serializer(), it) }.getOrNull()
            } ?: SupportConfigResponse(
                supportContact = "+91-9876543210",
                categories = listOf("TECHNICAL", "ACADEMIC", "ADMISSIONS", "FEES"),
                helpCenterUrl = "https://vidyaprayag.com/help"
            )

            call.ok(response, message = "Support config fetched")
        }
    }
}
