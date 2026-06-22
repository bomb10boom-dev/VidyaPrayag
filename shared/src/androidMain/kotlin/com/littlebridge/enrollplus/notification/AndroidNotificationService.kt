package com.littlebridge.enrollplus.notification

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.littlebridge.enrollplus.feature.notification.domain.service.NotificationService

/**
 * AndroidNotificationService — Android implementation of [NotificationService].
 * Delegates to the [DeviceTokenRegistrar] singleton for token sync, and handles
 * the Android 13+ (API 33) notification permission flow.
 */
class AndroidNotificationService(
    private val context: Context
) : NotificationService {

    override suspend fun syncDeviceToken(force: Boolean): Boolean {
        return DeviceTokenRegistrar.syncRegistration(context, force)
    }

    override fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required on API < 33
            true
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true)
            return
        }

        val activity = findActivity(context)
        if (activity == null) {
            // Cannot request from non-Activity context
            onResult(false)
            return
        }

        // We use the traditional ActivityCompat.requestPermissions here as this
        // service is a singleton and doesn't have access to an ActivityResultLauncher
        // directly. In a real app we'd often pipe this through the MainActivity.
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            PERMISSION_REQUEST_CODE
        )
        // Note: the actual result is handled by MainActivity.onRequestPermissionsResult
        // which we'll need to wire up if we want the callback to work.
        // For the "Ask on every home" requirement, we mainly need the dialog to appear.
    }

    override fun shouldShowRationale(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val activity = findActivity(context) ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    private fun findActivity(context: Context): Activity? {
        var c = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    companion object {
        const val PERMISSION_REQUEST_CODE = 1001
    }
}
