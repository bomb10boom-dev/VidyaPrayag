package com.littlebridge.enrollplus.feature.auth.data.remote

import com.littlebridge.enrollplus.core.model.ApiResponse
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.safeApiCall
import com.littlebridge.enrollplus.feature.auth.domain.model.*
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*

class AuthApi(
    private val client: HttpClient,
    private val baseUrl: String
) {
    private fun getUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val cleanPath = if (path.startsWith("/")) path.substring(1) else path
        return "$base$cleanPath"
    }

    suspend fun checkUser(identifier: String): NetworkResult<ApiResponse<UserFlowResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/check-user")) {
                contentType(ContentType.Application.Json)
                setBody(CheckUserRequest(identifier))
            }
        }
    }

    suspend fun signup(request: SignupRequest): NetworkResult<ApiResponse<AuthResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/signup")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    suspend fun registerSchool(request: SchoolRegisterRequest): NetworkResult<ApiResponse<AuthResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/register-school")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    suspend fun login(request: LoginRequest): NetworkResult<ApiResponse<AuthResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/login")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    suspend fun sendOtp(identifier: String, purpose: String? = null): NetworkResult<ApiResponse<OtpResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/send-otp")) {
                contentType(ContentType.Application.Json)
                setBody(OtpRequest(identifier, purpose))
            }
        }
    }

    suspend fun verifyOtp(identifier: String, code: String, purpose: String? = null): NetworkResult<ApiResponse<OtpResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/verify-otp")) {
                contentType(ContentType.Application.Json)
                setBody(mapOf("identifier" to identifier, "code" to code, "purpose" to purpose))
            }
        }
    }

    suspend fun getUserDetails(token: String): NetworkResult<UserDetailsResponse> {
        return safeApiCall {
            client.get(getUrl("api/v1/user/details"))
        }
    }

    /**
     * Exchange a refresh token for a fresh access token (audit §3.4, finding F).
     * The server's /refresh route was previously unreachable because no client
     * method called it.
     */
    suspend fun refresh(refreshToken: String): NetworkResult<ApiResponse<AuthResponse>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/refresh")) {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }
        }
    }

    /**
     * RA-54: change the authenticated user's password. The bearer token is
     * attached automatically by the Ktor Auth plugin. On success the server
     * flips profile_completed=true and must_change_password=false, resolving
     * the teacher first-login gate permanently.
     */
    suspend fun changePassword(request: ChangePasswordRequest): NetworkResult<ApiResponse<Unit>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/change-password")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }
    }

    /** Revoke the current session server-side (audit §3.6). */
    suspend fun logout(token: String, refreshToken: String?): NetworkResult<ApiResponse<Unit>> {
        return safeApiCall {
            client.post(getUrl("api/v1/auth/logout")) {
                contentType(ContentType.Application.Json)
                setBody(LogoutRequest(refreshToken))
            }
        }
    }
}
