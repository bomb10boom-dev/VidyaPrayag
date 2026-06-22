package com.littlebridge.enrollplus

import android.app.Application
import android.util.Log
import com.littlebridge.enrollplus.di.initKoin
import com.littlebridge.enrollplus.notification.NotificationManagerHelper
import com.littlebridge.enrollplus.util.AppConfig
import org.koin.android.ext.koin.androidContext

class EnRollPlusApp : Application() {
    companion object {
        lateinit var instance: EnRollPlusApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationManagerHelper.createDefaultChannel(this)

        // Surface the resolved backend at boot so you can confirm in Logcat
        // exactly which server the phone is calling (filter tag: VidyaPrayagApp).
        Log.i(
            "VidyaPrayagApp",
            "Backend -> authBaseUrl=${AppConfig.authBaseUrl} schoolBaseUrl=${AppConfig.schoolBaseUrl}"
        )

        initKoin {
            androidContext(this@EnRollPlusApp)
        }
    }
}
