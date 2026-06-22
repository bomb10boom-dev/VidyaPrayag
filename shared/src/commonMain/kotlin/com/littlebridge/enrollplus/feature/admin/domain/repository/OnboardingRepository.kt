package com.littlebridge.enrollplus.feature.admin.domain.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.domain.model.ClassDetailsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingCompletionResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStatusResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStepResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitResponse

/**
 * Domain-layer abstraction over the school onboarding endpoints.
 *
 * Implementations are responsible for talking to the network layer
 * (typically via [com.littlebridge.enrollplus.feature.admin.data.remote.OnboardingApi])
 * and unwrapping the server's ApiResponse envelope so callers
 * (ViewModels) only deal with the inner data types.
 */
interface OnboardingRepository {

    /**
     * Fetches the saved state / form schema for a single onboarding step.
     *
     * @param token JWT bearer token (no "Bearer " prefix; the API adds it)
     * @param obStepType One of: "BASIC", "BRANDING", "ACADEMIC", "REVIEW"
     */
    suspend fun getStep(
        token: String,
        obStepType: String
    ): NetworkResult<OnboardingStepResponse>

    /**
     * Persists a step's data (and optionally marks onboarding as complete).
     */
    suspend fun submitStep(
        token: String,
        request: OnboardingSubmitRequest
    ): NetworkResult<OnboardingSubmitResponse>

    /**
     * Fetches the subject / teacher details for a single class in the ACADEMIC step.
     *
     * @param classId The class code (e.g. "8", "NURSERY") as returned by [getStep] for ACADEMIC.
     */
    suspend fun getClassDetails(
        token: String,
        classId: String
    ): NetworkResult<ClassDetailsResponse>

    /**
     * Server-truth onboarding status (derived from REAL data, not a local flag).
     * Used by the post-login gate to decide dashboard vs onboarding and to
     * resume a returning admin at the first incomplete step.
     */
    suspend fun getStatus(token: String): NetworkResult<OnboardingStatusResponse>

    /** Idempotently finalizes onboarding (status → active, profile_completed=true). */
    suspend fun completeOnboarding(token: String): NetworkResult<OnboardingCompletionResponse>
}
