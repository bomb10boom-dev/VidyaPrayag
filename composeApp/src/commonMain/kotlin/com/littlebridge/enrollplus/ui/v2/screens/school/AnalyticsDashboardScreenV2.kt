package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.presentation.AnalyticsCardData
import com.littlebridge.enrollplus.feature.admin.presentation.AnalyticsDashboardState
import com.littlebridge.enrollplus.feature.admin.presentation.AnalyticsDashboardViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.InsightItem
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * AnalyticsDashboardScreenV2 — top-level analytics overlay.
 *
 * Wired to [AnalyticsDashboardViewModel] (`GET /api/v1/school/analytics/overview`).
 *
 * Layout:
 *   • Performance trend VCard with growth VBadge + a minimal hand-drawn
 *     line chart (no third-party chart lib — pure Canvas in teal).
 *   • 2×N grid of analytics cards (title + value in dataLg + subValue +
 *     optional trend VBadge).
 *   • Insights list (icon dot + title + description).
 *
 * Three states via [VStateHost] (LAW 3).
 */
@Composable
fun AnalyticsDashboardScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AnalyticsDashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Analytics", onBack = onBack)
        AnalyticsContent(
            state = state,
            onRetry = viewModel::loadOverview,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AnalyticsContent(
    state: AnalyticsDashboardState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            isEmpty = state.performanceTrend.isEmpty() &&
                state.cards.isEmpty() &&
                state.insights.isEmpty(),
            emptyTitle = "No analytics yet",
            emptyBody = "The overview will populate once the analytics rollup endpoint has data.",
            emptyIcon = VIcons.TrendingUp,
            onRetry = onRetry,
        ) {
            // Performance trend
            if (state.performanceTrend.isNotEmpty()) {
                VCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("Performance trend", style = VTheme.type.label.colored(c.ink3))
                            Spacer(Modifier.height(4.dp))
                            Text(state.currentGrowth, style = VTheme.type.dataLg.colored(c.ink))
                        }
                        VBadge(text = "OVERVIEW", tone = VBadgeTone.Arctic)
                    }
                    Spacer(Modifier.height(12.dp))
                    TrendChart(
                        values = state.performanceTrend,
                        labels = state.trendLabels,
                    )
                }
            }

            // Cards grid (2 per row)
            if (state.cards.isNotEmpty()) {
                VSectionHeader(title = "OVERVIEW")
                val pairs = state.cards.chunked(2)
                pairs.forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { AnalyticsCard(pair[0]) }
                        if (pair.size > 1) {
                            Box(Modifier.weight(1f)) { AnalyticsCard(pair[1]) }
                        } else {
                            Box(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Insights
            if (state.insights.isNotEmpty()) {
                VSectionHeader(title = "INSIGHTS")
                state.insights.forEach { item -> InsightCard(item) }
            }
        }
    }
}

@Composable
private fun TrendChart(values: List<Float>, labels: List<String>) {
    val c = VTheme.colors
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
        ) {
            if (values.size < 2) return@Canvas
            val min = values.min()
            val max = values.max()
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (values.size - 1).coerceAtLeast(1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = stepX * i
                val y = size.height - ((v - min) / range) * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = c.teal,
                style = Stroke(width = 3f),
            )
            // dots
            values.forEachIndexed { i, v ->
                val x = stepX * i
                val y = size.height - ((v - min) / range) * size.height
                drawCircle(color = c.tealDeep, radius = 4f, center = Offset(x, y))
            }
        }
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labels.forEach { l ->
                    Text(l, style = VTheme.type.caption.colored(c.ink3))
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(card: AnalyticsCardData) {
    val c = VTheme.colors
    VCard {
        Text(card.title, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(4.dp))
        Text(card.value, style = VTheme.type.dataLg.colored(c.ink))
        if (card.subValue.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(card.subValue, style = VTheme.type.caption.colored(c.ink2))
        }
        val trend = card.trend
        if (!trend.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            VBadge(text = trend, tone = VBadgeTone.Arctic)
        }
    }
}

@Composable
private fun InsightCard(item: InsightItem) {
    val c = VTheme.colors
    VCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(item.iconColor).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(item.iconColor)),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = VTheme.type.bodyStrong.colored(c.ink))
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(item.description, style = VTheme.type.caption.colored(c.ink2))
                }
            }
        }
    }
}
