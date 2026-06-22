package com.littlebridge.enrollplus.feature.notification.domain.service

/**
 * NotificationService — common interface for managing device-specific notification
 * registration and synchronization. This allows common code (like ViewModels) to
 * trigger platform-specific registration logic (FCM on Android, APNs on iOS).
 */
interface NotificationService {

    /**
     * Trigger a synchronization of the device token with the backend.
     * Implementation should handle fetching the platform token, comparing it
     * against local cache, and registering with the backend if necessary.
     *
     * @param force if true, bypass the cache check and force a backend registration.
     * @return true if synchronization succeeded (or was already in sync), false otherwise.
     */
    suspend fun syncDeviceToken(force: Boolean = false): Boolean

    /**
     * Checks if the app currently has the platform-level permission to post
     * notifications. On Android < 13 this returns true.
     */
    fun hasPermission(): Boolean

    /**
     * Requests the platform-level permission to post notifications.
     * On Android 13+, this should trigger the system permission dialog.
     *
     * @param onResult callback with true if granted, false if denied.
     */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * Checks if we should show a rationale for notification permission
     * (i.e. the user has denied once but not permanently).
     */
    fun shouldShowRationale(): Boolean
}
