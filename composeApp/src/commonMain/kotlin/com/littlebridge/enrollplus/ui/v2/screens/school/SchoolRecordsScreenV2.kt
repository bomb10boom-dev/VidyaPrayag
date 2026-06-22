package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.AttendanceSummaryDto
import com.littlebridge.enrollplus.feature.admin.domain.model.FeeLedgerDto
import com.littlebridge.enrollplus.feature.admin.domain.model.MarksSummaryDto
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolRecordsState
import com.littlebridge.enrollplus.feature.admin.presentation.SchoolRecordsViewModel
import com.littlebridge.enrollplus.feature.admin.presentation.SyllabusCoverageState
import com.littlebridge.enrollplus.feature.admin.presentation.SyllabusCoverageViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VComingSoon
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.components.VTopTabs
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * SchoolRecordsScreenV2 — `Admin.tsx → Records`, wired to the real
 * [SyllabusCoverageViewModel] (`AnalyticsApi` → `GET /api/v1/syllabus-coverage`).
 *
 * The **Coverage** tab renders live department progress, lagging alerts and academic
 * milestones from the analytics endpoint ([SyllabusCoverageViewModel]).
 *
 * RA-52: **Attendance**, **Marks** and **Fee** now read real school-wide rollups from
 * [SchoolRecordsViewModel] (`GET /api/v1/school/{attendance/summary,marks/summary,fees/ledger}`)
 * instead of `VComingSoon` placeholders. Each rollup loads lazily on first view of its tab
 * and carries its own loading / error+retry / empty state ([VStateHost], LAW: three states).
 * Only **Documents** stays `VComingSoon` (no media-storage backend yet — LAW 6, no fabrication).
 * No MockV2 in production.
 */
@Composable
fun SchoolRecordsScreenV2(
    modifier: Modifier = Modifier,
    viewModel: SyllabusCoverageViewModel = koinViewModel(),
    recordsViewModel: SchoolRecordsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val recordsState by recordsViewModel.state.collectAsStateV2()
    SchoolRecordsContent(
        state = state,
        records = recordsState,
        onRetry = viewModel::load,
        onTabSelected = { tab ->
            when (tab) {
                "Attendance" -> recordsViewModel.ensureAttendance()
                "Marks" -> recordsViewModel.ensureMarks()
                "Fee" -> recordsViewModel.ensureFees()
            }
        },
        onRetryAttendance = recordsViewModel::loadAttendance,
        onRetryMarks = recordsViewModel::loadMarks,
        onRetryFees = recordsViewModel::loadFees,
        modifier = modifier,
    )
}

@Composable
private fun SchoolRecordsContent(
    state: SyllabusCoverageState,
    records: SchoolRecordsState,
    onRetry: () -> Unit,
    onTabSelected: (String) -> Unit,
    onRetryAttendance: () -> Unit,
    onRetryMarks: () -> Unit,
    onRetryFees: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var tab by remember { mutableStateOf("Coverage") }

    // Lazy-load the rollup behind whichever data tab is currently selected.
    LaunchedEffect(tab) { onTabSelected(tab) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(top = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Records", style = VTheme.type.h1.colored(c.ink))
        VTopTabs(
            tabs = listOf("Coverage", "Attendance", "Marks", "Fee", "Documents"),
            selected = tab,
            onSelect = { tab = it },
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                "Coverage" -> CoverageTab(state = state, onRetry = onRetry)
                "Attendance" -> AttendanceTab(ui = records.attendance, onRetry = onRetryAttendance)
                "Marks" -> MarksTab(ui = records.marks, onRetry = onRetryMarks)
                "Fee" -> FeeTab(ui = records.fees, onRetry = onRetryFees)
                "Documents" -> VComingSoon(
                    title = "Document library",
                    description = "Circulars, timetables and holiday lists will be uploadable once media storage is configured.",
                )
            }
        }
    }
}

@Composable
private fun CoverageTab(state: SyllabusCoverageState, onRetry: () -> Unit) {
    val c = VTheme.colors
    VStateHost(
        loading = state.isLoading,
        error = state.errorMessage,
        isEmpty = state.departmentProgress.isEmpty() && state.alerts.isEmpty() && state.milestones.isEmpty(),
        emptyTitle = "No coverage data yet",
        emptyBody = "Syllabus coverage will appear here once teachers start marking units complete.",
        emptyIcon = VIcons.BookOpen,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 5, withAvatar = false) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // ── Overall ───────────────────────────────────────────────────────
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    VLabel("Overall syllabus coverage")
                    if (state.overallTrend.isNotBlank()) {
                        VBadge(text = state.overallTrend, tone = VBadgeTone.Arctic)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("${state.overallPercentage}%", style = VTheme.type.dataLg.colored(c.ink))
                Spacer(Modifier.height(8.dp))
                VProgressBar(
                    value = state.overallPercentage.toFloat(),
                    tone = if (state.overallPercentage < 70) VBadgeTone.Warning else VBadgeTone.Arctic,
                )
            }

            // ── By department ─────────────────────────────────────────────────
            if (state.departmentProgress.isNotEmpty()) {
                VCard {
                    Text("By department", style = VTheme.type.h3.colored(c.ink))
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.departmentProgress.forEach { d ->
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(d.name, style = VTheme.type.body.colored(c.ink))
                                    Text("${(d.progress * 100).roundToInt()}%", style = VTheme.type.dataSm.colored(c.ink2))
                                }
                                Spacer(Modifier.height(4.dp))
                                VProgressBar(
                                    value = d.progress * 100f,
                                    tone = if (d.isDelayed) VBadgeTone.Danger else VBadgeTone.Arctic,
                                )
                                if (d.trend.isNotBlank()) {
                                    Text(d.trend, style = VTheme.type.label.colored(if (d.isDelayed) c.dangerInk else c.ink3))
                                }
                            }
                        }
                    }
                }
            }

            // ── Lagging alerts ────────────────────────────────────────────────
            if (state.alerts.isNotEmpty()) {
                Column {
                    Text("Lagging classes", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.alerts.forEach { a ->
                            VCard {
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(VIcons.AlertCircle, contentDescription = null, tint = if (a.isCritical) c.dangerInk else c.warningInk, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("${a.subject} • ${a.className}", style = VTheme.type.bodyStrong.colored(c.ink))
                                        if (a.instructor.isNotBlank()) {
                                            Text(a.instructor, style = VTheme.type.caption.colored(c.ink2))
                                        }
                                    }
                                    VBadge(
                                        text = "${a.delayPercentage}% behind",
                                        tone = if (a.isCritical) VBadgeTone.Danger else VBadgeTone.Warning,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Academic milestones ───────────────────────────────────────────
            if (state.milestones.isNotEmpty()) {
                Column {
                    Text("Academic milestones", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.milestones.forEach { m ->
                            VCard {
                                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(m.month, style = VTheme.type.label.colored(c.ink3))
                                        Text(m.day, style = VTheme.type.dataLg.colored(c.ink))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(m.title, style = VTheme.type.bodyStrong.colored(c.ink))
                                        if (m.description.isNotBlank()) {
                                            Text(m.description, style = VTheme.type.caption.colored(c.ink2))
                                        }
                                    }
                                    if (m.isVerified) {
                                        Icon(VIcons.Check, contentDescription = "Verified", tint = c.successInk, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────── RA-52: Attendance ─────────────────────────────

@Composable
private fun AttendanceTab(
    ui: com.littlebridge.enrollplus.feature.admin.presentation.AttendanceSummaryUi,
    onRetry: () -> Unit,
) {
    val c = VTheme.colors
    val data: AttendanceSummaryDto? = ui.data
    VStateHost(
        loading = ui.isLoading,
        error = ui.error,
        isEmpty = ui.loaded && (data == null || data.total == 0),
        emptyTitle = "No attendance marked yet",
        emptyBody = "School-wide attendance will roll up here once teachers start marking the daily register.",
        emptyIcon = VIcons.Calendar,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 4, withAvatar = false) },
    ) {
        if (data == null) return@VStateHost
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    VLabel("Latest register" + (data.latestDate?.let { " • $it" } ?: ""))
                    VBadge(text = "${data.rate}% present", tone = if (data.rate < 75) VBadgeTone.Warning else VBadgeTone.Success)
                }
                Spacer(Modifier.height(8.dp))
                VProgressBar(
                    value = data.rate.toFloat(),
                    tone = if (data.rate < 75) VBadgeTone.Warning else VBadgeTone.Success,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell(label = "Present", value = data.present.toString(), tint = c.successInk)
                    StatCell(label = "Absent", value = data.absent.toString(), tint = c.dangerInk)
                    StatCell(label = "Late", value = data.late.toString(), tint = c.warningInk)
                    StatCell(label = "Total", value = data.total.toString(), tint = c.ink)
                }
            }

            if (data.byClass.isNotEmpty()) {
                VCard {
                    Text("By class", style = VTheme.type.h3.colored(c.ink))
                    Spacer(Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        data.byClass.forEach { row ->
                            Column {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(row.grade, style = VTheme.type.body.colored(c.ink))
                                    Text("${row.present + row.late}/${row.total} • ${row.rate}%", style = VTheme.type.dataSm.colored(c.ink2))
                                }
                                Spacer(Modifier.height(4.dp))
                                VProgressBar(
                                    value = row.rate.toFloat(),
                                    tone = if (row.rate < 75) VBadgeTone.Warning else VBadgeTone.Success,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── RA-52: Marks ────────────────────────────────

@Composable
private fun MarksTab(
    ui: com.littlebridge.enrollplus.feature.admin.presentation.MarksSummaryUi,
    onRetry: () -> Unit,
) {
    val c = VTheme.colors
    val data: MarksSummaryDto? = ui.data
    VStateHost(
        loading = ui.isLoading,
        error = ui.error,
        isEmpty = ui.loaded && (data == null || data.assessments.isEmpty()),
        emptyTitle = "No assessments yet",
        emptyBody = "Exam averages roll up here once teachers create assessments and enter marks.",
        emptyIcon = VIcons.BookOpen,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 4, withAvatar = false) },
    ) {
        if (data == null) return@VStateHost
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    VLabel("Overall average")
                    VBadge(text = "${data.assessmentCount} assessment${if (data.assessmentCount == 1) "" else "s"}", tone = VBadgeTone.Arctic)
                }
                Spacer(Modifier.height(8.dp))
                Text("${data.overallAveragePct}%", style = VTheme.type.dataLg.colored(c.ink))
                Spacer(Modifier.height(8.dp))
                VProgressBar(
                    value = data.overallAveragePct.toFloat(),
                    tone = if (data.overallAveragePct < 40) VBadgeTone.Danger else if (data.overallAveragePct < 60) VBadgeTone.Warning else VBadgeTone.Success,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.assessments.forEach { a ->
                    val pct = if (a.maxMarks > 0) ((a.average / a.maxMarks) * 100).roundToInt() else 0
                    VCard {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("${a.subject} • ${a.assessmentName}", style = VTheme.type.bodyStrong.colored(c.ink))
                                Text("${a.className}${a.examDate?.let { " • $it" } ?: ""}", style = VTheme.type.caption.colored(c.ink2))
                            }
                            VBadge(
                                text = if (a.isPublished) "Published" else "Draft",
                                tone = if (a.isPublished) VBadgeTone.Success else VBadgeTone.Neutral,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Avg ${a.average} / ${a.maxMarks}", style = VTheme.type.dataSm.colored(c.ink2))
                            Text(if (a.gradedCount > 0) "$pct% • ${a.gradedCount} graded" else "Not graded yet", style = VTheme.type.caption.colored(c.ink3))
                        }
                        Spacer(Modifier.height(4.dp))
                        VProgressBar(
                            value = pct.toFloat(),
                            tone = if (pct < 40) VBadgeTone.Danger else if (pct < 60) VBadgeTone.Warning else VBadgeTone.Success,
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────── RA-52: Fee ─────────────────────────────────

@Composable
private fun FeeTab(
    ui: com.littlebridge.enrollplus.feature.admin.presentation.FeeLedgerUi,
    onRetry: () -> Unit,
) {
    val c = VTheme.colors
    val data: FeeLedgerDto? = ui.data
    val hasAny = data != null && (data.paidCount + data.dueCount + data.overdueCount) > 0
    VStateHost(
        loading = ui.isLoading,
        error = ui.error,
        isEmpty = ui.loaded && !hasAny,
        emptyTitle = "No fee records yet",
        emptyBody = "Collections, dues and overdue reminders surface here once fee records are raised for this school.",
        emptyIcon = VIcons.Wallet,
        onRetry = onRetry,
        skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonList(rows = 4, withAvatar = false) },
    ) {
        if (data == null) return@VStateHost
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VCard {
                VLabel("Ledger (${data.currency})")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell(label = "Paid", value = formatMoney(data.paidTotal), sub = "${data.paidCount}", tint = c.successInk)
                    StatCell(label = "Due", value = formatMoney(data.dueTotal), sub = "${data.dueCount}", tint = c.warningInk)
                    StatCell(label = "Overdue", value = formatMoney(data.overdueTotal), sub = "${data.overdueCount}", tint = c.dangerInk)
                }
            }

            if (data.recent.isNotEmpty()) {
                Column {
                    Text("Recent", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.recent.forEach { f ->
                            VCard {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(f.title, style = VTheme.type.bodyStrong.colored(c.ink))
                                        Text("${f.category}${f.dueDate?.let { " • due $it" } ?: ""}", style = VTheme.type.caption.colored(c.ink2))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${f.currency} ${formatMoney(f.amount)}", style = VTheme.type.bodyStrong.colored(c.ink))
                                        Spacer(Modifier.height(4.dp))
                                        VBadge(
                                            text = f.status,
                                            tone = when (f.status.uppercase()) {
                                                "PAID" -> VBadgeTone.Success
                                                "OVERDUE" -> VBadgeTone.Danger
                                                else -> VBadgeTone.Warning
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A compact label-over-value stat cell used by the Attendance & Fee rollups. */
@Composable
private fun StatCell(label: String, value: String, tint: androidx.compose.ui.graphics.Color, sub: String? = null) {
    val c = VTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = VTheme.type.dataLg.colored(tint))
        Text(label, style = VTheme.type.label.colored(c.ink3))
        if (sub != null) {
            Text(sub, style = VTheme.type.caption.colored(c.ink2))
        }
    }
}

/** Group a Double into a plain integer-rupees string (no locale dep in commonMain). */
private fun formatMoney(value: Double): String {
    val whole = value.toLong()
    val s = whole.toString()
    val sb = StringBuilder()
    val len = s.length
    for (i in 0 until len) {
        if (i > 0 && (len - i) % 3 == 0) sb.append(',')
        sb.append(s[i])
    }
    return sb.toString()
}
