package com.littlebridge.enrollplus.notification

import com.littlebridge.enrollplus.feature.notification.domain.service.NotificationService

/**
 * IosNotificationService — iOS implementation of [NotificationService].
 * Currently a no-op as the iOS push integration is handled natively
 * or deferred.
 */
class IosNotificationService : NotificationService {

    override suspend fun syncDeviceToken(force: Boolean): Boolean {
        // iOS push registration usually happens via AppDelegate and native APIs.
        // Returning true here to satisfy the dependency in common code.
        return true
    }

    override fun hasPermission(): Boolean = true
    override fun requestPermission(onResult: (Boolean) -> Unit) { onResult(true) }
    override fun shouldShowRationale(): Boolean = false
}
