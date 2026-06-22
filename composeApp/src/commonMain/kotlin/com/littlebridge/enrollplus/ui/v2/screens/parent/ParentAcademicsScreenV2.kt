package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.ParentAcademicsState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentAcademicsViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.TrackProgressState
import com.littlebridge.enrollplus.feature.parent.presentation.TrackProgressViewModel
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

/**
 * ParentAcademicsScreenV2 — a faithful copy of `Parent.tsx → Academics`.
 *
 * Top tabs: Overview · Attendance · Marks · Syllabus · Report. **Overview is wired to the real
 * [TrackProgressViewModel]** (`shared/`) → `GET /api/v1/parent/track-progress` and renders the
 * child's academic competencies + emotional-intelligence radar from genuine backend data.
 *
 * RA-43 + RA-56: Attendance / Marks / Syllabus are now wired to REAL child-scoped endpoints
 * (`GET /api/v1/parent/child/{id}/{attendance,marks,syllabus}`) via [ParentAcademicsViewModel], and a
 * child chip selector at the top switches which child every tab reads (RA-56). Each tab carries the
 * three states (loading / error / empty) through [VStateHost]. Report stays an honest preview.
 */
@Composable
fun ParentAcademicsScreenV2(
    modifier: Modifier = Modifier,
    onOpenLeave: () -> Unit = {},
    viewModel: TrackProgressViewModel = koinViewModel(),
    academicsViewModel: ParentAcademicsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val academics by academicsViewModel.state.collectAsStateV2()
    ParentAcademicsContent(
        state = state,
        academics = academics,
        onLoadAttendance = { academicsViewModel.loadAttendance() },
        onLoadMarks = { academicsViewModel.loadMarks() },
        onLoadSyllabus = { academicsViewModel.loadSyllabus() },
        onOpenLeave = onOpenLeave,
        modifier = modifier,
    )
}

/** Stateless body — also used by @Preview with seeded state (no MockV2 in the live path). */
@Composable
private fun ParentAcademicsContent(
    state: TrackProgressState,
    academics: ParentAcademicsState,
    onLoadAttendance: () -> Unit,
    onLoadMarks: () -> Unit,
    onLoadSyllabus: () -> Unit,
    onOpenLeave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val d = VTheme.dimens
    var tab by remember { mutableStateOf("Overview") }

    // RA-56: each tab pulls fresh data for the selected child the first time it's
    // opened (and whenever the child changes — the VM clears stale data on switch).
    LaunchedEffect(tab, academics.selectedChildId) {
        when (tab) {
            "Attendance" -> if (academics.attendance == null && !academics.attendanceLoading) onLoadAttendance()
            "Marks" -> if (academics.marks == null && !academics.marksLoading) onLoadMarks()
            "Syllabus" -> if (academics.syllabus == null && !academics.syllabusLoading) onLoadSyllabus()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            // The canvas IS the website background token (#FCF8FF) — exactly like the Home
            // dashboard. Lavender is the brand ACCENT, not a wall-to-wall fill, so the wash is a
            // barely-there whisper (≤4%) top-left rather than a heavy violet bloom that used to
            // take over the whole screen. Keeps every tab feeling like one calm premium surface.
            .background(c.background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.accent.copy(alpha = 0.04f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.02f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
            .verticalScroll(rememberScrollState())
            // Breathing room under the last card (EI) so it never sits flush against the nav.
            .padding(bottom = 28.dp),
    ) {
        Text(
            "Academics",
            style = VTheme.type.h1.colored(c.ink),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )

        // ── RA-44: apply-for-leave entry — a premium violet action card ──────
        VCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable(onClick = onOpenLeave),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Soft lavender icon chip — gives the row depth and an on-brand accent.
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.Calendar, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Apply for leave", style = VTheme.type.bodyStrong.colored(c.ink))
                    Text(
                        "Request leave for your child — routed to their class teacher",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
                Icon(VIcons.ArrowRight, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        // ONE CANONICAL CHILD SWITCHER (design law): the active child is chosen ONLY from the
        // dropdown in the shared portal header (ParentPortalV2 → ParentHeader). The pick flows
        // through the app-scoped SelectedChildHolder, so this screen re-scopes reactively — there
        // is deliberately NO second switcher here anymore.

        VTopTabs(
            tabs = listOf("Overview", "Attendance", "Marks", "Syllabus", "Report"),
            selected = tab,
            onSelect = { tab = it },
            // RA-PP-THEME: Parents Portal tabs are violet, not the legacy teal.
            activeColor = c.accentDeep,
        )
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(d.sm + 4.dp),
        ) {
            when (tab) {
                "Overview" -> OverviewTab(state)
                "Attendance" -> AttendanceTab(academics, onLoadAttendance)
                "Marks" -> MarksTab(academics, onLoadMarks)
                "Syllabus" -> SyllabusTab(academics, onLoadSyllabus)
                "Report" -> VComingSoon(
                    title = "AI Report Card",
                    description = "At the end of each term, VidyaSetu will generate a personalised academic summary — narrative strengths, focus areas and study tips.",
                    preview = { AiReportCardPreview() },
                )
            }
        }
    }
}

@Composable
private fun AttendanceTab(academics: ParentAcademicsState, onRetry: () -> Unit) {
    val c = VTheme.colors
    val data = academics.attendance
    // NOTE (directive): the month calendar is a permanent element of this tab — it ALWAYS renders,
    // even when no attendance has been marked yet. We never gate it behind an empty state, and we
    // never invent fake attendance: an unmarked month simply shows blank day cells. Only genuine
    // loading / hard-error conditions take over the surface.
    VStateHost(
        loading = academics.attendanceLoading,
        error = academics.attendanceError,
        // Empty is handled INLINE below (a calm note above a real, empty calendar), not as a
        // full-surface takeover — so the calendar is always present.
        isEmpty = false,
        onRetry = onRetry,
    ) {
        val hasData = data != null && data.totalDays > 0
        if (hasData && data != null) {
            // Summary card — only when there is real marked attendance to summarise.
            // COLOR IS SEMANTIC: the rate bar reads GREEN (a healthy attendance figure), and the
            // present/late/absent breakdown is colour-coded green/amber/red — not flattened to one
            // brand-violet tint.
            VCard {
                VLabel("This term")
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Attendance rate", style = VTheme.type.bodyStrong.colored(c.ink))
                    Text(
                        "${data.attendanceRate}%",
                        style = VTheme.type.data.colored(c.successInk).copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(Modifier.height(8.dp))
                VProgressBar(value = data.attendanceRate.toFloat(), tone = VBadgeTone.Success, height = 8.dp)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AttendanceStat("Present", data.presentDays, c.successInk, Modifier.weight(1f))
                    AttendanceStat("Late", data.lateDays, c.warningInk, Modifier.weight(1f))
                    AttendanceStat("Absent", data.absentDays, c.dangerInk, Modifier.weight(1f))
                }
            }
        } else {
            // No attendance marked yet — a calm, honest note. NO fabricated stats, no fake rate.
            VCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(c.successInk.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(VIcons.Calendar, contentDescription = null, tint = c.successInk, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("No attendance marked yet", style = VTheme.type.bodyStrong.colored(c.ink))
                        Text(
                            "Days will fill in below as the school marks attendance.",
                            style = VTheme.type.caption.colored(c.ink2),
                        )
                    }
                }
            }
        }

        // RA-S19: the month-grid calendar — ALWAYS shown. With real records each day cell is
        // colour-coded by status; with none, it renders a clean, empty (blank-cell) month so the
        // tab still feels complete and premium. See ParentAttendanceCalendar.
        ParentAttendanceCalendar(records = data?.records ?: emptyList())
    }
}

/** A small colour-coded breakdown tile (Present/Late/Absent) — colour carries the state. */
@Composable
private fun AttendanceStat(label: String, count: Int, accent: Color, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$count",
            style = VTheme.type.dataLg.colored(accent).copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = VTheme.type.label.colored(c.ink2).copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun MarksTab(academics: ParentAcademicsState, onRetry: () -> Unit) {
    val c = VTheme.colors
    val data = academics.marks
    VStateHost(
        loading = academics.marksLoading,
        error = academics.marksError,
        isEmpty = data != null && data.results.isEmpty(),
        emptyTitle = "No published marks yet",
        emptyBody = "Marks appear here once teachers publish results to parents.",
        onRetry = onRetry,
    ) {
        data?.results?.forEachIndexed { idx, m ->
            val marksValue = m.marks
            val pct = if (marksValue != null && m.maxMarks > 0) (marksValue / m.maxMarks * 100.0) else null
            // COLOR IS SEMANTIC: the score itself drives a calm grade tone (strong=green,
            // mid=amber, low=red). A neutral subject tint chip on the left gives each row identity
            // so the list isn't a wall of one colour.
            val gradeColor = when {
                pct == null -> c.ink3
                pct >= 75 -> c.successInk
                pct >= 40 -> c.warningInk
                else -> c.dangerInk
            }
            val subjectColor = subjectPalette(c)[idx % subjectPalette(c).size]
            VCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(subjectColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            m.subject.take(2).uppercase(),
                            style = VTheme.type.labelStrong.colored(subjectColor).copy(fontWeight = FontWeight.ExtraBold, fontSize = 12.sp),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(m.examName, style = VTheme.type.bodyStrong.colored(c.ink))
                        Text(m.subject, style = VTheme.type.caption.colored(c.ink2))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (marksValue != null) "${marksValue.toInt()} / ${m.maxMarks}" else "—",
                            style = VTheme.type.data.colored(gradeColor).copy(fontWeight = FontWeight.Bold),
                        )
                        if (pct != null) {
                            Text(
                                "${pct.toInt()}%",
                                style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A small, on-brand palette of secondary accents that HARMONISE with the lavender brand without
 * being lavender — drawn straight from the website token set (teal/sky/peach + violet). Used to
 * give list rows/sections individual identity so the portal reads expressive, not monochrome.
 */
// The harmonious accent rotation now lives in one place — [parentSubjectPalette] in
// ParentPalette.kt — so Academics, the Covered-today card and the detail overlay all draw from
// the SAME palette (one source of truth, consistent across the whole Parents Portal).
@Composable
private fun subjectPalette(c: com.littlebridge.enrollplus.ui.v2.theme.VColors): List<Color> =
    parentSubjectPalette(c)

@Composable
private fun SyllabusTab(academics: ParentAcademicsState, onRetry: () -> Unit) {
    val c = VTheme.colors
    val data = academics.syllabus
    VStateHost(
        loading = academics.syllabusLoading,
        error = academics.syllabusError,
        isEmpty = data != null && data.subjects.isEmpty(),
        emptyTitle = "No syllabus shared yet",
        emptyBody = "A subject-wise coverage log will appear here once the school shares it.",
        onRetry = onRetry,
    ) {
        val palette = subjectPalette(c)
        data?.subjects?.forEachIndexed { idx, subj ->
            // Each subject gets its own harmonious accent (rotating through the on-brand palette) so
            // the syllabus reads as a colourful, scannable list rather than a violet monolith. The
            // progress bar matches the subject's tone; covered units read GREEN, pending AMBER.
            val tone = palette[idx % palette.size]
            VCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tone.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            subj.subject.take(2).uppercase(),
                            style = VTheme.type.labelStrong.colored(tone).copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp),
                        )
                    }
                    Text(subj.subject, style = VTheme.type.bodyStrong.colored(c.ink), modifier = Modifier.weight(1f))
                    Text("${subj.progress}%", style = VTheme.type.data.colored(tone).copy(fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(10.dp))
                VTintedProgressBar(value = subj.progress.toFloat(), fill = tone, height = 8.dp)
                Spacer(Modifier.height(10.dp))
                subj.units.forEach { u ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(u.title, style = VTheme.type.caption.colored(if (u.isCovered) c.ink else c.ink2), modifier = Modifier.weight(1f))
                        if (u.isCovered) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(VIcons.Check, contentDescription = null, tint = c.successInk, modifier = Modifier.size(13.dp))
                                Text(
                                    u.coveredOn ?: "Covered",
                                    style = VTheme.type.caption.colored(c.successInk).copy(fontWeight = FontWeight.SemiBold),
                                )
                            }
                        } else {
                            Text("Pending", style = VTheme.type.caption.colored(c.warningInk).copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/** A progress bar that fills with an arbitrary tonal [fill] colour (not limited to badge tones). */
@Composable
private fun VTintedProgressBar(value: Float, fill: Color, height: androidx.compose.ui.unit.Dp) {
    val c = VTheme.colors
    val clamped = (value.coerceIn(0f, 100f)) / 100f
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(c.cream),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(RoundedCornerShape(999.dp))
                .background(fill),
        )
    }
}

@Composable
private fun OverviewTab(state: TrackProgressState) {
    val c = VTheme.colors
    VStateHost(
        loading = state.isLoading,
        error = state.error,
        isEmpty = state.academicCompetencies.isEmpty() &&
            state.emotionalIntelligence.isEmpty() && state.emotionalDescription.isBlank(),
        emptyTitle = "No progress data yet",
        emptyBody = "Your child's competencies will appear here as teachers update them.",
    ) {
        if (state.academicCompetencies.isNotEmpty()) {
            val palette = subjectPalette(c)
            state.academicCompetencies.forEachIndexed { idx, comp ->
                // Each competency carries its own harmonious accent so the overview is expressive,
                // not a stack of identical violet bars.
                val tone = palette[idx % palette.size]
                VCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            comp.title,
                            style = VTheme.type.bodyStrong.colored(c.ink).copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            "${(comp.progress * 100f).toInt()}%",
                            style = VTheme.type.data.colored(tone).copy(fontWeight = FontWeight.Bold),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // A slightly heavier track than the default reads as premium, not hairline.
                    VTintedProgressBar(value = comp.progress * 100f, fill = tone, height = 8.dp)
                }
            }
        }

        if (state.emotionalIntelligence.isNotEmpty() || state.emotionalDescription.isNotBlank()) {
            VCard {
                VLabel("Emotional intelligence")
                if (state.emotionalDescription.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.emotionalDescription,
                        style = VTheme.type.body.colored(c.ink2),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val palette = subjectPalette(c)
                    state.emotionalIntelligence.entries.forEachIndexed { idx, (trait, score) ->
                        val tone = palette[(idx + 1) % palette.size]
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(trait, style = VTheme.type.caption.colored(c.ink2))
                                Text("${(score * 100f).toInt()}%", style = VTheme.type.dataSm.colored(tone).copy(fontWeight = FontWeight.Bold))
                            }
                            VTintedProgressBar(value = score * 100f, fill = tone, height = 7.dp)
                        }
                    }
                }
            }
        }

        if (state.currentLevel > 0) {
            VCard {
                VLabel("Journey level")
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Level ${state.currentLevel}", style = VTheme.type.dataLg.colored(c.ink))
                    VBadge(text = "${(state.overallProgress * 100f).toInt()}% complete", tone = VBadgeTone.Accent)
                }
            }
        }
    }
}
