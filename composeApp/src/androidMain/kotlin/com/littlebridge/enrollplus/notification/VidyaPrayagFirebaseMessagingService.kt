/*
 * File: VidyaPrayagFirebaseMessagingService.kt
 * Module: composeApp / androidMain / notification
 *
 * The Firebase Cloud Messaging entry point on Android. Registered in
 * AndroidManifest.xml with the
 *   <action android:name="com.google.firebase.MESSAGING_EVENT" />
 * intent filter so the system routes incoming FCM pushes + token-rotation
 * callbacks here.
 *
 * RESPONSIBILITIES (thin receiver — heavy lifting is delegated)
 *   onNewToken(token)
 *       Token rotation. Delegates to DeviceTokenRegistrar which performs the
 *       fetch → compare cached → register-if-changed flow against the backend
 *       (POST /api/device-tokens). Launched on a dedicated coroutine scope so
 *       the system-bound service lifecycle does not block the network round
 *       trip.
 *   onMessageReceived(remoteMessage)
 *       Foreground / data-only push delivery. Extracts the title / body /
 *       type / deepLink / entityId / schoolId from the data payload (the
 *       server's NotificationService always populates the data block, even
 *       when it also sends a notification block for background auto-render)
 *       and falls back to the notification block when data is absent. Then
 *       delegates the local system-tray display to NotificationManagerHelper.
 *
 * WHY THE PAYLOAD IS READ FROM BOTH BLOCKS
 *   FCM delivers a push as either a `notification` payload, a `data` payload,
 *   or a hybrid containing both. The server always sends hybrid:
 *     - notification { title, body }  → lets the system tray auto-render when
 *       the app is in the BACKGROUND (onMessageReceived is NOT invoked then).
 *     - data { title, body, type, deepLink, entityId, schoolId, ... }
 *       → delivered to onMessageReceived when the app is in the FOREGROUND.
 *   So when onMessageReceived fires we prefer the data block (it carries the
 *   routing fields the notification block cannot), and only fall back to the
 *   notification block for the display text when the data block omits it.
 *
 * GRACEFUL DEGRADATION
 *   - An empty RemoteMessage (no notification, no data) is logged and dropped.
 *   - Any throwable during display is caught so a malformed payload never
 *     crashes the system-managed service.
 *   - DeviceTokenRegistrar itself no-ops when Firebase is not initialised or
 *     the user is not signed in.
 */
package com.littlebridge.enrollplus.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class VidyaPrayagFirebaseMessagingService : FirebaseMessagingService() {

    private companion object {
        const val TAG = "VidyaPrayagFcmService"

        // Data-payload key contract — MUST match the server's NotificationService
        // payload builder so the round-trip keys agree exactly.
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_TYPE = "type"
        const val KEY_DEEP_LINK = "deepLink"
        const val KEY_ENTITY_ID = "entityId"
        const val KEY_SCHOOL_ID = "schoolId"
    }

    /**
     * Coroutine scope owned by the service. SupervisorJob so a single
     * registration failure does not cancel sibling jobs. Cancelled in
     * onDestroy to avoid leaking the scope past the service lifecycle.
     */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ------------------------------------------------------------------
    // Token rotation
    // ------------------------------------------------------------------

    /**
     * Called by FCM when a new registration token is issued (app install,
     * app data clear, token rotation). The fresh token is the new identity
     * for this device, so we run the full register-if-changed flow which:
     *   - compares against the cached token,
     *   - POSTs to /api/device-tokens when it changed,
     *   - persists the new token on success.
     * The [token] parameter is intentionally not used directly —
     * DeviceTokenRegistrar re-fetches via FirebaseMessaging.getInstance().token
     * so the single source of truth flows through the same compare/cache path
     * used at app startup. This keeps onNewToken and cold-start registration
     * identical.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "onNewToken received — triggering registration refresh.")
        scope.launch {
            runCatching { DeviceTokenRegistrar.syncRegistration(this@VidyaPrayagFirebaseMessagingService) }
                .onSuccess { ok -> Log.i(TAG, "Token registration refresh completed (ok=$ok).") }
                .onFailure { e -> Log.w(TAG, "Token registration refresh failed: ${e.message}") }
        }
    }

    // ------------------------------------------------------------------
    // Foreground / data message delivery
    // ------------------------------------------------------------------

    /**
     * Invoked for data and hybrid payloads when the app is foregrounded (and
     * for all data-only payloads regardless of app state). Extracts the
     * routing/display fields and delegates to [NotificationManagerHelper].
     *
     * Notification-only payloads delivered while the app is backgrounded are
     * auto-rendered by the system tray and do NOT reach this callback — that
     * is why the server always includes the `notification` block.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        // Nothing to render — log and bail.
        if (data.isEmpty() && notification == null) {
            Log.d(TAG, "onMessageReceived: empty payload — ignoring.")
            return
        }

        // Prefer the data block (carries routing fields); fall back to the
        // notification block for display text when data is absent.
        val title = data[KEY_TITLE]
            ?: notification?.title
        val body = data[KEY_BODY]
            ?: notification?.body
        val type = data[KEY_TYPE]
        val deepLink = data[KEY_DEEP_LINK]
        val entityId = data[KEY_ENTITY_ID]
        val schoolId = data[KEY_SCHOOL_ID]

        if (title.isNullOrBlank() && body.isNullOrBlank()) {
            Log.w(TAG, "onMessageReceived: no title/body in payload — ignoring. dataKeys=${data.keys}")
            return
        }

        Log.d(
            TAG,
            "onMessageReceived: title=${title?.take(40)} type=$type deepLink=$deepLink " +
                "entityId=$entityId schoolId=$schoolId"
        )

        runCatching {
            NotificationManagerHelper.displayNotification(
                context = this,
                title = title.orEmpty(),
                body = body.orEmpty(),
                deepLink = deepLink,
                type = type,
                entityId = entityId,
                schoolId = schoolId
            )
        }.onFailure { e ->
            Log.e(TAG, "Failed to display local notification for push payload.", e)
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onDestroy() {
        // Cancel any in-flight registration coroutine so the scope does not
        // outlive the system-managed service instance.
        runCatching { scope.cancel("Service destroyed") }
        super.onDestroy()
    }
}
