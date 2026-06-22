package com.littlebridge.enrollplus.feature.schools.data.repository

import com.littlebridge.enrollplus.feature.schools.data.local.SchoolLocalDataSource
import com.littlebridge.enrollplus.feature.schools.data.remote.KtorSchoolApi
import com.littlebridge.enrollplus.feature.schools.domain.model.School
import com.littlebridge.enrollplus.feature.schools.domain.repository.SchoolRepository
import kotlinx.coroutines.flow.Flow

class SchoolRepositoryImpl(
    private val localDataSource: SchoolLocalDataSource,
    private val api: KtorSchoolApi
) : SchoolRepository {

    override fun getSchools(): Flow<List<School>> {
        return localDataSource.getAllSchools()
    }

    override suspend fun refreshSchools() {
        val remoteSchools = api.fetchSchools()
        localDataSource.deleteAll()
        localDataSource.saveSchools(remoteSchools)
    }

    override suspend fun getSchoolById(id: String): School? {
        return localDataSource.getSchoolById(id)
    }

    override suspend fun clearCache() {
        localDataSource.deleteAll()
    }
}
