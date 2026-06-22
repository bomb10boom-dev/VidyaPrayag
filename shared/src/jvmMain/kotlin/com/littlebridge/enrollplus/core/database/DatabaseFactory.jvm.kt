package com.littlebridge.enrollplus.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual class DatabaseFactory {
    actual fun createBuilder(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = File(System.getProperty("java.io.tmpdir"), "vidya_prayag.db")
        return Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        ).setDriver(BundledSQLiteDriver())
    }
}
