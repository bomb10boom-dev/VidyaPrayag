package com.littlebridge.enrollplus.ui.v2.screens.school

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.presentation.ClassPerformanceState
import com.littlebridge.enrollplus.feature.admin.presentation.ClassPerformanceViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.GradeDistribution
import com.littlebridge.enrollplus.feature.admin.presentation.ProgressMonitoringItem
import com.littlebridge.enrollplus.feature.admin.presentation.SubjectMatrixItem
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * ClassPerformanceScreenV2 — class-level analytics overlay.
 *
 * Wired to [ClassPerformanceViewModel] (`GET /api/v1/school/analytics/class`).
 *
 * Layout:
 *   • 3-tile KPI row: avg proficiency / active students / median grade
 *   • Grade distribution VCard with VProgressBar rows (one per grade)
 *   • Subject matrix VCard (subject + percentage + trend VBadge)
 *   • Risk summary VCard (critical/moderate counts + target reach VBadge)
 *   • Top performer VCard
 *   • Recent progress monitoring rows (student initials avatar + status VBadge)
 *
 * Three states via [VStateHost] (LAW 3).
 */
@Composable
fun ClassPerformanceScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClassPerformanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Class Performance", onBack = onBack)
        ClassPerformanceContent(
            state = state,
            onRetry = { viewModel.load() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ClassPerformanceContent(
    state: ClassPerformanceState,
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
            isEmpty = state.gradeDistribution.isEmpty() &&
                state.subjectMatrix.isEmpty() &&
                state.recentProgress.isEmpty() &&
                state.avgProficiency.isBlank(),
            emptyTitle = "No data yet",
            emptyBody = "Class-level analytics will appear here once teachers post marks and attendance.",
            emptyIcon = VIcons.TrendingUp,
            onRetry = onRetry,
        ) {
            // KPI row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Kpi(label = "Avg proficiency", value = state.avgProficiency.ifBlank { "—" }) }
                Box(Modifier.weight(1f)) { Kpi(label = "Active students", value = state.activeStudents.toString()) }
                Box(Modifier.weight(1f)) { Kpi(label = "Median grade", value = state.medianGrade.ifBlank { "—" }) }
            }

            // Grade distribution
            if (state.gradeDistribution.isNotEmpty()) {
                VSectionHeader(title = "GRADE DISTRIBUTION")
                VCard {
                    state.gradeDistribution.forEachIndexed { i, g ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        GradeRow(g)
                    }
                }
            }

            // Subject matrix
            if (state.subjectMatrix.isNotEmpty()) {
                VSectionHeader(title = "SUBJECT MATRIX")
                VCard {
                    state.subjectMatrix.forEachIndexed { i, s ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                        SubjectRow(s)
                    }
                }
            }

            // Risk summary
            VSectionHeader(title = "EARLY WARNING")
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { RiskTile(label = "Critical", value = state.criticalRiskCount.toString(), tone = VBadgeTone.Danger) }
                    Box(Modifier.weight(1f)) { RiskTile(label = "Moderate", value = state.moderateRiskCount.toString(), tone = VBadgeTone.Warning) }
                    Box(Modifier.weight(1f)) { RiskTile(label = "On target", value = "${state.proficiencyTargetReach}%", tone = VBadgeTone.Success) }
                }
            }

            // Top performer
            if (state.topPerformerName.isNotBlank()) {
                VSectionHeader(title = "TOP PERFORMER")
                VCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VBadge(text = "★ 1ST", tone = VBadgeTone.Warning)
                        Column(Modifier.weight(1f)) {
                            Text(state.topPerformerName, style = VTheme.type.bodyStrong.colored(c.ink))
                            if (state.topPerformerDetails.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(state.topPerformerDetails, style = VTheme.type.caption.colored(c.ink3))
                            }
                        }
                    }
                }
            }

            // Recent progress monitoring
            if (state.recentProgress.isNotEmpty()) {
                VSectionHeader(title = "PROGRESS MONITORING")
                state.recentProgress.forEach { p -> ProgressRow(p) }
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String) {
    val c = VTheme.colors
    VCard {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(4.dp))
        Text(value, style = VTheme.type.dataLg.colored(c.ink))
    }
}

@Composable
private fun RiskTile(label: String, value: String, tone: VBadgeTone) {
    val c = VTheme.colors
    Column {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(4.dp))
        VBadge(text = value, tone = tone)
    }
}

@Composable
private fun GradeRow(g: GradeDistribution) {
    val c = VTheme.colors
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(g.grade, style = VTheme.type.bodyStrong.colored(c.ink))
            Text("${g.percentage}%", style = VTheme.type.dataSm.colored(c.ink2))
        }
        Spacer(Modifier.height(6.dp))
        VProgressBar(value = (g.value * 100f).coerceIn(0f, 100f))
    }
}

@Composable
private fun SubjectRow(s: SubjectMatrixItem) {
    val c = VTheme.colors
    val (trendText, trendTone) = when (s.trend.lowercase()) {
        "up" -> "▲ Up" to VBadgeTone.Success
        "down" -> "▼ Down" to VBadgeTone.Danger
        else -> "● Flat" to VBadgeTone.Neutral
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(s.name, style = VTheme.type.bodyStrong.colored(c.ink), modifier = Modifier.weight(1f))
        Text("${s.percentage}%", style = VTheme.type.dataSm.colored(c.ink2))
        VBadge(text = trendText, tone = trendTone)
    }
}

@Composable
private fun ProgressRow(p: ProgressMonitoringItem) {
    val c = VTheme.colors
    val statusTone = when (p.status.uppercase()) {
        "EXCELLING" -> VBadgeTone.Success
        "PEWS ALERT" -> VBadgeTone.Danger
        "CONSISTENT" -> VBadgeTone.Arctic
        else -> VBadgeTone.Neutral
    }
    VCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.cream)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(p.initials.take(2).uppercase(), style = VTheme.type.dataSm.colored(c.ink))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        p.name,
                        style = VTheme.type.bodyStrong.colored(c.ink),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VBadge(text = p.status, tone = statusTone)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Math ${p.math} · Sci ${p.science} · Lit ${p.literature}",
                    style = VTheme.type.dataSm.colored(c.ink2),
                )
                Spacer(Modifier.height(2.dp))
                Text("Attendance ${p.attendance}", style = VTheme.type.caption.colored(c.ink3))
            }
        }
    }
}
