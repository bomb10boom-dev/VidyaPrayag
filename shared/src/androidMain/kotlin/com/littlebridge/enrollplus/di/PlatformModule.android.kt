package com.littlebridge.enrollplus.di

import com.littlebridge.enrollplus.AndroidPlatform
import com.littlebridge.enrollplus.Platform
import com.littlebridge.enrollplus.core.database.AppDatabase
import com.littlebridge.enrollplus.core.database.DatabaseFactory
import com.littlebridge.enrollplus.core.prefs.PreferenceManager
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.prefs.createDataStore
import com.littlebridge.enrollplus.feature.schools.data.local.RoomSchoolLocalDataSource
import com.littlebridge.enrollplus.feature.schools.data.local.SchoolLocalDataSource
import io.ktor.client.engine.okhttp.*
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<Platform> { AndroidPlatform(androidContext().cacheDir.absolutePath.toPath()) }
    single { OkHttp.create() }
    single { DatabaseFactory(androidContext()) }
    single<AppDatabase> { get<DatabaseFactory>().createBuilder().build() }
    single { get<AppDatabase>().schoolDao() }
    single<SchoolLocalDataSource> { RoomSchoolLocalDataSource(get()) }

    single {
        val context = androidContext()
        createDataStore {
            context.filesDir.resolve("edu_trust_prefs.preferences_pb").absolutePath
        }
    }
    single<PreferenceRepository> { PreferenceManager(get()) }
    single<com.littlebridge.enrollplus.feature.notification.domain.service.NotificationService> {
        com.littlebridge.enrollplus.notification.AndroidNotificationService(androidContext())
    }
}
