package com.littlebridge.enrollplus.di

import com.littlebridge.enrollplus.core.prefs.LocalStoragePreferenceManager
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.schools.data.local.InMemorySchoolLocalDataSource
import com.littlebridge.enrollplus.feature.schools.data.local.SchoolLocalDataSource
import io.ktor.client.engine.js.*
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<com.littlebridge.enrollplus.Platform> { com.littlebridge.enrollplus.getPlatform() }
    single { Js.create() }
    single<SchoolLocalDataSource> { InMemorySchoolLocalDataSource() }
    single<PreferenceRepository> { LocalStoragePreferenceManager() }
}
