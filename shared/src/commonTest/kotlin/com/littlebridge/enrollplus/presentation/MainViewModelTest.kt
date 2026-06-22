package com.littlebridge.enrollplus.presentation

import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.notification.domain.service.NotificationService
import com.littlebridge.enrollplus.feature.schools.domain.model.School
import com.littlebridge.enrollplus.feature.schools.domain.repository.SchoolRepository
import com.littlebridge.enrollplus.feature.schools.domain.usecase.GetSchoolsUseCase
import com.littlebridge.enrollplus.feature.auth.domain.repository.AuthRepository
import com.littlebridge.enrollplus.core.network.NetworkResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `syncDeviceToken is called when userToken becomes non-null`() = runTest {
        val fakePrefs = FakePreferenceRepository()
        val fakeNotificationService = FakeNotificationService()
        val fakeSchoolRepo = FakeSchoolRepository()
        val fakeSchoolsUseCase = GetSchoolsUseCase(fakeSchoolRepo)
        val fakeAuthRepo = FakeAuthRepository()

        // Initial state: not logged in
        fakePrefs.userTokenFlow.value = null

        val viewModel = MainViewModel(
            fakeSchoolsUseCase,
            fakePrefs,
            fakeAuthRepo,
            fakeNotificationService
        )

        advanceUntilIdle()
        assertEquals(0, fakeNotificationService.syncCount, "Should not sync when not logged in")

        // Act: User logs in
        fakePrefs.userTokenFlow.value = "valid-token"
        
        advanceUntilIdle()
        assertEquals(1, fakeNotificationService.syncCount, "Should sync when logged in")
    }

    // --- Fakes ---

    class FakePreferenceRepository : PreferenceRepository {
        val userTokenFlow = MutableStateFlow<String?>(null)
        override fun getUserToken(): Flow<String?> = userTokenFlow
        override fun getUserRole(): Flow<String> = flowOf("GUEST")
        override fun getThemeName(): Flow<String> = flowOf("LIGHT")
        override suspend fun setUserToken(token: String?) { userTokenFlow.value = token }
        override suspend fun setUserRole(role: String) {}
        override suspend fun setThemeName(name: String) {}
        override fun getFcmToken(): Flow<String?> = flowOf(null)
        override suspend fun setFcmToken(token: String?) {}
        override fun getNotificationsDeclined(): Flow<Boolean> = flowOf(false)
        override suspend fun setNotificationsDeclined(declined: Boolean) {}
        override suspend fun clearSession() {}
        
        override fun getUserId(): Flow<String?> = flowOf(null)
        override suspend fun setUserId(userId: String?) {}
        override fun getRefreshToken(): Flow<String?> = flowOf(null)
        override suspend fun setRefreshToken(token: String?) {}
        override fun getProfileCompleted(): Flow<Boolean?> = flowOf(null)
        override suspend fun setProfileCompleted(completed: Boolean?) {}
        override fun getUserName(): Flow<String?> = flowOf(null)
        override suspend fun setUserName(name: String?) {}
    }

    class FakeNotificationService : NotificationService {
        var syncCount = 0
        override suspend fun syncDeviceToken(force: Boolean): Boolean {
            syncCount++
            return true
        }
        override fun hasPermission(): Boolean = true
        override fun requestPermission(onResult: (Boolean) -> Unit) { onResult(true) }
        override fun shouldShowRationale(): Boolean = false
    }

    class FakeSchoolRepository : SchoolRepository {
        override fun getSchools(): Flow<List<School>> = flowOf(emptyList())
        override suspend fun refreshSchools() {}
        override suspend fun getSchoolById(id: String): School? = null
        override suspend fun clearCache() {}
    }

    class FakeAuthRepository : AuthRepository {
        override suspend fun checkUser(identifier: String): NetworkResult<com.littlebridge.enrollplus.feature.auth.domain.model.AuthFlow> = TODO()
        override suspend fun signup(request: com.littlebridge.enrollplus.feature.auth.domain.model.SignupRequest): NetworkResult<com.littlebridge.enrollplus.feature.auth.domain.model.AuthResponse> = TODO()
        override suspend fun registerSchool(request: com.littlebridge.enrollplus.feature.auth.domain.model.SchoolRegisterRequest): NetworkResult<com.littlebridge.enrollplus.feature.auth.domain.model.AuthResponse> = TODO()
        override suspend fun login(request: com.littlebridge.enrollplus.feature.auth.domain.model.LoginRequest): NetworkResult<com.littlebridge.enrollplus.feature.auth.domain.model.AuthResponse> = TODO()
        override suspend fun sendOtp(identifier: String, purpose: String?): NetworkResult<String> = TODO()
        override suspend fun verifyOtp(identifier: String, code: String, purpose: String?): NetworkResult<Boolean> = TODO()
        override suspend fun saveSession(response: com.littlebridge.enrollplus.feature.auth.domain.model.AuthResponse) {}
        override suspend fun getSession(): com.littlebridge.enrollplus.feature.auth.domain.model.AuthResponse? = null
        override suspend fun refresh(): NetworkResult<com.littlebridge.enrollplus.feature.auth.domain.model.AuthResponse> = TODO()
        override suspend fun changePassword(oldPassword: String?, newPassword: String): NetworkResult<Unit> = TODO()
        override suspend fun logout() {}
        override suspend fun getUserDetails(token: String): NetworkResult<com.littlebridge.enrollplus.feature.auth.domain.model.UserDetailsResponse> = TODO()
    }
}
