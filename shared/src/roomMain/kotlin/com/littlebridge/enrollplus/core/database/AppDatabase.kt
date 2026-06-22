package com.littlebridge.enrollplus.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.littlebridge.enrollplus.feature.schools.data.local.SchoolDao
import com.littlebridge.enrollplus.feature.schools.data.local.SchoolEntity

@Database(entities = [SchoolEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun schoolDao(): SchoolDao
    companion object
}

// Room generator will provide this on iOS
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect fun AppDatabase.Companion.instantiateImpl(): AppDatabase
