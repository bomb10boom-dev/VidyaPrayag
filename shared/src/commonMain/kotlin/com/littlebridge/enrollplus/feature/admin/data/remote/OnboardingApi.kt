package com.littlebridge.enrollplus.feature.admin.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.admin.domain.model.ClassDetailsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingCompletionResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStatusResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStepResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Network client for the school onboarding endpoints.
 *
 * Server routes are defined in:
 *   server/.../feature/onboarding/OnboardingRouting.kt
 *
 *   GET  /api/v1/onboarding/step?obStepType={BASIC|BRANDING|ACADEMIC|REVIEW}
 *   GET  /api/v1/onboarding/academic/class-details?classId={code}
 *   POST /api/v1/onboarding/submit
 *
 * All requests require a Bearer token. We follow the same pattern as
 * [com.littlebridge.enrollplus.feature.parent.data.remote.ParentApi] - the
 * caller passes the token in and we attach it as an Authorization header.
 */
class OnboardingApi(
    private val client: HttpClient,
    private val baseUrl: String
) {

    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    /**
     * GET /api/v1/onboarding/step?obStepType=...
     */
    suspend fun getStep(
        token: String,
        obStepType: String
    ): NetworkResult<ApiResponse<OnboardingStepResponse>> {
        return safeApiCall {
            client.get(getUrl("api/v1/onboarding/step")) {
                parameter("obStepType", obStepType)
            }
        }
    }

    /**
     * POST /api/v1/onboarding/submit
     */
    suspend fun submitStep(
        token: String,
        request: OnboardingSubmitRequest
    ): NetworkResult<ApiResponse<OnboardingSubmitResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/onboarding/submit")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    /**
     * GET /api/v1/onboarding/academic/class-details?classId=...
     */
    suspend fun getClassDetails(
        token: String,
        classId: String
    ): NetworkResult<ApiResponse<ClassDetailsResponse>> {
        return safeApiCall {
            client.get(getUrl("api/v1/onboarding/academic/class-details")) {
                parameter("classId", classId)
            }
        }
    }

    /**
     * GET /api/v1/onboarding/status
     *
     * Server-truth onboarding completion derived from REAL persisted data
     * (school row, branding, classes, onboarded_at) — NOT a local flag. The
     * post-login gate reads this so a stale/manually-set profile_completed
     * cannot wrongly skip onboarding, and so a returning admin resumes at the
     * first incomplete step.
     */
    suspend fun getStatus(
        token: String
    ): NetworkResult<ApiResponse<OnboardingStatusResponse>> {
        return safeApiCall {
            client.get(getUrl("api/v1/onboarding/status"))
        }
    }

    /**
     * POST /api/v1/onboarding/complete
     *
     * Idempotently finalizes onboarding (ensures school, seeds default academics
     * if skipped, stamps onboarded_at + profile_completed=true + status=active).
     */
    suspend fun completeOnboarding(
        token: String
    ): NetworkResult<ApiResponse<OnboardingCompletionResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/onboarding/complete"))
        }
    }
}
