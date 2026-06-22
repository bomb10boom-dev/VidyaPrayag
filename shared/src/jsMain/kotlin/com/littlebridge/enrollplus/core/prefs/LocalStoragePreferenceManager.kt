package com.littlebridge.enrollplus.core.prefs

import kotlinx.browser.localStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.w3c.dom.get
import org.w3c.dom.set

/**
 * RA-28: web (js) session persistence.
 *
 * The previous [InMemoryPreferenceManager] held the session only in
 * [MutableStateFlow]s, so a browser reload wiped token / role / userId /
 * refreshToken and bounced the user back to the landing funnel. This
 * implementation persists every session field to `window.localStorage`
 * while keeping reactive StateFlows seeded from storage at construction,
 * so reads stay reactive and reloads survive.
 *
 * Keys are namespaced under `vp.` to avoid collisions with anything else
 * the page stores. `clearSession()` removes the session keys (theme is
 * deliberately preserved across logout, matching the mobile DataStore).
 */
class LocalStoragePreferenceManager : PreferenceRepository {

    private fun read(key: String): String? = localStorage[key]

    private fun write(key: String, value: String?) {
        if (value == null) localStorage.removeItem(key) else localStorage[key] = value
    }

    private val themeName = MutableStateFlow(read(KEY_THEME) ?: "LIGHT")
    private val userRole = MutableStateFlow(read(KEY_ROLE) ?: "GUEST")
    private val userToken = MutableStateFlow(read(KEY_TOKEN))
    private val userId = MutableStateFlow(read(KEY_USER_ID))
    private val refreshToken = MutableStateFlow(read(KEY_REFRESH))
    private val profileCompleted = MutableStateFlow(read(KEY_PROFILE)?.toBooleanStrictOrNull())
    private val userName = MutableStateFlow(read(KEY_USER_NAME))

    override fun getThemeName(): Flow<String> = themeName
    override suspend fun setThemeName(name: String) {
        themeName.value = name
        write(KEY_THEME, name)
    }

    override fun getUserRole(): Flow<String> = userRole
    override suspend fun setUserRole(role: String) {
        userRole.value = role
        write(KEY_ROLE, role)
    }

    override fun getUserToken(): Flow<String?> = userToken
    override suspend fun setUserToken(token: String?) {
        userToken.value = token
        write(KEY_TOKEN, token)
    }

    override fun getUserId(): Flow<String?> = userId
    override suspend fun setUserId(userId: String?) {
        this.userId.value = userId
        write(KEY_USER_ID, userId)
    }

    override fun getRefreshToken(): Flow<String?> = refreshToken
    override suspend fun setRefreshToken(token: String?) {
        refreshToken.value = token
        write(KEY_REFRESH, token)
    }

    override fun getProfileCompleted(): Flow<Boolean?> = profileCompleted
    override suspend fun setProfileCompleted(completed: Boolean?) {
        profileCompleted.value = completed
        write(KEY_PROFILE, completed?.toString())
    }

    override fun getUserName(): Flow<String?> = userName
    override suspend fun setUserName(name: String?) {
        val v = name?.takeIf { it.isNotBlank() }
        userName.value = v
        write(KEY_USER_NAME, v)
    }

    override suspend fun clearSession() {
        userRole.value = "GUEST"
        userToken.value = null
        userId.value = null
        refreshToken.value = null
        profileCompleted.value = null
        userName.value = null
        write(KEY_ROLE, "GUEST")
        write(KEY_TOKEN, null)
        write(KEY_USER_ID, null)
        write(KEY_REFRESH, null)
        write(KEY_PROFILE, null)
        write(KEY_USER_NAME, null)
    }

    private companion object {
        const val KEY_THEME = "vp.themeName"
        const val KEY_ROLE = "vp.userRole"
        const val KEY_TOKEN = "vp.userToken"
        const val KEY_USER_ID = "vp.userId"
        const val KEY_REFRESH = "vp.refreshToken"
        const val KEY_PROFILE = "vp.profileCompleted"
        const val KEY_USER_NAME = "vp.userName"
    }
}
