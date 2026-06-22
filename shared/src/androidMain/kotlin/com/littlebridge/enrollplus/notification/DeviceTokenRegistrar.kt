/*
 * File: DeviceTokenRegistrar.kt
 * Module: shared / androidMain / notification
 *
 * Owns the client side of the device-token registration lifecycle:
 *   1. Fetch the current FCM token from FirebaseMessaging.
 *   2. Compare it against the cached token in PreferenceRepository.
 *   3. Only when it has CHANGED (or there is no cached token) POST it to the
 *      backend via NotificationApi — this is the "fetch → compare cached →
 *      register if changed" gate the foundation spec mandates, so a normal
 *      cold start with a stable token is a network no-op.
 *   4. Persist the freshly registered token back into the cache.
 *
 * WHY A DEDICATED REGISTRAR (NOT INLINE IN THE SERVICE / APP)
 *   Token registration happens from two entry points:
 *     - Application.onCreate()  (app-startup registration)
 *     - FirebaseMessagingService.onNewToken() (token rotation)
 *   Both share the exact same fetch → compare → register → persist flow, so it
 *   lives here once. The service and the app both call [registerIfChanged].
 *
 * GRACEFUL DEGRADATION
 *   - Firebase not initialised (no google-services config) → skip silently.
 *   - Not signed in (no access token in prefs) → skip; the token is not pushed
 *     until the next authenticated launch (the server requires JWT).
 *   - Network / API failure → the token is NOT cached, so the next launch
 *     retries. We only cache after a confirmed successful registration.
 *
 * NOTE on kotlinx-coroutines-play-services:
 *   That helper is not on the classpath, so we bridge the Firebase Task result
 *   via suspendCancellableCoroutine using only kotlinx-coroutines-core.
 */
package com.littlebridge.enrollplus.notification

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.feature.notification.data.remote.NotificationApi
import com.littlebridge.enrollplus.feature.notification.domain.model.RegisterDeviceTokenRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object DeviceTokenRegistrar {

    private const val TAG = "DeviceTokenRegistrar"

    /**
     * Fetch the FCM token, compare with the cached value, and register with the
     * backend when it has changed. Safe to call on every app start and on every
     * onNewToken() callback. Returns true when a registration call succeeded
     * (or the token was already in sync); false when it was skipped or failed.
     */
    suspend fun syncRegistration(
        context: Context,
        force: Boolean = false
    ): Boolean {

        // Firebase available?
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.d(TAG, "Firebase not initialised — skipping token sync.")
            return false
        }

        // Current FCM token
        val token = runCatching { fetchFcmToken() }.getOrNull()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "FCM token unavailable.")
            return false
        }

        // Resolve dependencies
        val prefs: PreferenceRepository = resolveKoin() ?: run {
            Log.w(TAG, "PreferenceRepository unavailable.")
            return false
        }

        val api: NotificationApi = resolveKoin() ?: run {
            Log.w(TAG, "NotificationApi unavailable.")
            return false
        }

        // Must be authenticated
        val accessToken = runCatching {
            prefs.getUserToken().first()
        }.getOrNull()

        if (accessToken.isNullOrBlank()) {
            Log.d(TAG, "User not authenticated — deferring sync.")
            return false
        }

        // Cache state
        val cachedToken = runCatching {
            prefs.getFcmToken().first()
        }.getOrNull()

        // Skip only when token unchanged AND caller did not force sync
        if (!force &&
            !cachedToken.isNullOrBlank() &&
            cachedToken == token
        ) {
            Log.d(TAG, "FCM token already synced.")
            return true
        }

        val request = RegisterDeviceTokenRequest(
            token = token,
            platform = "android",
            appVersion = appVersion(context),
            deviceModel = deviceModel()
        )

        val result = runCatching {
            api.registerDeviceToken(request)
        }.getOrElse { e ->
            Log.w(TAG, "Device registration failed: ${e.message}")
            return false
        }

        val success =
            (result as? NetworkResult.Success)?.data?.success == true

        if (!success) {
            Log.w(TAG, "Backend rejected device registration.")
            return false
        }

        runCatching {
            prefs.setFcmToken(token)
        }.onFailure {
            Log.w(TAG, "Failed to cache token: ${it.message}")
        }

        Log.i(
            TAG,
            if (force)
                "Device registration force-synced."
            else
                "Device registration synced."
        )

        return true
    }

    // ------------------------------------------------------------------
    // FCM token fetch (Task → suspend bridge)
    // ------------------------------------------------------------------

    /**
     * Bridge the FirebaseMessaging.getInstance().token Task into a suspend call.
     * Resumes with the token string on success, or throws on failure. The Task
     * is awaited via addOnSuccessListener / addOnFailureListener so we never
     * block a thread.
     */
    private suspend fun fetchFcmToken(): String = suspendCancellableCoroutine { cont ->
        val task: Task<String> = FirebaseMessaging.getInstance().token
        task.addOnSuccessListener { token -> if (cont.isActive) cont.resume(token) }
        task.addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    private fun appVersion(context: Context): String? = runCatching {
        val pm = context.packageManager
        val info = pm.getPackageInfo(context.packageName, 0)
        info.versionName
    }.getOrNull()

    private fun deviceModel(): String? = runCatching {
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        if (manufacturer.isBlank() && model.isBlank()) null
        else if (model.startsWith(manufacturer, ignoreCase = true)) model
        else "$manufacturer $model"
    }.getOrNull()

    // ------------------------------------------------------------------
    // Koin resolution
    // ------------------------------------------------------------------

    /**
     * Resolve a Koin singleton. Returns null if Koin has not been started yet
     * (e.g. a callback firing before Application.onCreate completes) so callers
     * can degrade gracefully instead of crashing.
     */
    private inline fun <reified T : Any> resolveKoin(): T? = runCatching {
        org.koin.core.context.GlobalContext.get().get<T>()
    }.getOrNull()
}
