/*
 * File: AppStatusRouting.kt
 * Module: feature.config
 * Endpoints implemented:
 *   GET /api/v1/config/app-status      (public — no JWT required)
 *
 * Spec ref: vidya_prayag_api_spec.artifact.md §Screen: Splash / Startup (Global)
 *
 * Purpose:
 *   The "handshake" API the mobile app hits on every cold boot. Provides:
 *     - version_check.{current,minimum_required,force_update,update_url,update_message}
 *     - maintenance.{is_under_maintenance,estimated_end_time,message}
 *     - flags.{is_whatsapp_sync_enabled, show_scholarships, …}
 *
 * Data source: app_config (AppConfigTable). Same JSON-in-KV pattern as
 *              LandingRouting — values are stored under three keys
 *              (version_check, maintenance, flags).
 *
 * Header requirements per spec (Content-Type, App-Version, Platform, Device-Id)
 * are NOT validated server-side yet because the mobile clients are not all
 * sending them in dev builds. Treat as "TODO: enforce in production".
 */
package com.littlebridge.enrollplus.feature.config

import com.littlebridge.enrollplus.core.ok
import com.littlebridge.enrollplus.db.AppConfigTable
import com.littlebridge.enrollplus.db.DatabaseFactory.dbQuery
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.sql.selectAll

@Serializable
data class AppStatusResponse(
    @SerialName("version_check") val versionCheck: JsonElement,
    val maintenance: JsonElement,
    val flags: JsonElement
)

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun Route.appStatusRouting() {
    route("/api/v1/config") {
        get("/app-status") {
            val kv: Map<String, String> = dbQuery {
                AppConfigTable.selectAll().associate {
                    it[AppConfigTable.key] to it[AppConfigTable.value]
                }
            }
            val response = AppStatusResponse(
                versionCheck = lenientJson.parseToJsonElement(kv["version_check"] ?: "{}"),
                maintenance  = lenientJson.parseToJsonElement(kv["maintenance"] ?: "{}"),
                flags        = lenientJson.parseToJsonElement(kv["flags"] ?: "{}")
            )
            call.ok(response, message = "App status fetched")
        }
    }
}
