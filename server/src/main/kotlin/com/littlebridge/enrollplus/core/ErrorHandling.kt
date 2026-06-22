/*
 * File: ErrorHandling.kt
 * Module: core
 * Purpose:
 *   Installs Ktor's StatusPages so that any uncaught exception from a route
 *   becomes a well-formed { success:false, message } envelope instead of an
 *   HTML stack trace. Keeps mobile clients happy and matches the spec's
 *   error response shape.
 *
 * Maps:
 *   - BadRequestException (parent of ContentTransformationException in Ktor 3.x)
 *                                    -> 400 Bad Request ("Invalid request body")
 *   - IllegalArgumentException       -> 400 Bad Request (message preserved)
 *   - NotFoundException              -> 404 Not Found
 *   - Throwable (catch-all)          -> 500 Internal Server Error
 *
 * Used by:
 *   - Application.kt -> install(StatusPages) { configureErrorHandling() }
 */
package com.littlebridge.enrollplus.core

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import io.ktor.server.request.header
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

/**
 * RA-40: server errors go through SLF4J (not raw stderr/printStackTrace), so a
 * production deployment can route them to a real sink — a Sentry SLF4J appender,
 * JSON logs, log shipping, etc. — instead of unstructured stderr lines.
 */
private val errorLog = LoggerFactory.getLogger("VidyaPrayag.Errors")

/**
 * RA-40: production signal, identical to `JwtConfig.isProduction` /
 * `OtpService.isProduction` — a managed deploy (Render/Supabase) always sets
 * `DATABASE_URL`. Used to HARD-GATE the `DEBUG_ERRORS` leak so a stray
 * `DEBUG_ERRORS=true` on a prod dyno can never echo raw exception detail to
 * clients.
 */
private val isProduction: Boolean
    get() = System.getenv("DATABASE_URL")?.takeIf { it.isNotBlank() } != null

fun StatusPagesConfig.configureErrorHandling() {
    // BadRequestException is the parent of Ktor's ContentTransformationException,
    // so this also catches malformed JSON / missing fields during deserialization.
    exception<BadRequestException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            ApiError(message = "Invalid request: ${cause.message ?: "malformed body"}")
        )
    }
    exception<IllegalArgumentException> { call, cause ->
        call.respond(
            HttpStatusCode.BadRequest,
            ApiError(message = cause.message ?: "Bad request")
        )
    }
    exception<NotFoundException> { call, cause ->
        call.respond(
            HttpStatusCode.NotFound,
            ApiError(message = cause.message ?: "Resource not found")
        )
    }
    exception<Throwable> { call, cause ->
        // RA-40: structured logging through SLF4J. Passing `cause` as the last
        // arg logs the full stack trace via the configured appender (logback.xml)
        // and is the hook point a Sentry SLF4J appender attaches to — replaces the
        // previous raw System.err.println + cause.printStackTrace().
        errorLog.error("Unhandled error on {}", call.request.uri, cause)

        // RA-40: DEBUG_ERRORS echoes raw exception detail to the client. It is
        // opt-in (default off) AND hard-gated to non-production: even if
        // DEBUG_ERRORS=true is set on a prod dyno, the leak is suppressed whenever
        // DATABASE_URL is configured (same prod signal as JwtConfig/OtpService).
        // NEVER set DEBUG_ERRORS=true in production.
        val showFullError = !isProduction && System.getenv("DEBUG_ERRORS") == "true"
        val message = if (showFullError) {
            "DEBUG_ERROR: ${cause.message ?: cause.toString()}"
        } else {
            "Something went wrong. Please try again later."
        }

        call.respond(
            HttpStatusCode.InternalServerError,
            ApiError(message = message)
        )
    }

    // Catch-all 404 for paths that didn't match any defined route.
    //
    // Special case: in Ktor 3, routes wrapped in `authenticate("jwt") { ... }`
    // do NOT match unauthenticated requests — they fall through here. To avoid
    // a misleading "endpoint not found" when the real cause is a missing Bearer
    // token, sniff the Authorization header on `/api/v1/...` paths and emit a
    // 401 with an actionable message instead.
    //
    // CRITICAL FIX (link-child "Endpoint not found" bug): StatusPages' status()
    // hook fires for EVERY 404 response — including ones a route handler sent
    // deliberately via call.fail("No student found…", NotFound). That clobbered
    // every legitimate 404 body in the API with "Endpoint not found: <uri>",
    // which is exactly what parents saw on POST /api/v1/parent/link-child even
    // though the route exists and ran. Route handlers now stamp
    // [RouteHandledResponseKey] inside call.fail(); when the marker is present
    // we step aside and let the handler's own envelope through untouched.
    status(HttpStatusCode.NotFound) { call, _ ->
        if (call.attributes.contains(RouteHandledResponseKey)) return@status
        val uri = call.request.uri
        val hasBearer = call.request.header(HttpHeaders.Authorization)?.startsWith("Bearer ") == true
        if (uri.startsWith("/api/v1/") && !hasBearer) {
            call.respond(
                HttpStatusCode.Unauthorized,
                ApiError(
                    message = "Missing or invalid Authorization header. " +
                              "Send 'Authorization: Bearer <jwt>' (login first to get one). " +
                              "If the endpoint really is public, double-check the path: $uri",
                    errorCode = "UNAUTHORIZED"
                )
            )
        } else {
            call.respond(
                HttpStatusCode.NotFound,
                ApiError(message = "Endpoint not found: $uri")
            )
        }
    }
}
