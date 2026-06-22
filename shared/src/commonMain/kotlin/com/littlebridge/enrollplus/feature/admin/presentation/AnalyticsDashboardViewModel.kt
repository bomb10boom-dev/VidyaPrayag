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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// ---------------------------------------------------------------------------
// UI data classes — consumed by AnalyticsDashboardScreen.kt
// These are intentionally plain data classes (no @Serializable) because they
// live in the presentation layer and are never serialised over the wire.
// ---------------------------------------------------------------------------

data class AnalyticsCardData(
    val title: String,
    val value: String,
    val subValue: String,
    val iconUrl: String,
    val trend: String? = null
)

data class InsightItem(
    val title: String,
    val description: String,
    val iconName: String,
    val iconColor: Long        // 0xAARRGGBB hex color as Long, e.g. 0xFF4CAF50
)

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

data class AnalyticsDashboardState(
    val performanceTrend: List<Float> = emptyList(),
    val trendLabels: List<String> = emptyList(),
    val currentGrowth: String = "0%",
    val cards: List<AnalyticsCardData> = emptyList(),
    val insights: List<InsightItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class AnalyticsDashboardViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val preferenceRepository: PreferenceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsDashboardState())
    val state: StateFlow<AnalyticsDashboardState> = _state.asStateFlow()

    init { loadOverview() }

    fun loadOverview() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val token = preferenceRepository.getUserToken().first()
            if (token.isNullOrBlank()) {
                _state.value = _state.value.copy(isLoading = false)
                return@launch
            }
            when (val result = analyticsRepository.getOverview(token)) {
                is NetworkResult.Success -> {
                    val data = result.data.data
                    _state.value = _state.value.copy(
                        performanceTrend = data?.performanceTrend?.map { it.toFloat() } ?: emptyList(),
                        trendLabels      = data?.trendLabels ?: emptyList(),
                        currentGrowth    = data?.currentGrowth ?: "0%",
                        cards            = data?.cards?.mapNotNull { parseCard(it) } ?: emptyList(),
                        insights         = data?.insights?.mapNotNull { parseInsight(it) } ?: emptyList(),
                        isLoading        = false
                    )
                }
                is NetworkResult.Error -> {
                    AppLogger.e("AnalyticsDashboardVM", "getOverview error: ${result.message}")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = result.message)
                }
                is NetworkResult.ConnectionError -> {
                    AppLogger.e("AnalyticsDashboardVM", "getOverview connection error")
                    _state.value = _state.value.copy(isLoading = false, errorMessage = "Connection error. Check your internet.")
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // JSON parsing helpers — JsonElement is only touched here, inside `shared`,
    // where kotlinx-serialization-json is on the classpath.
    // composeApp only ever sees the typed data classes above.
    // -----------------------------------------------------------------------

    private fun parseCard(element: JsonElement): AnalyticsCardData? {
        return try {
            val obj   = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
            AnalyticsCardData(
                title    = title,
                value    = obj["value"]?.jsonPrimitive?.contentOrNull    ?: "",
                subValue = obj["sub_value"]?.jsonPrimitive?.contentOrNull ?: "",
                iconUrl  = obj["icon_url"]?.jsonPrimitive?.contentOrNull  ?: "",
                trend    = obj["trend"]?.jsonPrimitive?.contentOrNull
            )
        } catch (e: Exception) {
            AppLogger.e("AnalyticsDashboardVM", "parseCard failed: ${e.message}")
            null
        }
    }

    private fun parseInsight(element: JsonElement): InsightItem? {
        return try {
            val obj   = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return null
            InsightItem(
                title       = title,
                description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                iconName    = obj["icon_name"]?.jsonPrimitive?.contentOrNull   ?: "insights",
                iconColor   = obj["icon_color"]?.jsonPrimitive?.longOrNull     ?: 0xFF6200EE
            )
        } catch (e: Exception) {
            AppLogger.e("AnalyticsDashboardVM", "parseInsight failed: ${e.message}")
            null
        }
    }
}
