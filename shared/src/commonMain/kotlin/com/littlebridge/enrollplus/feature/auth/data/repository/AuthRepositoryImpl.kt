package com.littlebridge.enrollplus.feature.auth.data.repository

import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.network.SessionManager
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.state.SelectedChildHolder
import com.littlebridge.enrollplus.feature.auth.data.remote.AuthApi
import com.littlebridge.enrollplus.feature.auth.domain.model.*
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first

class AuthRepositoryImpl(
    private val api: AuthApi,
    private val preferenceRepository: PreferenceRepository,
    // RA-S01: needed to evict the Ktor Auth plugin's cached bearer token on logout.
    private val sessionManager: SessionManager,
    // RA-S05: reset the shared selected-child on logout so a re-login as a
    // different parent does not inherit the previous parent's child selection.
    private val selectedChildHolder: SelectedChildHolder,
) : AuthRepository {
    // RA-29: there is NO in-memory session cache. `prefs` is the single source
    // of truth — the same store the Ktor `Auth` plugin's `refreshTokens` writes
    // the rotated access/refresh tokens into. A previous `cachedSession` field
    // was written once on login but never updated on refresh, so `getSession()`
    // could hand back a stale (pre-rotation) token until the next cold start.
    // Reading prefs every time keeps the repository and the plugin in lock-step.

    override suspend fun checkUser(identifier: String): NetworkResult<AuthFlow> {
        return when (val result = api.checkUser(identifier)) {
            is NetworkResult.Success -> {
                val data = result.data.data ?: return NetworkResult.Error("No data in response")
                val isEmail = identifier.contains("@")
                val flow = when {
                    isEmail && data.isNewUser -> AuthFlow.SIGNUP_EMAIL
                    isEmail && !data.isNewUser -> AuthFlow.LOGIN_EMAIL
                    !isEmail && data.isNewUser -> AuthFlow.SIGNUP_PHONE
                    else -> AuthFlow.LOGIN_PHONE
                }
                NetworkResult.Success(flow)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun signup(request: SignupRequest): NetworkResult<AuthResponse> {
        return when (val result = api.signup(request)) {
            is NetworkResult.Success -> {
                val data = result.data.data ?: return NetworkResult.Error("No data in response")
                saveSession(data)
                NetworkResult.Success(data)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun registerSchool(request: SchoolRegisterRequest): NetworkResult<AuthResponse> {
        return when (val result = api.registerSchool(request)) {
            is NetworkResult.Success -> {
                val data = result.data.data ?: return NetworkResult.Error("No data in response")
                // Persists profileCompleted=false → the post-auth gate routes the
                // new admin into the onboarding wizard.
                saveSession(data)
                NetworkResult.Success(data)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun login(request: LoginRequest): NetworkResult<AuthResponse> {
        return when (val result = api.login(request)) {
            is NetworkResult.Success -> {
                val data = result.data.data ?: return NetworkResult.Error("No data in response")
                saveSession(data)
                NetworkResult.Success(data)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun sendOtp(identifier: String, purpose: String?): NetworkResult<String> {
        return when (val result = api.sendOtp(identifier, purpose)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.data?.message ?: result.data.message)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun verifyOtp(identifier: String, code: String, purpose: String?): NetworkResult<Boolean> {
        return when (val result = api.verifyOtp(identifier, code, purpose)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data.success)
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun saveSession(response: AuthResponse) {
        // RA-69: re-order writes so profileCompleted is in place BEFORE the
        // token/role. isAuthenticated in App.kt/NavGraphV2 reacts to the token,
        // so writing profileCompleted first ensures the post-login gate reads
        // the correct value on its first resolution.
        preferenceRepository.setProfileCompleted(response.profileCompleted)
        preferenceRepository.setUserId(response.userId)
        preferenceRepository.setRefreshToken(response.refreshToken)
        preferenceRepository.setUserRole(response.role)
        // RA-S03: persist the display name so portal headers/avatars can greet
        // the real user instead of hardcoding "Parent". Refreshed by getUserDetails.
        preferenceRepository.setUserName(response.name)
        preferenceRepository.setUserToken(response.token)
    }

    override suspend fun getSession(): AuthResponse? {
        // RA-29: always read the live prefs (single source of truth). After a
        // 401-triggered token refresh, the Auth plugin rewrites the token here,
        // so this reflects the rotated token immediately rather than a stale one.
        val token = preferenceRepository.getUserToken().first() ?: return null
        val refreshToken = preferenceRepository.getRefreshToken().first() ?: ""
        val userId = preferenceRepository.getUserId().first() ?: ""
        val role = preferenceRepository.getUserRole().first()
        val profileCompleted = preferenceRepository.getProfileCompleted().first() ?: false
        // RA-S03: surface the persisted display name (empty string if never set).
        val name = preferenceRepository.getUserName().first() ?: ""
        return AuthResponse(
            token = token,
            refreshToken = refreshToken,
            userId = userId,
            name = name,
            role = role,
            profileCompleted = profileCompleted
        )
    }

    override suspend fun refresh(): NetworkResult<AuthResponse> {
        val refreshToken = preferenceRepository.getRefreshToken().first()
            ?: return NetworkResult.Error("No refresh token stored")
        return when (val result = api.refresh(refreshToken)) {
            is NetworkResult.Success -> {
                val data = result.data.data ?: return NetworkResult.Error("No data in response")
                saveSession(data)
                NetworkResult.Success(data)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun changePassword(oldPassword: String?, newPassword: String): NetworkResult<Unit> {
        return when (val result = api.changePassword(
            com.littlebridge.enrollplus.feature.auth.domain.model.ChangePasswordRequest(
                oldPassword = oldPassword,
                newPassword = newPassword
            )
        )) {
            is NetworkResult.Success -> {
                // RA-54: the server flipped profile_completed=true and cleared
                // must_change_password. Persist the resolved gate locally so the
                // TeacherFirstLogin gate never reappears on cold start.
                preferenceRepository.setProfileCompleted(true)
                NetworkResult.Success(Unit)
            }
            is NetworkResult.Error -> NetworkResult.Error(result.message, result.code)
            is NetworkResult.ConnectionError -> NetworkResult.ConnectionError
        }
    }

    override suspend fun logout() {
        // Best-effort server-side revocation (audit §3.6) before clearing local
        // state, so the refresh token cannot be reused for 30 days.
        val token = preferenceRepository.getUserToken().first()
        val refreshToken = preferenceRepository.getRefreshToken().first()
        if (token != null) {
            runCatching { api.logout(token, refreshToken) }
        }
        // Clear the persisted session FIRST so loadTokens() reads null on the next request…
        preferenceRepository.clearSession()
        // …then evict the Ktor Auth plugin's in-memory bearer cache (RA-S01). Without this the
        // singleton HttpClient keeps serving the previous user's cached token until a 401 forces
        // a refresh, leaking a stale session across a logout → re-login (esp. a role switch).
        sessionManager.clearAuthCache()
        // RA-S05: drop the shared selected-child (a Koin single survives logout).
        selectedChildHolder.clear()
    }

    override suspend fun getUserDetails(token: String): NetworkResult<UserDetailsResponse> {
        val result = api.getUserDetails(token)
        // RA-S03: refresh the persisted display name from the authoritative
        // profile so headers/avatars stay correct after a name change.
        if (result is NetworkResult.Success) {
            result.data.data.personalDetails.name
                .takeIf { it.isNotBlank() }
                ?.let { preferenceRepository.setUserName(it) }
        }
        return result
    }
}
