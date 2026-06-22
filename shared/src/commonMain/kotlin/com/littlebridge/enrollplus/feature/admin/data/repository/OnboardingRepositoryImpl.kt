package com.littlebridge.enrollplus.feature.admin.data.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.admin.data.remote.OnboardingApi
import com.littlebridge.enrollplus.feature.admin.domain.model.ClassDetailsResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingCompletionResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStatusResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingStepResponse
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitRequest
import com.littlebridge.enrollplus.feature.admin.domain.model.OnboardingSubmitResponse
import com.littlebridge.enrollplus.feature.admin.domain.repository.OnboardingRepository

/**
 * Default [OnboardingRepository] implementation.
 *
 * Mirrors the pattern used by AuthRepositoryImpl: takes the ApiResponse envelope
 * returned by the server and unwraps `.data`, surfacing a clean error if the
 * server returned `success=false` (or the data field was missing).
 */
class OnboardingRepositoryImpl(
    private val api: OnboardingApi
) : OnboardingRepository {

    override suspend fun getStep(
        token: String,
        obStepType: String
    ): NetworkResult<OnboardingStepResponse> {
        return when (val result = api.getStep(token, obStepType)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch onboarding step" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun submitStep(
        token: String,
        request: OnboardingSubmitRequest
    ): NetworkResult<OnboardingSubmitResponse> {
        return when (val result = api.submitStep(token, request)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to submit onboarding step" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun getClassDetails(
        token: String,
        classId: String
    ): NetworkResult<ClassDetailsResponse> {
        return when (val result = api.getClassDetails(token, classId)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch class details" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun getStatus(
        token: String
    ): NetworkResult<OnboardingStatusResponse> {
        return when (val result = api.getStatus(token)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to fetch onboarding status" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun completeOnboarding(
        token: String
    ): NetworkResult<OnboardingCompletionResponse> {
        return when (val result = api.completeOnboarding(token)) {
            is NetworkResult.Success -> {
                val envelope = result.data
                val data = envelope.data
                when {
                    !envelope.success -> NetworkResult.Error(
                        envelope.message.ifBlank { "Failed to complete onboarding" }
                    )
                    data == null -> NetworkResult.Error("No data in response")
                    else -> NetworkResult.Success(data)
                }
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }
}
