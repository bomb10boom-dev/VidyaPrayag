package com.littlebridge.enrollplus.core.database

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

actual class DatabaseFactory {
    actual fun createBuilder(): RoomDatabase.Builder<AppDatabase> {
        val dbFile = NSHomeDirectory() + "/vidya_prayag.db"
        return Room.databaseBuilder<AppDatabase>(
            name = dbFile,
            factory = { AppDatabase.instantiateImpl() }
        )
    }
}
