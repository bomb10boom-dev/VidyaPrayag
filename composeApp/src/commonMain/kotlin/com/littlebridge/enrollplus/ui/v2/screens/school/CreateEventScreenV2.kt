package com.littlebridge.enrollplus.ui.v2.screens.school

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventAudience
import com.littlebridge.enrollplus.feature.admin.domain.model.CalEventType
import com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VDatePicker
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * CreateEventScreenV2 — the dedicated 7-step event-creation wizard (NO dialogs).
 *   1. Event Type     (cards)
 *   2. Basic Details
 *   3. Schedule       (start / end / all-day / multi-day)
 *   4. Audience       (multi-select)
 *   5. Notifications
 *   6. Preview        (review + conflict heads-up)
 *   7. Save Draft OR Publish
 */
@Composable
fun CreateEventScreenV2(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateCalendarEventViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val c = VTheme.colors

    // Pop once the event is actually created.
    LaunchedEffect(state.created) {
        if (state.created != null) {
            onCreated()
            viewModel.reset()
        }
    }

    Column(
        modifier.fillMaxSize().background(c.background).imePadding().navigationBarsPadding(),
    ) {
        VBackHeader(
            title = "Create Event",
            onBack = { if (state.step > 1) viewModel.back() else onBack() },
        )

        // Step progress bar.
        StepProgress(current = state.step, total = state.totalSteps)

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state.step) {
                1 -> StepType(state.form.type, viewModel::setType)
                2 -> StepBasics(state, viewModel)
                3 -> StepSchedule(state, viewModel)
                4 -> StepAudience(state.form.audiences, viewModel::toggleAudience)
                5 -> StepNotifications(state, viewModel)
                6 -> StepPreview(state)
                7 -> StepSave(state)
            }

            state.errorMessage?.let {
                Text(it, style = VTheme.type.caption.colored(c.dangerInk))
            }
        }

        // Footer navigation.
        WizardFooter(state, viewModel)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Progress + footer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepProgress(current: Int, total: Int) {
    val c = VTheme.colors
    Column(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            "Step $current of $total",
            style = VTheme.type.label.colored(c.ink3),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..total).forEach { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= current) c.tealDeep else c.hairline),
                )
            }
        }
    }
}

@Composable
private fun WizardFooter(
    state: com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventState,
    viewModel: CreateCalendarEventViewModel,
) {
    val c = VTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.card).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step > 1) {
            VButton(
                text = "Back",
                onClick = viewModel::back,
                variant = VButtonVariant.Secondary,
                tone = VButtonTone.Navy,
                modifier = Modifier.weight(1f),
            )
        }
        when (state.step) {
            7 -> {
                VButton(
                    text = "Save Draft",
                    onClick = viewModel::saveDraft,
                    variant = VButtonVariant.Secondary,
                    tone = VButtonTone.Navy,
                    loading = state.isSaving,
                    modifier = Modifier.weight(1f),
                )
                VButton(
                    text = "Publish",
                    onClick = viewModel::publish,
                    tone = VButtonTone.Teal,
                    loading = state.isSaving,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                VButton(
                    text = "Continue",
                    onClick = viewModel::next,
                    tone = VButtonTone.Teal,
                    enabled = state.canGoNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 1 — Event Type cards
// ─────────────────────────────────────────────────────────────────────────────

private data class TypeOption(val type: String, val title: String, val subtitle: String)

private val TYPE_OPTIONS = listOf(
    TypeOption(CalEventType.EXAM, "Exam", "Tests, assessments, finals"),
    TypeOption(CalEventType.HOLIDAY, "Holiday", "Days off & breaks"),
    TypeOption(CalEventType.PTM, "PTM", "Parent-teacher meetings"),
    TypeOption(CalEventType.SCHOOL_EVENT, "School Event", "Functions, celebrations"),
    TypeOption(CalEventType.ACTIVITY, "Activity", "Sports, clubs, field trips"),
    TypeOption(CalEventType.ADMINISTRATIVE, "Administrative", "Internal / staff items"),
)

@Composable
private fun StepType(selected: String, onSelect: (String) -> Unit) {
    val c = VTheme.colors
    VLabel("What kind of event?")
    TYPE_OPTIONS.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { opt ->
                val isSel = opt.type == selected
                VCard(
                    modifier = Modifier.weight(1f),
                    background = if (isSel) c.teal.copy(alpha = 0.12f) else c.card,
                    onClick = { onSelect(opt.type) },
                ) {
                    Text(opt.title, style = VTheme.type.bodyStrong.colored(if (isSel) c.tealDeep else c.ink))
                    Spacer(Modifier.height(2.dp))
                    Text(opt.subtitle, style = VTheme.type.caption.colored(c.ink3))
                }
            }
            if (row.size == 1) Box(Modifier.weight(1f)) {}
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 2 — Basic details
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepBasics(
    state: com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventState,
    viewModel: CreateCalendarEventViewModel,
) {
    VLabel("Basic details")
    VInput(
        value = state.form.title,
        onValueChange = viewModel::setTitle,
        label = "Title",
        placeholder = "e.g. Half-Yearly Examinations",
    )
    VInput(
        value = state.form.description,
        onValueChange = viewModel::setDescription,
        label = "Description",
        placeholder = "Add details for parents and staff",
        singleLine = false,
    )
    VInput(
        value = state.form.bannerUrl,
        onValueChange = viewModel::setBannerUrl,
        label = "Banner image URL (optional)",
        placeholder = "https://…",
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 3 — Schedule
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepSchedule(
    state: com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventState,
    viewModel: CreateCalendarEventViewModel,
) {
    val c = VTheme.colors
    VLabel("When is it?")
    VDatePicker(
        value = state.form.startDate,
        onValueChange = viewModel::setStartDate,
        label = "Start date",
    )
    VDatePicker(
        value = state.form.endDate,
        onValueChange = viewModel::setEndDate,
        label = "End date (same as start for single-day)",
    )
    TogglePill(
        label = "All-day event",
        checked = state.form.allDay,
        onToggle = { viewModel.setAllDay(it) },
    )
    if (state.form.startDate.isNotBlank() && state.form.endDate.isNotBlank() &&
        state.form.endDate > state.form.startDate
    ) {
        Text("Multi-day event", style = VTheme.type.caption.colored(c.tealDeep))
    }
    TogglePill(
        label = "Mark as academic milestone",
        checked = state.form.isMilestone,
        onToggle = { viewModel.setMilestone(it) },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 4 — Audience multi-select
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepAudience(selected: Set<String>, onToggle: (String) -> Unit) {
    val c = VTheme.colors
    VLabel("Who is this for?")
    CalEventAudience.ALL.forEach { scope ->
        val isSel = scope in selected
        VCard(
            background = if (isSel) c.teal.copy(alpha = 0.12f) else c.card,
            onClick = { onToggle(scope) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) c.tealDeep else c.cream),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSel) Icon(VIcons.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(CalEventAudience.label(scope), style = VTheme.type.bodyStrong.colored(c.ink))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 5 — Notifications
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepNotifications(
    state: com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventState,
    viewModel: CreateCalendarEventViewModel,
) {
    VLabel("Send notifications to")
    TogglePill("Students", state.form.notifyStudents) { viewModel.setNotify(students = it) }
    TogglePill("Parents", state.form.notifyParents) { viewModel.setNotify(parents = it) }
    TogglePill("Teachers", state.form.notifyTeachers) { viewModel.setNotify(teachers = it) }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 6 — Preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepPreview(
    state: com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventState,
) {
    val c = VTheme.colors
    val f = state.form
    VLabel("Review")
    VCard {
        PreviewRow("Type", CalEventType.label(f.type))
        PreviewRow("Title", f.title)
        if (f.description.isNotBlank()) PreviewRow("Description", f.description)
        PreviewRow("Schedule", if (f.endDate.isBlank() || f.endDate == f.startDate) f.startDate else "${f.startDate} → ${f.endDate}")
        PreviewRow("All-day", if (f.allDay) "Yes" else "No")
        PreviewRow("Audience", f.audiences.joinToString { CalEventAudience.label(it) })
        val notify = buildList {
            if (f.notifyStudents) add("Students")
            if (f.notifyParents) add("Parents")
            if (f.notifyTeachers) add("Teachers")
        }
        PreviewRow("Notify", if (notify.isEmpty()) "No one" else notify.joinToString())
        if (f.isMilestone) PreviewRow("Milestone", "Yes")
    }
    Text(
        "Tip: events overlapping existing ones are flagged with a Potential Schedule Conflict after saving.",
        style = VTheme.type.caption.colored(c.ink3),
    )
}

@Composable
private fun PreviewRow(label: String, value: String) {
    val c = VTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = VTheme.type.caption.colored(c.ink3), modifier = Modifier.width(110.dp))
        Text(value, style = VTheme.type.body.colored(c.ink), modifier = Modifier.weight(1f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step 7 — Save
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StepSave(
    state: com.littlebridge.enrollplus.feature.admin.presentation.CreateCalendarEventState,
) {
    val c = VTheme.colors
    VLabel("Ready to go")
    VCard {
        Text(state.form.title, style = VTheme.type.h3.colored(c.ink))
        Spacer(Modifier.height(4.dp))
        Text(
            "Save as a draft to keep editing, or publish to make it live and send any selected notifications.",
            style = VTheme.type.body.colored(c.ink2),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom toggle pill (design-system primitive, no raw Material Switch)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TogglePill(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val c = VTheme.colors
    VCard(onClick = { onToggle(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = VTheme.type.bodyStrong.colored(c.ink), modifier = Modifier.weight(1f))
            Box(
                Modifier.size(width = 44.dp, height = 26.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (checked) c.tealDeep else c.hairline)
                    .clickable { onToggle(!checked) }
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}
