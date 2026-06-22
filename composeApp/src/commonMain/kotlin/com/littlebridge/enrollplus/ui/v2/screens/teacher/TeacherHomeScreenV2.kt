package com.littlebridge.enrollplus.ui.v2.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherHomeState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherHomeViewModel
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherTask
import com.littlebridge.enrollplus.presentation.PermissionViewModel
import com.littlebridge.enrollplus.ui.v2.components.*
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * TeacherHomeScreenV2 — `Teacher.tsx → TeacherHome`, wired to the real [TeacherHomeViewModel]
 * (`TeacherRepository.getHome` → `GET /api/v1/teacher/home`).
 *
 * Greeting header (bell + avatar) · "Today's tasks" colour-coded cards · "Today's periods"
 * horizontal scroller · recent-activity list — all from live VM state. No MockV2 in production:
 * the three UI states (Loading · Error · Empty) come from [VStateHost].
 */
@Composable
fun TeacherHomeScreenV2(
    modifier: Modifier = Modifier,
    onOpenNotifications: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenLeave: () -> Unit = {},
    onExit: () -> Unit = {},
    viewModel: TeacherHomeViewModel = koinViewModel(),
    permissionVm: PermissionViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val showRationale by permissionVm.showNotificationRationale.collectAsStateV2()

    androidx.compose.runtime.LaunchedEffect(Unit) {
        permissionVm.checkNotificationPermission()
    }

    TeacherHomeContent(
        state = state,
        onOpenNotifications = onOpenNotifications,
        onOpenCalendar = onOpenCalendar,
        onOpenLeave = onOpenLeave,
        onExit = onExit,
        onRetry = viewModel::load,
        modifier = modifier,
    )

    VConfirmDialog(
        visible = showRationale,
        title = "Stay Informed",
        message = "Enable notifications to receive important updates about student attendance, new assignments, and school events.",
        confirmLabel = "Enable",
        onConfirm = permissionVm::requestNotificationPermission,
        onDismiss = permissionVm::declineNotifications,
        cancelLabel = "Not Now",
        icon = VIcons.Bell,
    )
}

@Composable
private fun TeacherHomeContent(
    state: TeacherHomeState,
    onOpenNotifications: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenLeave: () -> Unit,
    onExit: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val firstName = state.teacherName.substringBefore(' ').ifBlank { state.teacherName }

    Column(
        modifier
            .fillMaxSize()
            // §11.1 — push header under the status bar / iOS notch.
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                VLabel("Welcome back")
                Text(
                    firstName.ifBlank { "Teacher" },
                    style = VTheme.type.h1.colored(c.ink),
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(state.schoolName, style = VTheme.type.caption.colored(c.ink2))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.06f)).clickable { onOpenNotifications() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.Bell, contentDescription = "Notifications", tint = c.ink, modifier = Modifier.size(18.dp))
                }
                Box(Modifier.clickable { onExit() }) { VAvatar(name = state.teacherName.ifBlank { "Teacher" }, size = 40.dp) }
            }
        }

        // ── RA-44: leave-requests queue entry ────────────────────────────────
        VCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenLeave)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(c.ink.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.ClipboardList, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Leave requests", style = VTheme.type.bodyStrong.colored(c.ink))
                    Text(
                        "Review and decide student leave for your classes",
                        style = VTheme.type.caption.colored(c.ink2),
                    )
                }
                Icon(VIcons.ArrowRight, contentDescription = null, tint = c.ink3, modifier = Modifier.size(18.dp))
            }
        }

        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.periods.isEmpty() && state.tasks.isEmpty(),
            emptyTitle = "Nothing scheduled",
            emptyBody = "Your tasks and periods for today will show up here.",
            emptyIcon = VIcons.Calendar,
            onRetry = onRetry,
            skeleton = { com.littlebridge.enrollplus.ui.v2.screens.SkeletonDashboard() },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                // ── Today's tasks ────────────────────────────────────────────
                if (state.tasks.isNotEmpty()) {
                    Column {
                        Text("Today's tasks", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.tasks.forEach { task ->
                                TaskCard(
                                    tone = toneForTask(task),
                                    title = task.title,
                                    sub = task.subtitle,
                                    cta = ctaForTask(task),
                                    icon = iconForTask(task),
                                    onTap = onOpenCalendar,
                                )
                            }
                        }
                    }
                }

                // ── Today's periods ──────────────────────────────────────────
                if (state.periods.isNotEmpty()) {
                    Column {
                        Text("Today's periods", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.periods.forEach { p ->
                                val active = p.status.equals("active", ignoreCase = true) ||
                                    p.status.equals("current", ignoreCase = true)
                                Column(
                                    Modifier
                                        .widthIn(min = 150.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) c.teal else c.ink.copy(alpha = 0.06f))
                                        .padding(12.dp),
                                ) {
                                    Text(p.time, style = VTheme.type.labelStrong.colored(if (active) Color(0xFF080808) else c.ink3))
                                    Text(p.subject, style = VTheme.type.bodyStrong.colored(if (active) Color(0xFF080808) else c.ink), modifier = Modifier.padding(top = 4.dp))
                                    Text(
                                        listOfNotNull(p.className.ifBlank { null }, p.room.ifBlank { null }).joinToString(" • "),
                                        style = VTheme.type.dataSm.colored(if (active) Color(0xFF080808).copy(alpha = 0.8f) else c.ink2).copy(fontSize = 11.sp),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── At a glance (live counts) ────────────────────────────────
                Column {
                    Text("At a glance", style = VTheme.type.h3.colored(c.ink), modifier = Modifier.padding(bottom = 8.dp))
                    VCard {
                        val rows = listOf(
                            "Classes today" to state.classesToday.toString(),
                            "Pending attendance" to state.pendingAttendance.toString(),
                            "Pending marks" to state.pendingMarks.toString(),
                            "Homework due" to state.homeworkDue.toString(),
                        )
                        rows.forEachIndexed { i, (what, count) ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(what, style = VTheme.type.body.colored(c.ink), modifier = Modifier.weight(1f))
                                Text(count, style = VTheme.type.data.colored(c.ink).copy(letterSpacing = TextUnit.Unspecified))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Map a task's `type` to the pastel tone used by the React TaskCard icon-circle. */
@Composable
private fun toneForTask(task: TeacherTask): Color {
    val c = VTheme.colors
    return when (task.type.lowercase()) {
        "attendance" -> c.success
        "syllabus" -> c.warning
        "marks" -> c.teal
        else -> Color(0x26F5F5F3)
    }
}

private fun iconForTask(task: TeacherTask): ImageVector = when (task.type.lowercase()) {
    "attendance" -> VIcons.Check
    "syllabus" -> VIcons.AlertCircle
    "marks" -> VIcons.ListChecks
    else -> VIcons.Clock
}

private fun ctaForTask(task: TeacherTask): String = when (task.type.lowercase()) {
    "attendance" -> "View details"
    "syllabus" -> "Update now"
    "marks" -> "Enter marks"
    else -> "View"
}

@Composable
private fun TaskCard(tone: Color, title: String, sub: String, cta: String, icon: ImageVector, onTap: () -> Unit = {}) {
    val c = VTheme.colors
    VCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF080808), modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = VTheme.type.bodyStrong.colored(c.ink))
                Text(sub, style = VTheme.type.caption.colored(c.ink2))
            }
            Text(cta, style = VTheme.type.caption.colored(c.teal).copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable { onTap() })
        }
    }
}
