/*
 * File: NotificationManagerHelper.kt
 * Module: composeApp / androidMain / notification
 *
 * Standalone helper that owns the LOCAL side of push delivery: notification
 * channel creation, building + posting system-tray notifications, and wiring
 * the tap → deep-link PendingIntent that relaunches MainActivity carrying the
 * in-app route so a future UI layer (Notification Center — explicitly out of
 * scope for this foundation PR) can navigate on tap.
 *
 * WHY IT IS A SEPARATE OBJECT (NOT INSIDE VidyaPrayagFirebaseMessagingService)
 *   The notification-foundation spec mandates that channel/display/deep-link
 *   logic live in its own helper so it is reusable by future callers (a
 *   scheduled local reminder, an in-app event trigger, a debug screen, …)
 *   without depending on the FirebaseMessagingService lifecycle. The service
 *   is a thin receiver that extracts the payload and delegates here.
 *
 * DEEP-LINK CONTRACT
 *   The server sends a `deepLink` data key shaped like an in-app path:
 *     "/announcement/123", "/calendar/event/456", "/student/789".
 *   This helper wraps that path into a `vidyaprayag://app<path>` Uri and sets
 *   it as the PendingIntent's intent data, AND stashes the raw path string in
 *   the EXTRA_DEEP_LINK intent extra. MainActivity (or the Compose layer it
 *   hosts) can then read either form to route the tap once the Notification
 *   Center UI exists. Nothing here assumes a particular navigation library —
 *   the routing layer is free to consume the Uri / extra however it likes.
 */
package com.littlebridge.enrollplus.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.littlebridge.enrollplus.MainActivity
import com.littlebridge.enrollplus.shared.R
import java.util.concurrent.atomic.AtomicInteger

object NotificationManagerHelper {

    private const val TAG = "NotificationManagerHelper"

    /** Default channel id — matches the spec literally. */
    const val CHANNEL_ID_GENERAL = "general_notifications"

    /** Human-readable channel name shown in Android Settings. */
    const val CHANNEL_NAME_GENERAL = "General Notifications"

    /**
     * Custom URI scheme used to wrap in-app deep-link paths so the PendingIntent
     * carries a real, parseable Uri. Future manifest <intent-filter> deep-link
     * entries (or the Compose routing layer) can key off this scheme.
     */
    const val DEEP_LINK_SCHEME = "vidyaprayag"

    /** Intent extra carrying the raw deep-link path string (e.g. "/announcement/123"). */
    const val EXTRA_DEEP_LINK = "com.littlebridge.enrollplus.notification.DEEP_LINK"

    /** Intent extra carrying the notification `type` discriminator. */
    const val EXTRA_NOTIFICATION_TYPE = "com.littlebridge.enrollplus.notification.TYPE"

    /** Intent extra carrying the optional `entityId`. */
    const val EXTRA_ENTITY_ID = "com.littlebridge.enrollplus.notification.ENTITY_ID"

    /** Intent extra carrying the optional `schoolId`. */
    const val EXTRA_SCHOOL_ID = "com.littlebridge.enrollplus.notification.SCHOOL_ID"

    /** Intent extra flag — set true so MainActivity knows it was launched from a push tap. */
    const val EXTRA_FROM_PUSH = "com.littlebridge.enrollplus.notification.FROM_PUSH"

    private val notificationIdSeed = AtomicInteger(2000)

    // ------------------------------------------------------------------
    // Channel creation
    // ------------------------------------------------------------------

    /**
     * Create the default "General Notifications" channel (id [CHANNEL_ID_GENERAL])
     * if it does not already exist. Safe to call on every app start — channel
     * creation is idempotent on Android 8+ (re-asserting an existing channel with
     * the same id is a no-op). On API < 26 channels do not exist and this is a
     * no-op. Importance HIGH so heads-up notifications are shown.
     *
     * MUST be called from Application.onCreate() before any notification is posted.
     */
    fun createDefaultChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: run {
                Log.w(TAG, "NotificationManager system service unavailable — channel not created.")
                return
            }
        if (nm.getNotificationChannel(CHANNEL_ID_GENERAL) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID_GENERAL,
            CHANNEL_NAME_GENERAL,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "General school notifications from VidyaPrayag"
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(channel)
        Log.i(TAG, "Notification channel created: id=$CHANNEL_ID_GENERAL name=$CHANNEL_NAME_GENERAL")
    }

    // ------------------------------------------------------------------
    // Notification display
    // ------------------------------------------------------------------

    /**
     * Build and post a local system-tray notification.
     *
     * @param context        any context (the service / app).
     * @param title          notification title.
     * @param body           notification body text.
     * @param deepLink       optional in-app path ("/announcement/123"); when
     *                       present the tap PendingIntent carries it so a future
     *                       UI layer can route.
     * @param type           optional payload discriminator (e.g. "announcement").
     * @param entityId       optional entity id carried through to the tap handler.
     * @param schoolId       optional tenant id carried through to the tap handler.
     * @param notificationId stable id for this notification; defaults to
     *                       [nextNotificationId]. Pass a derived id to make
     *                       re-posts update the same notification in place.
     * @return the notification id used (useful for later cancellation).
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun displayNotification(
        context: Context,
        title: String,
        body: String,
        deepLink: String? = null,
        type: String? = null,
        entityId: String? = null,
        schoolId: String? = null,
        notificationId: Int = nextNotificationId()
    ): Int {
        // Honour the API 33+ runtime POST_NOTIFICATIONS gate and any user
        // per-app notification muting — never crash if posting is disallowed.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "Notifications not enabled — suppressing local display (title=$title).")
            return notificationId
        }

        val pendingIntent = buildContentPendingIntent(context, deepLink, type, entityId, schoolId)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_GENERAL)
            .setSmallIcon(com.littlebridge.enrollplus.R.drawable.ic_app_mono) // default system icon; app icon drawable may follow
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        runCatching { NotificationManagerCompat.from(context).notify(notificationId, builder.build()) }
            .onFailure { e -> Log.e(TAG, "Failed to post notification id=$notificationId", e) }

        return notificationId
    }

    // ------------------------------------------------------------------
    // Deep-link PendingIntent
    // ------------------------------------------------------------------

    /**
     * Build the [PendingIntent] launched when the user taps the notification.
     * Targets [MainActivity], carrying the deep-link as both the intent data
     * Uri ([normalizeDeepLinkUri]) and the raw path in [EXTRA_DEEP_LINK], plus
     * the type / entityId / schoolId extras and a [EXTRA_FROM_PUSH] flag.
     *
     * Uses FLAG_IMMUTABLE (required on API 31+) combined with FLAG_UPDATE_CURRENT
     * so re-issuing the same notification refreshes its pending intent in place.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun buildContentPendingIntent(
        context: Context,
        deepLink: String?,
        type: String?,
        entityId: String?,
        schoolId: String?
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_FROM_PUSH, true)
            type?.let { putExtra(EXTRA_NOTIFICATION_TYPE, it) }
            entityId?.let { putExtra(EXTRA_ENTITY_ID, it) }
            schoolId?.let { putExtra(EXTRA_SCHOOL_ID, it) }
            deepLink?.takeIf { it.isNotBlank() }?.let { path ->
                putExtra(EXTRA_DEEP_LINK, path)
                normalizeDeepLinkUri(path)?.let { data = it }
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, deepLink.hashCode(), intent, flags)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Convert an in-app path like "/announcement/123" into a parseable
     * `vidyaprayag://app/announcement/123` Uri. If the input already looks like a
     * full URI (has a scheme) it is returned as-is. null/blank → null.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun normalizeDeepLinkUri(deepLink: String?): Uri? {
        if (deepLink.isNullOrBlank()) return null
        // Already a full URI (e.g. "https://…" or a custom scheme) → trust it.
        val parsed = runCatching { Uri.parse(deepLink) }.getOrNull()
        if (parsed != null && !parsed.scheme.isNullOrBlank()) return parsed
        // Treat as an in-app path. Ensure a leading slash.
        val path = if (deepLink.startsWith("/")) deepLink else "/$deepLink"
        return Uri.parse("$DEEP_LINK_SCHEME://app$path")
    }

    /** Monotonic notification id generator (offset above 0 to avoid clashing with small ids). */
    fun nextNotificationId(): Int = notificationIdSeed.incrementAndGet()

    /** Cancel a previously posted notification by id. */
    fun cancel(context: Context, notificationId: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId) }
    }
}
