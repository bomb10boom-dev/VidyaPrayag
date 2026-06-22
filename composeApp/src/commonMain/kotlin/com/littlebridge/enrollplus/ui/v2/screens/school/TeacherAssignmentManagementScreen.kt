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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherAssignmentOverviewDto
import com.littlebridge.enrollplus.feature.admin.domain.model.TeacherClassAssignmentDto
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherAssignmentUiState
import com.littlebridge.enrollplus.feature.admin.presentation.TeacherAssignmentViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-TAM: TeacherAssignmentManagementScreen — the single reusable assignment
 * experience shared by Teacher Listing, Teacher Profile and (optionally)
 * onboarding. Resource-allocation feel: hero header · KPI carousel · current
 * assignment cards · add-assignment flow (subject → classes → sections →
 * preview → save) · workload insights · distribution visual.
 *
 * [teacherId] is loaded via [TeacherAssignmentViewModel.load] in a
 * LaunchedEffect. Three states via [VStateHost] (LAW 3).
 */
@Composable
fun TeacherAssignmentManagementScreen(
    teacherId: String,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherAssignmentViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    LaunchedEffect(teacherId) { viewModel.load(teacherId) }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    ) {
        VBackHeader(title = "Assign Classes", onBack = onBack)
        AssignmentContent(
            state = state,
            onRetry = viewModel::retry,
            onSelectSubject = viewModel::selectSubject,
            onToggleClass = viewModel::toggleClass,
            onToggleSection = viewModel::toggleSection,
            onResetDraft = viewModel::resetDraft,
            onSave = { viewModel.saveDraft() },
            onRemove = viewModel::removeAssignment,
            onClearMessage = viewModel::clearSaveMessage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AssignmentContent(
    state: TeacherAssignmentUiState,
    onRetry: () -> Unit,
    onSelectSubject: (String?, String?) -> Unit,
    onToggleClass: (String) -> Unit,
    onToggleSection: (String) -> Unit,
    onResetDraft: () -> Unit,
    onSave: () -> Unit,
    onRemove: (String) -> Unit,
    onClearMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var pendingRemoveId by remember { mutableStateOf<String?>(null) }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.overview == null && !state.isLoading && state.error == null,
            emptyTitle = "No teacher",
            emptyBody = "This teacher's record could not be found.",
            emptyIcon = VIcons.User,
            onRetry = onRetry,
        ) {
            val overview = state.overview ?: return@VStateHost
            TeacherHeader(overview)                       // 1. Teacher header
            KpiCarousel(overview)                         // 2. Assignment summary
            CurrentAssignments(                           // 3. Current assignments
                assignments = overview.assignments,
                removingId = state.removingId,
                onRequestRemove = { pendingRemoveId = it },
            )
            state.removeError?.let { err ->
                Text(err, style = VTheme.type.caption.colored(c.dangerInk))
            }
            AddAssignment(                                // 4. Add assignment flow
                state = state,
                onSelectSubject = onSelectSubject,
                onToggleClass = onToggleClass,
                onToggleSection = onToggleSection,
                onResetDraft = onResetDraft,
                onSave = onSave,
                onClearMessage = onClearMessage,
            )
            WorkloadInsights(overview)                    // workload insights
            DistributionVisual(overview)                  // distribution visual
        }
    }

    VConfirmDialog(
        visible = pendingRemoveId != null,
        title = "Remove assignment",
        message = "Remove this class/subject assignment from the teacher? This can be re-added at any time.",
        confirmLabel = "Remove",
        icon = VIcons.AlertTriangle,
        onConfirm = {
            pendingRemoveId?.let(onRemove)
            pendingRemoveId = null
        },
        onDismiss = { pendingRemoveId = null },
    )
}

// ─────────────────────────── 1. Teacher header ────────────────────────────

@Composable
private fun TeacherHeader(overview: TeacherAssignmentOverviewDto) {
    val c = VTheme.colors
    val s = overview.summary
    val subject = overview.distribution.firstOrNull()?.subject
    VCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            VAvatar(name = s.teacherName, size = 64.dp, ring = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(s.teacherName, style = VTheme.type.h2.colored(c.ink))
                Text(
                    subject?.let { "$it Teacher" } ?: "Teacher",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    VBadge(text = "${s.classCount} Classes", tone = VBadgeTone.Arctic)
                    VBadge(text = "${s.subjectCount} Subjects", tone = VBadgeTone.Success)
                }
            }
        }
    }
}

// ─────────────────────────── 2. KPI carousel ──────────────────────────────

@Composable
private fun KpiCarousel(overview: TeacherAssignmentOverviewDto) {
    val s = overview.summary
    val kpis = listOf(
        KpiData("Classes Assigned", s.classCount.toString(), "active", VIcons.School, VBadgeTone.Arctic),
        KpiData("Subjects Assigned", s.subjectCount.toString(), "covered", VIcons.BookOpen, VBadgeTone.Warning),
        KpiData("Total Students", s.studentCount.toString(), "taught", VIcons.Users, VBadgeTone.Success),
        KpiData("Sections Covered", s.sectionCount.toString(), "across classes", VIcons.Target, VBadgeTone.Arctic),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ASSIGNMENT SUMMARY")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(kpis) { KpiCard(it) }
        }
    }
}

private data class KpiData(
    val label: String,
    val value: String,
    val support: String,
    val icon: ImageVector,
    val tone: VBadgeTone,
)

@Composable
private fun KpiCard(data: KpiData) {
    val c = VTheme.colors
    val tint = toneTint(data.tone)
    VCard(modifier = Modifier.width(150.dp), padding = 16.dp) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(data.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(data.value, style = VTheme.type.dataLg.colored(c.ink))
        Text(data.label, style = VTheme.type.bodyStrong.colored(c.ink2))
        Text(data.support, style = VTheme.type.label.colored(c.ink3))
    }
}

// ──────────────────────── 3. Current assignments ──────────────────────────

@Composable
private fun CurrentAssignments(
    assignments: List<TeacherClassAssignmentDto>,
    removingId: String?,
    onRequestRemove: (String) -> Unit,
) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "CURRENT ASSIGNMENTS")
        if (assignments.isEmpty()) {
            EmptyCard(VIcons.BookOpen, "No classes assigned yet. Use the builder below to add some.")
        } else {
            assignments.forEach { a ->
                AssignmentCard(a, isRemoving = removingId == a.id, onRemove = { onRequestRemove(a.id) })
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    a: TeacherClassAssignmentDto,
    isRemoving: Boolean,
    onRemove: () -> Unit,
) {
    val c = VTheme.colors
    VCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.tealDeep.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.BookOpen, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(a.subject, style = VTheme.type.h4.colored(c.ink))
                Text(
                    "${a.className} · Section ${a.section}",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(VIcons.Users, contentDescription = null, tint = c.ink3, modifier = Modifier.size(14.dp))
                    Text("${a.studentCount} students", style = VTheme.type.caption.colored(c.ink3))
                }
            }
            VButton(
                text = "Remove",
                onClick = onRemove,
                variant = VButtonVariant.Destructive,
                size = VButtonSize.Sm,
                loading = isRemoving,
                enabled = !isRemoving,
            )
        }
    }
}

// ─────────────────────── 4. Add assignment flow ───────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddAssignment(
    state: TeacherAssignmentUiState,
    onSelectSubject: (String?, String?) -> Unit,
    onToggleClass: (String) -> Unit,
    onToggleSection: (String) -> Unit,
    onResetDraft: () -> Unit,
    onSave: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val c = VTheme.colors
    val options = state.options
    val draft = state.draft

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ADD ASSIGNMENT")
        VCard(padding = 18.dp) {
            if (options == null) {
                Text("Loading class & subject options…", style = VTheme.type.body.colored(c.ink2))
                return@VCard
            }
            if (options.classes.isEmpty() && options.subjects.isEmpty()) {
                Text(
                    "No classes or subjects have been set up for this school yet.",
                    style = VTheme.type.body.colored(c.ink2),
                )
                return@VCard
            }

            // Step 1 — Subject
            StepLabel("Step 1 · Select subject")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.subjects.forEach { subj ->
                    VTag(
                        text = subj.name,
                        active = draft.subjectId == subj.subjectId ||
                            (draft.subjectId == null && draft.subjectName == subj.name),
                        onClick = { onSelectSubject(subj.subjectId, subj.name) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Step 2 — Classes (multi-select)
            StepLabel("Step 2 · Select classes")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.classes.forEach { cls ->
                    VTag(
                        text = cls.name,
                        active = cls.classId in draft.selectedClassIds,
                        onClick = { onToggleClass(cls.classId) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Step 3 — Sections (union of sections from chosen classes)
            val availableSections = options.classes
                .filter { it.classId in draft.selectedClassIds }
                .flatMap { it.sections }
                .distinct()
                .sorted()
            StepLabel("Step 3 · Select sections")
            if (availableSections.isEmpty()) {
                Text(
                    "Pick one or more classes first — their sections appear here. (Leaving this empty assigns ALL sections of the chosen classes.)",
                    style = VTheme.type.caption.colored(c.ink3),
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableSections.forEach { sec ->
                        VTag(
                            text = sec,
                            active = sec in draft.selectedSections,
                            onClick = { onToggleSection(sec) },
                        )
                    }
                }
                Text(
                    "Leave all unselected to assign every section of the chosen classes.",
                    style = VTheme.type.label.colored(c.ink3),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            // Step 4 — Preview
            val previewClasses = options.classes.filter { it.classId in draft.selectedClassIds }
            val previewTargets = previewClasses.flatMap { cls ->
                val secs = if (draft.selectedSections.isEmpty()) cls.sections
                else cls.sections.filter { it in draft.selectedSections }
                secs.ifEmpty { draft.selectedSections.toList() }.map { "${cls.name} $it" }
            }
            if (draft.subjectName != null && previewTargets.isNotEmpty()) {
                StepLabel("Step 4 · Preview")
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.cream).padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(draft.subjectName?:"", style = VTheme.type.bodyStrong.colored(c.ink))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            previewTargets.forEach { t -> VBadge(text = t, tone = VBadgeTone.Arctic) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Step 5 — Save / Reset
            state.saveError?.let { err ->
                Text(err, style = VTheme.type.caption.colored(c.dangerInk))
                Spacer(Modifier.height(8.dp))
            }
            state.lastSaveMessage?.let { msg ->
                Text(msg, style = VTheme.type.caption.colored(c.successInk))
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton(
                    text = "Clear",
                    onClick = { onResetDraft(); onClearMessage() },
                    variant = VButtonVariant.Ghost,
                    tone = VButtonTone.Navy,
                    enabled = !state.isSaving,
                )
                VButton(
                    text = "Save assignments",
                    onClick = onSave,
                    tone = VButtonTone.Teal,
                    full = true,
                    loading = state.isSaving,
                    enabled = !state.isSaving,
                    leading = { Icon(VIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(
        text,
        style = VTheme.type.label.colored(VTheme.colors.ink3),
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

// ───────────────────────── workload insights ──────────────────────────────

@Composable
private fun WorkloadInsights(overview: TeacherAssignmentOverviewDto) {
    val c = VTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "WORKLOAD INSIGHTS")
        if (overview.insights.isEmpty()) {
            EmptyCard(VIcons.Sparkles, "No workload insights yet.")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                overview.insights.forEach { insight ->
                    VCard(padding = 14.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.teal.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(VIcons.Sparkles, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(17.dp))
                            }
                            Text(insight, style = VTheme.type.body.colored(c.ink), modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────── distribution visual ──────────────────────────────

@Composable
private fun DistributionVisual(overview: TeacherAssignmentOverviewDto) {
    val c = VTheme.colors
    val dist = overview.distribution
    if (dist.isEmpty()) return
    val max = (dist.maxOfOrNull { it.studentCount } ?: 0).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VSectionHeader(title = "ASSIGNMENT DISTRIBUTION")
        VCard(padding = 18.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                dist.forEach { d ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.subject, style = VTheme.type.bodyStrong.colored(c.ink2))
                            Text(
                                "${d.classCount} cls · ${d.studentCount} stu",
                                style = VTheme.type.label.colored(c.ink3),
                            )
                        }
                        VProgressBar(
                            value = (d.studentCount * 100f) / max,
                            tone = VBadgeTone.Arctic,
                            height = 8.dp,
                        )
                    }
                }
            }
        }
    }
}

// ───────────────────────────── shared bits ────────────────────────────────

@Composable
private fun EmptyCard(icon: ImageVector, message: String) {
    val c = VTheme.colors
    VCard(padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(c.cream),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(17.dp))
            }
            Text(message, style = VTheme.type.body.colored(c.ink2))
        }
    }
}

@Composable
private fun toneTint(tone: VBadgeTone) = when (tone) {
    VBadgeTone.Arctic,VBadgeTone.Accent -> VTheme.colors.tealDeep
    VBadgeTone.Success -> VTheme.colors.successInk
    VBadgeTone.Warning -> VTheme.colors.warningInk
    VBadgeTone.Danger -> VTheme.colors.dangerInk
    VBadgeTone.Neutral -> VTheme.colors.ink3
}
