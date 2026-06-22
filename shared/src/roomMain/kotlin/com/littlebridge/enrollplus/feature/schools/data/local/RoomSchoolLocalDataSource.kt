package com.littlebridge.enrollplus.feature.schools.data.local

import com.littlebridge.enrollplus.feature.schools.domain.model.School
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSchoolLocalDataSource(
    private val dao: SchoolDao
) : SchoolLocalDataSource {

    override fun getAllSchools(): Flow<List<School>> {
        return dao.getAllSchools().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveSchools(schools: List<School>) {
        dao.insertSchools(schools.map { it.toEntity() })
    }

    override suspend fun getSchoolById(id: String): School? {
        return dao.getSchoolById(id)?.toDomain()
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

fun SchoolEntity.toDomain() = School(
    id = id,
    name = name,
    location = location,
    board = board,
    description = description,
    imageUrl = imageUrl,
    sriScore = 0.0, // Default for now
    feesRange = "",
    isVerified = true
)

fun School.toEntity() = SchoolEntity(
    id = id,
    name = name,
    location = location,
    board = board,
    description = description,
    imageUrl = imageUrl
)
