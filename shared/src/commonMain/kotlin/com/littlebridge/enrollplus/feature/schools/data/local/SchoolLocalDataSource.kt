package com.littlebridge.enrollplus.feature.schools.data.local

import com.littlebridge.enrollplus.feature.schools.domain.model.School
import kotlinx.coroutines.flow.Flow

interface SchoolLocalDataSource {
    fun getAllSchools(): Flow<List<School>>
    suspend fun saveSchools(schools: List<School>)
    suspend fun getSchoolById(id: String): School?
    suspend fun deleteAll()
}
