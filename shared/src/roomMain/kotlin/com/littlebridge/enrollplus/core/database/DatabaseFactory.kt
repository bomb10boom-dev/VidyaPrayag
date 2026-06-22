package com.littlebridge.enrollplus.core.database

import androidx.room.RoomDatabase

expect class DatabaseFactory {
    fun createBuilder(): RoomDatabase.Builder<AppDatabase>
}
