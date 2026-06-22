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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class DepartmentProgress(
    val name: String,
    val progress: Float,
    val trend: String,
    val isDelayed: Boolean = false
)

data class LaggingAlert(
    val id: String,
    val subject: String,
    val className: String,
    val delayPercentage: Int,
    val instructor: String,
    val isCritical: Boolean = false
)

data class AcademicMilestone(
    val id: String,
    val month: String,
    val day: String,
    val title: String,
    val description: String,
    val isVerified: Boolean = false
)

data class SyllabusCoverageState(
    val departmentStats: List<Float> = emptyList(),
    val departmentLabels: List<String> = emptyList(),
    val departmentProgress: List<DepartmentProgress> = emptyList(),
    val alerts: List<LaggingAlert> = emptyList(),
    val milestones: List<AcademicMilestone> = emptyList(),
    val overallPercentage: Int = 0,
    val overallTrend: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SyllabusCoverageViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SyllabusCoverageState())
    val state: StateFlow<SyllabusCoverageState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false); return@launch
            }
            when (val result = analyticsRepository.getSyllabusCoverage(token)) {
                is NetworkResult.Success -> {
                    _state.value = parseSyllabus(result.data.data).copy(isLoading = false)
                }
                is NetworkResult.Error -> {
                    AppLogger.e("SyllabusCoverageVM", "getSyllabusCoverage error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("SyllabusCoverageVM", "getSyllabusCoverage connection error")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        errorMessage = "Connection error. Check your internet."
                    )
                }
            }
        }
    }

    private fun parseSyllabus(element: JsonElement?): SyllabusCoverageState {
        val obj = (element as? JsonObject) ?: return SyllabusCoverageState()
        return try {
            val overall = obj["overall"] as? JsonObject
            val bySubject = (obj["by_subject"] as? JsonArray) ?: JsonArray(emptyList())

            // departmentStats + labels: keep them index-aligned by iterating the
            // same source list once and only keeping entries that have both a
            // percentage and a name.
            val statPairs = bySubject.mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    val pct = o["percentage"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                    name to (pct.coerceIn(0, 100) / 100f)
                }.getOrNull()
            }
            val stats = statPairs.map { it.second }
            val statLabels = statPairs.map { it.first }

            // departmentProgress: 1:1 with by_subject, semantic trend / delay
            // derived from behind_by_days when no explicit `trend` field is set.
            val progress = bySubject.mapNotNull { el ->
                runCatching {
                    val o = el.jsonObject
                    val name = o["name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                    val pct = (o["percentage"]?.jsonPrimitive?.intOrNull ?: 0).coerceIn(0, 100)
                    val behind = o["behind_by_days"]?.jsonPrimitive?.intOrNull ?: 0
                    val explicitTrend = o["trend"]?.jsonPrimitive?.contentOrNull
                    DepartmentProgress(
                        name     = name,
                        progress = pct / 100f,
                        trend    = explicitTrend ?: if (behind > 0) "Delayed by $behind days" else "On Target",
                        isDelayed = behind > 0
                    )
                }.getOrNull()
            }

            // Optional CMS keys — server may add these later without a deploy.
            val alerts = (obj["alerts"] as? JsonArray)?.mapNotNull { parseAlert(it) } ?: emptyList()
            val milestones = (obj["milestones"] as? JsonArray)?.mapNotNull { parseMilestone(it) } ?: emptyList()

            SyllabusCoverageState(
                departmentStats    = stats,
                departmentLabels   = statLabels,
                departmentProgress = progress,
                alerts             = alerts,
                milestones         = milestones,
                overallPercentage  = overall?.get("percentage")?.jsonPrimitive?.intOrNull ?: 0,
                overallTrend       = overall?.get("trend")?.jsonPrimitive?.contentOrNull ?: ""
            )
        } catch (e: Exception) {
            AppLogger.e("SyllabusCoverageVM", "parseSyllabus failed: ${e.message}")
            SyllabusCoverageState(errorMessage = "Could not parse server response")
        }
    }

    private fun parseAlert(el: JsonElement): LaggingAlert? {
        return try {
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
            LaggingAlert(
                id               = id,
                subject          = o["subject"]?.jsonPrimitive?.contentOrNull ?: "",
                className        = o["class_name"]?.jsonPrimitive?.contentOrNull
                    ?: o["class"]?.jsonPrimitive?.contentOrNull ?: "",
                delayPercentage  = o["delay_percentage"]?.jsonPrimitive?.intOrNull ?: 0,
                instructor       = o["instructor"]?.jsonPrimitive?.contentOrNull ?: "",
                isCritical       = o["is_critical"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        } catch (_: Exception) { null }
    }

    private fun parseMilestone(el: JsonElement): AcademicMilestone? {
        return try {
            val o = el.jsonObject
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
            AcademicMilestone(
                id          = id,
                month       = o["month"]?.jsonPrimitive?.contentOrNull ?: "",
                day         = o["day"]?.jsonPrimitive?.contentOrNull ?: "",
                title       = o["title"]?.jsonPrimitive?.contentOrNull ?: "",
                description = o["description"]?.jsonPrimitive?.contentOrNull ?: "",
                isVerified  = o["is_verified"]?.jsonPrimitive?.booleanOrNull ?: false
            )
        } catch (_: Exception) { null }
    }
}
