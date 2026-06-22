package com.littlebridge.enrollplus.feature.admin.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.littlebridge.enrollplus.core.network.NetworkResult
import com.littlebridge.enrollplus.core.prefs.PreferenceRepository
import com.littlebridge.enrollplus.feature.admin.domain.repository.AnalyticsRepository
import com.littlebridge.enrollplus.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class StarTeacher(
    val rank: Int,
    val name: String,
    val department: String,
    val score: Double,
    val imageUrl: String
)

data class FacultyAccountability(
    val id: String,
    val name: String,
    val department: String,
    val complianceScore: Int,
    val avgUpdateDelay: String,
    val studentAvgMark: String,
    val riskCorrelation: String, // "Stable", "High Risk", "Watching"
    val initials: String
)

data class DeptEfficiency(
    val name: String,
    val percentage: Int
)

data class TeacherPerformanceState(
    val aggregateCompliance: String = "",
    val complianceTrend: String = "",
    val syllabusUpdateTrend: List<Float> = emptyList(),
    val starFaculty: List<StarTeacher> = emptyList(),
    val accountabilityMatrix: List<FacultyAccountability> = emptyList(),
    val deptEfficiencies: List<DeptEfficiency> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class TeacherPerformanceViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TeacherPerformanceState())
    val state: StateFlow<TeacherPerformanceState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false); return@launch
            }
            when (val result = analyticsRepository.getTeacherPerformance(token)) {
                is NetworkResult.Success -> {
                    _state.value = parseTeacher(result.data.data).copy(isLoading = false)
                }
                is NetworkResult.Error -> {
                    AppLogger.e("TeacherPerformanceVM", "getTeacherPerformance error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("TeacherPerformanceVM", "getTeacherPerformance connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    private fun parseTeacher(element: JsonElement?): TeacherPerformanceState {
        val obj = (element as? JsonObject) ?: return TeacherPerformanceState()
        return try {
            val trend = (obj["syllabus_update_trend"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.floatOrNull } ?: emptyList()
            val stars = (obj["star_faculty"] as? JsonArray)?.mapNotNull { parseStar(it) } ?: emptyList()
            val matrix = (obj["accountability_matrix"] as? JsonArray)?.mapNotNull { parseAccountability(it) } ?: emptyList()
            val depts = (obj["dept_efficiencies"] as? JsonArray)?.mapNotNull { parseDept(it) } ?: emptyList()

            TeacherPerformanceState(
                aggregateCompliance  = obj["aggregate_compliance"]?.jsonPrimitive?.contentOrNull ?: "",
                complianceTrend      = obj["compliance_trend"]?.jsonPrimitive?.contentOrNull ?: "",
                syllabusUpdateTrend  = trend,
                starFaculty          = stars,
                accountabilityMatrix = matrix,
                deptEfficiencies     = depts
            )
        } catch (e: Exception) {
            AppLogger.e("TeacherPerformanceVM", "parseTeacher failed: ${e.message}")
            TeacherPerformanceState(errorMessage = "Could not parse server response")
        }
    }

    private fun parseStar(el: JsonElement): StarTeacher? {
        return try {
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null
            StarTeacher(
                rank       = o["rank"]?.jsonPrimitive?.intOrNull ?: 0,
                name       = name,
                department = o["department"]?.jsonPrimitive?.contentOrNull ?: "",
                score      = o["score"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                imageUrl   = o["image_url"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        } catch (_: Exception) { null }
    }

    private fun parseAccountability(el: JsonElement): FacultyAccountability? {
        return try {
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
            FacultyAccountability(
                id              = id,
                name            = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                department      = o["department"]?.jsonPrimitive?.contentOrNull ?: "",
                complianceScore = o["compliance_score"]?.jsonPrimitive?.intOrNull ?: 0,
                avgUpdateDelay  = o["avg_update_delay"]?.jsonPrimitive?.contentOrNull ?: "",
                studentAvgMark  = o["student_avg_mark"]?.jsonPrimitive?.contentOrNull ?: "",
                riskCorrelation = o["risk_correlation"]?.jsonPrimitive?.contentOrNull ?: "Stable",
                initials        = o["initials"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        } catch (_: Exception) { null }
    }

    private fun parseDept(el: JsonElement): DeptEfficiency? {
        return try {
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null
            DeptEfficiency(
                name       = name,
                percentage = o["percentage"]?.jsonPrimitive?.intOrNull ?: 0
            )
        } catch (_: Exception) { null }
    }
}
