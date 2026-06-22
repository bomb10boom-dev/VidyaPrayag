package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
// Explicit imports to avoid collision with feature.teacher.presentation.AttendanceStatus/Attendee
import com.littlebridge.enrollplus.feature.admin.presentation.Attendee
import com.littlebridge.enrollplus.feature.admin.presentation.AttendanceStatus
import com.littlebridge.enrollplus.feature.admin.presentation.DailyAttendanceState
import com.littlebridge.enrollplus.feature.admin.presentation.DailyAttendanceViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * DailyAttendanceScreenV2 — admin daily attendance roster overlay.
 *
 * Wired to [DailyAttendanceViewModel] (`GET /api/v1/school/attendance/daily`,
 * `PATCH /api/v1/school/attendance/{id}/status`).
 *
 * NOTE: explicitly imports admin's [AttendanceStatus]/[Attendee] (NOT
 * feature.teacher.presentation — same names exist there).
 *
 * Layout:
 *   • Students/Faculty VTag selector (setAttendanceType)
 *   • Class picker (horizontally scrolling VTag row) — only visible for Students
 *   • Summary VCard: presentCount/totalCount + percentage VBadge (Arctic)
 *   • Roster — each row: avatar + name + 3 P/A/L pills (Present=Success,
 *     Absent=Danger, Late=Warning) wired to updateStatus.
 *
 * Three states via [VStateHost] (LAW 3).
 */
@Composable
fun DailyAttendanceScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DailyAttendanceViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Daily Attendance", onBack = onBack)
        DailyAttendanceContent(
            state = state,
            onTypeChange = viewModel::setAttendanceType,
            onClassChange = viewModel::selectClass,
            onUpdateStatus = viewModel::updateStatus,
            onRetry = { viewModel.setAttendanceType(state.attendanceType) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DailyAttendanceContent(
    state: DailyAttendanceState,
    onTypeChange: (String) -> Unit,
    onClassChange: (String) -> Unit,
    onUpdateStatus: (String, AttendanceStatus) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val isStudents = state.attendanceType.equals("Students", ignoreCase = true)

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Type selector
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VTag(text = "Students", active = isStudents, onClick = { onTypeChange("Students") })
            VTag(text = "Faculty", active = !isStudents, onClick = { onTypeChange("Faculty") })
        }

        // Class picker (Students mode only)
        if (isStudents && state.availableClasses.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.availableClasses.forEach { name ->
                    VTag(
                        text = name,
                        active = name == state.selectedClass,
                        onClick = { onClassChange(name) },
                    )
                }
            }
        }

        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            isEmpty = state.attendees.isEmpty(),
            emptyTitle = "No roster",
            emptyBody = if (isStudents)
                "There are no students in ${state.selectedClass} yet."
            else
                "No faculty roster is available.",
            emptyIcon = VIcons.Users,
            onRetry = onRetry,
        ) {
            // Summary
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Present today", style = VTheme.type.label.colored(c.ink3))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.presentCount} / ${state.totalCount}",
                            style = VTheme.type.dataLg.colored(c.ink),
                        )
                    }
                    VBadge(text = state.attendancePercentage, tone = VBadgeTone.Arctic)
                }
            }

            VSectionHeader(title = if (isStudents) "STUDENTS" else "FACULTY")

            VCard {
                state.attendees.forEachIndexed { i, a ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                    AttendeeRow(attendee = a, onSetStatus = { onUpdateStatus(a.id, it) })
                }
            }
        }
    }
}

@Composable
private fun AttendeeRow(
    attendee: Attendee,
    onSetStatus: (AttendanceStatus) -> Unit,
) {
    val c = VTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VAvatar(name = attendee.name.ifBlank { attendee.initials.ifBlank { "?" } }, src = attendee.imageUrl, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(attendee.name, style = VTheme.type.bodyStrong.colored(c.ink))
            Text(attendee.initials, style = VTheme.type.caption.colored(c.ink3))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            StatusPill("P", attendee.status == AttendanceStatus.PRESENT, c.success) { onSetStatus(AttendanceStatus.PRESENT) }
            StatusPill("A", attendee.status == AttendanceStatus.ABSENT, c.danger) { onSetStatus(AttendanceStatus.ABSENT) }
            StatusPill("L", attendee.status == AttendanceStatus.LATE, c.warning) { onSetStatus(AttendanceStatus.LATE) }
        }
    }
}

@Composable
private fun StatusPill(letter: String, active: Boolean, tone: Color, onClick: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) tone else c.ink.copy(alpha = 0.06f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = VTheme.type.label.colored(if (active) c.background else c.ink3).copy(fontWeight = FontWeight.SemiBold),
        )
    }
}
