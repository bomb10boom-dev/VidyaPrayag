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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class RiskStudent(
    val id: String,
    val name: String,
    val imageUrl: String,
    val retentionRisk: Int,
    val masteryTrend: String,
    val riskLevel: String // "Critical", "Medium", "Low"
)

data class SubjectEngagement(
    val name: String,
    val percentage: Float,
    val status: String? = null
)

data class StudentAnalyticsState(
    val dailyVolatility: List<Float> = emptyList(),
    val criticalRiskCount: Int = 0,
    val mediumRiskCount: Int = 0,
    val lowRiskCount: Int = 0,
    val atRiskStudents: List<RiskStudent> = emptyList(),
    val subjectEngagements: List<SubjectEngagement> = emptyList(),
    val cohortComparison: List<Float> = emptyList(), // per-grade averages
    val cohortLabels: List<String> = emptyList(),     // grade labels matching cohortComparison
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class StudentAnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StudentAnalyticsState())
    val state: StateFlow<StudentAnalyticsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false); return@launch
            }
            when (val result = analyticsRepository.getStudentCohort(token)) {
                is NetworkResult.Success -> {
                    _state.value = parseCohort(result.data.data).copy(isLoading = false)
                }
                is NetworkResult.Error -> {
                    AppLogger.e("StudentAnalyticsVM", "getStudentCohort error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("StudentAnalyticsVM", "getStudentCohort connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    private fun parseCohort(element: JsonElement?): StudentAnalyticsState {
        val obj = (element as? JsonObject) ?: return StudentAnalyticsState()
        return try {
            val volatility = (obj["daily_volatility"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.floatOrNull } ?: emptyList()
            val risk = obj["risk"] as? JsonObject
            val atRisk = (obj["at_risk_students"] as? JsonArray)?.mapNotNull { parseRisk(it) } ?: emptyList()
            val engagements = (obj["subject_engagements"] as? JsonArray)?.mapNotNull { parseEngagement(it) } ?: emptyList()
            val cohort = (obj["cohort_comparison"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.floatOrNull } ?: emptyList()
            val cohortLabels = (obj["cohort_labels"] as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

            StudentAnalyticsState(
                dailyVolatility    = volatility,
                criticalRiskCount  = risk?.get("critical_count")?.jsonPrimitive?.intOrNull ?: 0,
                mediumRiskCount    = risk?.get("medium_count")?.jsonPrimitive?.intOrNull ?: 0,
                lowRiskCount       = risk?.get("low_count")?.jsonPrimitive?.intOrNull ?: 0,
                atRiskStudents     = atRisk,
                subjectEngagements = engagements,
                cohortComparison   = cohort,
                cohortLabels       = cohortLabels
            )
        } catch (e: Exception) {
            AppLogger.e("StudentAnalyticsVM", "parseCohort failed: ${e.message}")
            StudentAnalyticsState(errorMessage = "Could not parse server response")
        }
    }

    private fun parseRisk(el: JsonElement): RiskStudent? {
        return try {
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
            RiskStudent(
                id            = id,
                name          = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
                imageUrl      = o["image_url"]?.jsonPrimitive?.contentOrNull ?: "",
                retentionRisk = o["retention_risk"]?.jsonPrimitive?.intOrNull ?: 0,
                masteryTrend  = o["mastery_trend"]?.jsonPrimitive?.contentOrNull ?: "",
                riskLevel     = o["risk_level"]?.jsonPrimitive?.contentOrNull ?: "Low"
            )
        } catch (_: Exception) { null }
    }

    private fun parseEngagement(el: JsonElement): SubjectEngagement? {
        return try {
            val o = el.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return null
            SubjectEngagement(
                name       = name,
                percentage = o["percentage"]?.jsonPrimitive?.floatOrNull ?: 0f,
                status     = o["status"]?.jsonPrimitive?.contentOrNull
            )
        } catch (_: Exception) { null }
    }
}
