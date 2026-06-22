package com.littlebridge.enrollplus.feature.schools.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SchoolDao {
    @Query("SELECT * FROM school_entity")
    fun getAllSchools(): Flow<List<SchoolEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchools(schools: List<SchoolEntity>)

    @Query("SELECT * FROM school_entity WHERE id = :id")
    suspend fun getSchoolById(id: String): SchoolEntity?

    @Query("DELETE FROM school_entity")
    suspend fun deleteAll()
}

@androidx.room.Entity(tableName = "school_entity")
data class SchoolEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val location: String,
    val board: String,
    val description: String,
    val imageUrl: String
)
