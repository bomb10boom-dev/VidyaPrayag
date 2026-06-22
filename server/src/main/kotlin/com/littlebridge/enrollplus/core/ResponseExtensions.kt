/*
 * File: ResponseExtensions.kt
 * Module: core
 * Purpose:
 *   Tiny DSL on top of Ktor's `ApplicationCall.respond` that guarantees every
 *   handler emits the canonical { success, message, data } envelope without
 *   boilerplate.
 *
 * Provides:
 *   - call.ok(data, message, status)          → 200 success envelope
 *   - call.created(data, message)             → 201 Created envelope
 *   - call.accepted(data, message)            → 202 Accepted envelope (used by /sync-whatsapp)
 *   - call.fail(message, status, errorCode)   → error envelope w/ chosen HTTP code
 *
 * Used by: every *Routing.kt file under the feature package.
 */
package com.littlebridge.enrollplus.core

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.AttributeKey

/**
 * Marks a call whose response was EXPLICITLY produced by a route handler (via
 * [fail]). The StatusPages `status(NotFound)` catch-all in ErrorHandling.kt
 * checks this marker and steps aside, so a route's own 404 body (e.g.
 * "No student found with that roll…") is never rewritten into the misleading
 * "Endpoint not found: <uri>" envelope. Without this, EVERY call.fail(...,
 * NotFound) in the codebase was masked by the catch-all.
 */
val RouteHandledResponseKey = AttributeKey<Unit>("RouteHandledResponse")

suspend inline fun <reified T : Any> ApplicationCall.ok(
    data: T,
    message: String = "OK",
    status: HttpStatusCode = HttpStatusCode.OK
) = respond(status, ApiResponse(success = true, message = message, data = data))

suspend inline fun <reified T : Any> ApplicationCall.created(
    data: T,
    message: String = "Created"
) = respond(HttpStatusCode.Created, ApiResponse(success = true, message = message, data = data))

suspend inline fun <reified T : Any> ApplicationCall.accepted(
    data: T,
    message: String = "Accepted"
) = respond(HttpStatusCode.Accepted, ApiResponse(success = true, message = message, data = data))

suspend fun ApplicationCall.okMessage(
    message: String,
    status: HttpStatusCode = HttpStatusCode.OK
) = respond(status, ApiResponse<Unit>(success = true, message = message, data = null))

suspend fun ApplicationCall.fail(
    message: String,
    status: HttpStatusCode = HttpStatusCode.BadRequest,
    errorCode: String? = null
) {
    // Mark this call as route-handled BEFORE responding so the StatusPages
    // status(NotFound) catch-all does not clobber our explicit error body
    // with "Endpoint not found: <uri>" (see ErrorHandling.kt).
    attributes.put(RouteHandledResponseKey, Unit)
    respond(status, ApiError(success = false, message = message, errorCode = errorCode))
}
