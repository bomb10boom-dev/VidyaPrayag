package com.littlebridge.enrollplus.feature.schools.data.local

import com.littlebridge.enrollplus.feature.schools.domain.model.School
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemorySchoolLocalDataSource : SchoolLocalDataSource {
    private val schools = MutableStateFlow<Map<String, School>>(emptyMap())

    override fun getAllSchools(): Flow<List<School>> {
        return schools.map { it.values.toList() }
    }

    override suspend fun saveSchools(schools: List<School>) {
        this.schools.value = this.schools.value + schools.associateBy { it.id }
    }

    override suspend fun getSchoolById(id: String): School? {
        return schools.value[id]
    }

    override suspend fun deleteAll() {
        schools.value = emptyMap()
    }
}
