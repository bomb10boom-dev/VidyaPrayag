/*
 * File: VersionRouting.kt
 * Module: feature.config
 * Endpoints implemented:
 *   GET /api/v1/config/version     (public — no JWT required)
 *
 * Purpose:
 *   Backend-target visibility (SCHOOL_SIDE_STATUS_REPORT §8.4).
 *
 *   When a real phone is connected over WiFi to a laptop backend, it must be
 *   trivial to PROVE which backend is actually answering. This endpoint returns
 *   the git SHA, build time and version baked into the JAR at build time (see
 *   server/build.gradle.kts -D system properties). It also echoes the server's
 *   resolved host/port so a screenshot can confirm laptop-vs-Render at a glance.
 *
 *   Use it like:
 *     curl http://<laptop-LAN-ip>:8080/api/v1/config/version
 *   from the phone browser. If `git_sha` matches the laptop's HEAD, the phone
 *   is hitting the laptop. If it shows the old Render SHA (or "unknown"), the
 *   app's devBaseUrl is still pointing at Render.
 */
package com.littlebridge.enrollplus.feature.config

import com.littlebridge.enrollplus.core.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackendVersionResponse(
    @SerialName("git_sha") val gitSha: String,
    @SerialName("build_time") val buildTime: String,
    val version: String,
    @SerialName("server_time") val serverTime: String,
    @SerialName("requested_host") val requestedHost: String
)

fun Route.versionRouting() {
    route("/api/v1/config") {
        get("/version") {
            val gitSha = System.getProperty("vidyaprayag.git.sha") ?: "unknown"
            val buildTime = System.getProperty("vidyaprayag.build.time") ?: "unknown"
            val version = System.getProperty("vidyaprayag.version") ?: "unknown"
            // The Host header tells us exactly what address the phone dialled,
            // which is the single most useful fact for laptop-vs-Render debugging.
            val host = call.request.headers["Host"] ?: "unknown"
            call.ok(
                BackendVersionResponse(
                    gitSha = gitSha,
                    buildTime = buildTime,
                    version = version,
                    serverTime = java.time.Instant.now().toString(),
                    requestedHost = host
                ),
                message = "Backend identity"
            )
        }
    }
}
