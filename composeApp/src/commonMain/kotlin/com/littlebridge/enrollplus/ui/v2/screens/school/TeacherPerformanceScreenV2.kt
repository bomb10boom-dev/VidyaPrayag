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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.presentation.DeptEfficiency
import com.littlebridge.enrollplus.feature.admin.presentation.FacultyAccountability
import com.littlebridge.enrollplus.feature.admin.presentation.StarTeacher
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherPerformanceState
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherPerformanceViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
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
 * TeacherPerformanceScreenV2 — faculty-level analytics overlay.
 *
 * Wired to [TeacherPerformanceViewModel] (`GET /api/v1/school/analytics/teachers`).
 *
 * Layout:
 *   • Aggregate compliance KPI VCard with trend VBadge
 *   • Star Faculty leaderboard VCard (rank + avatar + name/dept + score)
 *   • Accountability matrix VCard (name + dept + compliance% + update delay
 *     + risk correlation pill)
 *   • Department efficiencies VCard with VProgressBar per department
 *
 * Three states via [VStateHost] (LAW 3).
 */
@Composable
fun TeacherPerformanceScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherPerformanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Teacher Performance", onBack = onBack)
        TeacherPerformanceContent(
            state = state,
            onRetry = viewModel::load,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun TeacherPerformanceContent(
    state: TeacherPerformanceState,
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
            isEmpty = state.starFaculty.isEmpty() &&
                state.accountabilityMatrix.isEmpty() &&
                state.deptEfficiencies.isEmpty() &&
                state.aggregateCompliance.isBlank(),
            emptyTitle = "No data yet",
            emptyBody = "Teacher analytics will appear here once faculty start posting attendance and marks.",
            emptyIcon = VIcons.Users,
            onRetry = onRetry,
        ) {
            // Aggregate compliance
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Aggregate compliance", style = VTheme.type.label.colored(c.ink3))
                        Spacer(Modifier.height(4.dp))
                        Text(state.aggregateCompliance.ifBlank { "—" }, style = VTheme.type.dataLg.colored(c.ink))
                    }
                    if (state.complianceTrend.isNotBlank()) {
                        VBadge(text = state.complianceTrend, tone = VBadgeTone.Arctic)
                    }
                }
            }

            // Star Faculty
            if (state.starFaculty.isNotEmpty()) {
                VSectionHeader(title = "STAR FACULTY")
                VCard {
                    state.starFaculty.forEachIndexed { i, t ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                        StarRow(t)
                    }
                }
            }

            // Accountability matrix
            if (state.accountabilityMatrix.isNotEmpty()) {
                VSectionHeader(title = "ACCOUNTABILITY MATRIX")
                state.accountabilityMatrix.forEach { f -> AccountabilityCard(f) }
            }

            // Department efficiencies
            if (state.deptEfficiencies.isNotEmpty()) {
                VSectionHeader(title = "DEPARTMENT EFFICIENCY")
                VCard {
                    state.deptEfficiencies.forEachIndexed { i, d ->
                        if (i > 0) Spacer(Modifier.height(10.dp))
                        DeptRow(d)
                    }
                }
            }
        }
    }
}

@Composable
private fun StarRow(t: StarTeacher) {
    val c = VTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VBadge(text = "#${t.rank}", tone = if (t.rank == 1) VBadgeTone.Warning else VBadgeTone.Arctic)
        VAvatar(name = t.name.ifBlank { "?" }, src = t.imageUrl.ifBlank { null }, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(t.name, style = VTheme.type.bodyStrong.colored(c.ink))
            Spacer(Modifier.height(2.dp))
            Text(t.department, style = VTheme.type.caption.colored(c.ink3))
        }
        Text(
            t.score.toString().take(5),
            style = VTheme.type.dataSm.colored(c.ink2),
        )
    }
}

@Composable
private fun AccountabilityCard(f: FacultyAccountability) {
    val c = VTheme.colors
    val riskTone = when (f.riskCorrelation.lowercase()) {
        "high risk" -> VBadgeTone.Danger
        "watching" -> VBadgeTone.Warning
        "stable" -> VBadgeTone.Success
        else -> VBadgeTone.Neutral
    }
    VCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VAvatar(name = f.name.ifBlank { f.initials.ifBlank { "?" } }, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        f.name,
                        style = VTheme.type.bodyStrong.colored(c.ink),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VBadge(text = f.riskCorrelation, tone = riskTone)
                }
                Spacer(Modifier.height(2.dp))
                Text(f.department, style = VTheme.type.caption.colored(c.ink3))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { MiniStat(label = "Compliance", value = "${f.complianceScore}%") }
            Box(Modifier.weight(1f)) { MiniStat(label = "Delay", value = f.avgUpdateDelay) }
            Box(Modifier.weight(1f)) { MiniStat(label = "Avg mark", value = f.studentAvgMark) }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    val c = VTheme.colors
    Column {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(2.dp))
        Text(value, style = VTheme.type.dataSm.colored(c.ink))
    }
}

@Composable
private fun DeptRow(d: DeptEfficiency) {
    val c = VTheme.colors
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(d.name, style = VTheme.type.bodyStrong.colored(c.ink))
            Text("${d.percentage}%", style = VTheme.type.dataSm.colored(c.ink2))
        }
        Spacer(Modifier.height(6.dp))
        VProgressBar(value = d.percentage.toFloat().coerceIn(0f, 100f))
    }
}
