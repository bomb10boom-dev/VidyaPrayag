package com.littlebridge.enrollplus.core.prefs

import kotlinx.coroutines.flow.Flow

interface PreferenceRepository {
    fun getThemeName(): Flow<String>
    suspend fun setThemeName(name: String)
    
    fun getUserRole(): Flow<String>
    suspend fun setUserRole(role: String)

    fun getUserToken(): Flow<String?>
    suspend fun setUserToken(token: String?)

    // --- session persistence (audit §3.4, finding F) ---
    // These three were previously only held in an in-memory cache and were
    // permanently lost on app restart, making the full session unrecoverable
    // and the server's refresh infrastructure unreachable.
    fun getUserId(): Flow<String?>
    suspend fun setUserId(userId: String?)

    fun getRefreshToken(): Flow<String?>
    suspend fun setRefreshToken(token: String?)

    fun getProfileCompleted(): Flow<Boolean?>
    suspend fun setProfileCompleted(completed: Boolean?)

    // RA-S03: the user's display name, persisted at sign-in and refreshed from
    // GET /user/details, so the portal headers/avatars can greet the real user
    // instead of hardcoding "Parent". (school_id is intentionally NOT persisted
    // — the server authoritatively derives it from the JWT.)
    fun getUserName(): Flow<String?>
    suspend fun setUserName(name: String?)

    // --- notification foundation (FCM device-token registration cache) ---
    // The last FCM token we successfully registered with the backend. The
    // Android DeviceTokenRegistrar compares the freshly-fetched token against
    // this cache and only POSTs /api/device-tokens when it has CHANGED —
    // avoiding a redundant registration on every cold start. Cleared on
    // logout so a re-login with a different account re-registers the same
    // physical device token under the new user.
    fun getFcmToken(): Flow<String?>
    suspend fun setFcmToken(token: String?)

    // --- notification permission state (audit §11.2) ---
    // Whether the user has explicitly declined the notification rationale.
    // We check this before showing the rationale again to avoid pestering.
    fun getNotificationsDeclined(): Flow<Boolean>
    suspend fun setNotificationsDeclined(declined: Boolean)

    suspend fun clearSession()
}
