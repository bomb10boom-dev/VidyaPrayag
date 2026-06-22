/*
 * File: ApiResponse.kt
 * Module: core
 * Purpose:
 *   Common, spec-compliant JSON envelope used by every endpoint in the
 *   VidyaPrayag backend, of the shape:
 *     { "success": true|false, "message": "...", "data": { ... } }
 *
 * Why a single envelope?
 *   - Mobile clients (Android/iOS Compose Multiplatform) need a uniform shape
 *     so a generic `NetworkResult` mapper can extract `data` or `message`.
 *   - The two API spec artifacts (vidya_prayag_api_spec.artifact.md &
 *     vidya_prayag_api_spec2.artifact.md) consistently use this envelope.
 *
 * Used by: every *Routing.kt file via the `ok(...)` / `fail(...)` helpers in
 *          core/ResponseExtensions.kt
 *
 * Spec refs:
 *   - vidya_prayag_api_spec.artifact.md §Common Landing Page §Response
 *   - vidya_prayag_api_spec2.artifact.md §All endpoints (envelope examples)
 */
package com.littlebridge.enrollplus.core

import kotlinx.serialization.Serializable

/**
 * Generic API response wrapper. `T` is the per-endpoint `data` payload type.
 * Use `data = null` for write-only endpoints that only return a status message
 * (e.g. PUT /user/profile/philosophy).
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String = "",
    val data: T? = null
)

/**
 * Failure envelope when no payload is meaningful. Some endpoints in the spec
 * also attach an `error_code` (e.g. DEVICE_BLOCKED on /config/app-status).
 */
@Serializable
data class ApiError(
    val success: Boolean = false,
    val message: String,
    val errorCode: String? = null
)
