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
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ---------------------------------------------------------------------------
// UI data classes — consumed by ClassPerformanceScreen.kt. Field shapes are
// stable; only the source of the data changes (server CMS blob instead of
// hardcoded defaults).
// ---------------------------------------------------------------------------

data class GradeDistribution(
    val grade: String,
    val percentage: Int,
    val value: Float // normalized 0-1 for chart
)

data class SubjectMatrixItem(
    val name: String,
    val percentage: Int,
    val trend: String // "up", "flat", "down"
)

data class ProgressMonitoringItem(
    val name: String,
    val initials: String,
    val math: String,
    val science: String,
    val literature: String,
    val attendance: String,
    val status: String // "EXCELLING", "PEWS ALERT", "CONSISTENT"
)

data class ClassPerformanceState(
    val gradeDistribution: List<GradeDistribution> = emptyList(),
    val avgProficiency: String = "",
    val activeStudents: Int = 0,
    val medianGrade: String = "",
    val subjectMatrix: List<SubjectMatrixItem> = emptyList(),
    val criticalRiskCount: Int = 0,
    val moderateRiskCount: Int = 0,
    val proficiencyTargetReach: Int = 0,
    val topPerformerName: String = "",
    val topPerformerDetails: String = "",
    val recentProgress: List<ProgressMonitoringItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ClassPerformanceViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ClassPerformanceState())
    val state: StateFlow<ClassPerformanceState> = _state.asStateFlow()

    init { load() }

    fun load(className: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            when (val result = analyticsRepository.getClassPerformance(token, className)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.value = parseClassPerformance(data).copy(isLoading = false)
                }
                is NetworkResult.Error -> {
                    AppLogger.e("ClassPerformanceVM", "getClassPerformance error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("ClassPerformanceVM", "getClassPerformance connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // JSON → state parser. Confined to `shared` where kotlinx.serialization is
    // available; the screen only ever sees the plain data classes above.
    // -----------------------------------------------------------------------

    private fun parseClassPerformance(element: JsonElement?): ClassPerformanceState {
        val obj = (element as? JsonObject) ?: return ClassPerformanceState()
        return try {
            val grade = (obj["grade_distribution"] as? JsonArray)?.mapNotNull { parseGrade(it) } ?: emptyList()
            val summary = obj["summary"] as? JsonObject
            val matrix = (obj["subject_matrix"] as? JsonArray)?.mapNotNull { parseSubject(it) } ?: emptyList()
            val risk = obj["risk"] as? JsonObject
            val top = obj["top_performer"] as? JsonObject
            val progress = (obj["recent_progress"] as? JsonArray)?.mapNotNull { parseProgress(it) } ?: emptyList()

            ClassPerformanceState(
                gradeDistribution      = grade,
                avgProficiency         = summary?.get("avg_proficiency")?.jsonPrimitive?.contentOrNull ?: "",
                activeStudents         = summary?.get("active_students")?.jsonPrimitive?.intOrNull ?: 0,
                medianGrade            = summary?.get("median_grade")?.jsonPrimitive?.contentOrNull ?: "",
                subjectMatrix          = matrix,
                criticalRiskCount      = risk?.get("critical_count")?.jsonPrimitive?.intOrNull ?: 0,
                moderateRiskCount      = risk?.get("moderate_count")?.jsonPrimitive?.intOrNull ?: 0,
                proficiencyTargetReach = risk?.get("proficiency_target_reach")?.jsonPrimitive?.intOrNull ?: 0,
                topPerformerName       = top?.get("name")?.jsonPrimitive?.contentOrNull ?: "",
                topPerformerDetails    = top?.get("details")?.jsonPrimitive?.contentOrNull ?: "",
                recentProgress         = progress
            )
        } catch (e: Exception) {
            AppLogger.e("ClassPerformanceVM", "parseClassPerformance failed: ${e.message}")
            ClassPerformanceState(errorMessage = "Could not parse server response")
        }
    }

    private fun parseGrade(el: JsonElement): GradeDistribution? {
        return try {
            val o = el.jsonObject
            val grade = o["grade"]?.jsonPrimitive?.contentOrNull ?: return null
            GradeDistribution(
                grade      = grade,
                percentage = o["percentage"]?.jsonPrimitive?.intOrNull ?: 0,
                value      = o["value"]?.jsonPrimitive?.floatOrNull ?: 0f
            )
        } catch (_: Exception) { null }
    }

    private fun parseSubject(el: JsonElement): SubjectMatrixItem? {
        return try {
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null
            SubjectMatrixItem(
                name       = name,
                percentage = o["percentage"]?.jsonPrimitive?.intOrNull ?: 0,
                trend      = o["trend"]?.jsonPrimitive?.contentOrNull ?: "flat"
            )
        } catch (_: Exception) { null }
    }

    private fun parseProgress(el: JsonElement): ProgressMonitoringItem? {
        return try {
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null
            ProgressMonitoringItem(
                name       = name,
                initials   = o["initials"]?.jsonPrimitive?.contentOrNull ?: "",
                math       = o["math"]?.jsonPrimitive?.contentOrNull ?: "",
                science    = o["science"]?.jsonPrimitive?.contentOrNull ?: "",
                literature = o["literature"]?.jsonPrimitive?.contentOrNull ?: "",
                attendance = o["attendance"]?.jsonPrimitive?.contentOrNull ?: "",
                status     = o["status"]?.jsonPrimitive?.contentOrNull ?: ""
            )
        } catch (_: Exception) { null }
    }
}
