package com.littlebridge.enrollplus.di

import com.littlebridge.enrollplus.core.database.AppDatabase
import com.littlebridge.enrollplus.core.database.DatabaseFactory
import com.littlebridge.enrollplus.core.prefs.PreferenceManager
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.core.prefs.createDataStore
import com.littlebridge.enrollplus.feature.schools.data.local.RoomSchoolLocalDataSource
import com.littlebridge.enrollplus.feature.schools.data.local.SchoolLocalDataSource
import io.ktor.client.engine.darwin.*
import kotlinx.cinterop.ExperimentalForeignApi
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual fun platformModule(): Module = module {
    single<com.littlebridge.enrollplus.Platform> { com.littlebridge.enrollplus.getPlatform() }
    single { Darwin.create() }
    single { DatabaseFactory() }
    single<AppDatabase> { get<DatabaseFactory>().createBuilder().build() }
    single { get<AppDatabase>().schoolDao() }
    single<SchoolLocalDataSource> { RoomSchoolLocalDataSource(get()) }

    single {
        createDataStore {
            val directory = NSFileManager.defaultManager.URLForDirectory(
                directory = NSDocumentDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null
            )
            requireNotNull(directory).path + "/edu_trust_prefs.preferences_pb"
        }
    }
    single<PreferenceRepository> { PreferenceManager(get()) }
    single<com.littlebridge.enrollplus.feature.notification.domain.service.NotificationService> {
        com.littlebridge.enrollplus.notification.IosNotificationService()
    }
}
