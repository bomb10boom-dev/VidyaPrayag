package com.littlebridge.enrollplus.feature.auth.domain.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.auth.domain.model.*

interface AuthRepository {
    suspend fun checkUser(identifier: String): NetworkResult<AuthFlow>
    suspend fun signup(request: SignupRequest): NetworkResult<AuthResponse>
    /** "Onboard your school" — mints a school_admin + a pending school in one call. */
    suspend fun registerSchool(request: SchoolRegisterRequest): NetworkResult<AuthResponse>
    suspend fun login(request: LoginRequest): NetworkResult<AuthResponse>
    suspend fun sendOtp(identifier: String, purpose: String? = null): NetworkResult<String>
    suspend fun verifyOtp(identifier: String, code: String, purpose: String? = null): NetworkResult<Boolean>
    suspend fun saveSession(response: AuthResponse)
    suspend fun getSession(): AuthResponse?
    suspend fun refresh(): NetworkResult<AuthResponse>
    /** RA-54: change password; on success persists profileCompleted=true locally. */
    suspend fun changePassword(oldPassword: String?, newPassword: String): NetworkResult<Unit>
    suspend fun logout()
    suspend fun getUserDetails(token: String): NetworkResult<UserDetailsResponse>
}
