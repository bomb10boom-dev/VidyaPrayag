package com.littlebridge.enrollplus.ui.v2.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherClass
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherClassesState
import com.littlebridge.enrollplus.feature.teacher.presentation.TeacherClassesViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VComingSoon
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/**
 * TeacherClassesScreenV2 — `Teacher.tsx → MyClasses` + `ClassDetail`, wired to the real
 * [TeacherClassesViewModel] (`TeacherRepository.getClasses` → `GET /api/v1/teacher/classes`).
 *
 * A 2-column grid of class cards (students + today %), tapping one opens a back-headered class
 * detail (stat header + roster placeholder). No MockV2 in production; the per-student roster has
 * no backend yet, so the detail surfaces real class stats and a [VComingSoon] roster card.
 */
@Composable
fun TeacherClassesScreenV2(
    onOpenClass: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: TeacherClassesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    TeacherClassesContent(
        state = state,
        onOpenClass = onOpenClass,
        onRetry = viewModel::load,
        onMessageParents = viewModel::openBroadcast,
        onSendBroadcast = viewModel::sendBroadcast,
        onCloseBroadcast = viewModel::closeBroadcast,
        modifier = modifier,
    )
}

@Composable
private fun TeacherClassesContent(
    state: TeacherClassesState,
    onOpenClass: (String) -> Unit,
    onRetry: () -> Unit,
    onMessageParents: (String) -> Unit,
    onSendBroadcast: (String) -> Unit,
    onCloseBroadcast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var detailId by remember { mutableStateOf<String?>(null) }

    // RA-51: the "message class parents" composer overlay.
    if (state.broadcastClassName != null) {
        ClassBroadcastComposer(
            className = state.broadcastClassName!!,
            sending = state.broadcasting,
            error = state.broadcastError,
            sentCount = state.broadcastResultCount,
            onSend = onSendBroadcast,
            onClose = onCloseBroadcast,
            modifier = modifier,
        )
        return
    }

    val detail = detailId?.let { id -> state.classes.firstOrNull { it.id == id } }
    if (detail != null) {
        ClassDetail(
            cls = detail,
            onBack = { detailId = null },
            onMessageParents = { onMessageParents(detail.className) },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 24.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("My Classes", style = VTheme.type.h1.colored(c.ink))

        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.classes.isEmpty(),
            emptyTitle = "No classes assigned",
            emptyBody = "Classes you teach will appear here once your school adds them.",
            emptyIcon = VIcons.Users,
            onRetry = onRetry,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.classes.chunked(2).forEach { rowCls ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowCls.forEach { cls ->
                            VCard(modifier = Modifier.weight(1f), onClick = { detailId = cls.id; onOpenClass(cls.id) }) {
                                Text(
                                    cls.className,
                                    style = VTheme.type.h3.colored(c.ink).copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                                )
                                Text(cls.subject, style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 11.sp))
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(cls.studentCount.toString(), style = VTheme.type.data.colored(c.ink).copy(fontSize = 18.sp))
                                        Text("Students", style = VTheme.type.label.colored(c.ink3).copy(letterSpacing = TextUnit.Unspecified, fontSize = 10.sp))
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${(cls.avgAttendance * 100).roundToInt()}%", style = VTheme.type.data.colored(c.teal).copy(fontSize = 18.sp))
                                        Text("Attendance", style = VTheme.type.label.colored(c.ink3).copy(letterSpacing = TextUnit.Unspecified, fontSize = 10.sp))
                                    }
                                }
                            }
                        }
                        if (rowCls.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassDetail(
    cls: TeacherClass,
    onBack: () -> Unit,
    onMessageParents: () -> Unit,
    modifier: Modifier,
) {
    val c = VTheme.colors
    Column(modifier.fillMaxSize()) {
        VBackHeader(
            title = cls.className,
            onBack = onBack,
            action = {
                Icon(VIcons.Edit3, contentDescription = "Edit", tint = c.ink, modifier = Modifier.size(16.dp))
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat(cls.studentCount.toString(), "Students", Modifier.weight(1f))
                MiniStat("${(cls.avgAttendance * 100).roundToInt()}%", "Attendance", Modifier.weight(1f))
                MiniStat("${(cls.syllabusProgress * 100).roundToInt()}%", "Syllabus", Modifier.weight(1f))
            }
            // RA-51: opens the broadcast composer (was a dead onClick = {}).
            VButton(text = "Message class parents", onClick = onMessageParents, full = true, variant = VButtonVariant.Secondary)
            // No per-class roster endpoint exists yet (LAW 2: no MockV2 in production paths) —
            // surface an honest placeholder until the roster backend lands.
            VComingSoon(
                title = "Student roster",
                description = "The per-student roster for ${cls.className} will appear here once the class-detail backend is available.",
            )
        }
    }
}

/**
 * RA-51 — composer for "message class parents". Sends one message that fans out
 * to every parent of [className] via `POST /api/v1/teacher/messages/class`.
 * Three states: idle compose / sending / sent-confirmation (or error).
 */
@Composable
private fun ClassBroadcastComposer(
    className: String,
    sending: Boolean,
    error: String?,
    sentCount: Int?,
    onSend: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var body by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        VBackHeader(title = "Message $className parents", onBack = onClose)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sentCount != null) {
                VCard {
                    Text(
                        "Message delivered to $sentCount parent${if (sentCount == 1) "" else "s"}.",
                        style = VTheme.type.bodyStrong.colored(c.ink),
                    )
                    Spacer(Modifier.height(12.dp))
                    VButton(text = "Done", onClick = onClose, full = true)
                }
            } else {
                Text(
                    "Every parent of $className will receive this in their inbox.",
                    style = VTheme.type.caption.colored(c.ink2),
                )
                VInput(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = "Write your message…",
                    enabled = !sending,
                    singleLine = false,
                )
                if (error != null) {
                    Text(error, style = VTheme.type.caption.colored(c.danger))
                }
                VButton(
                    text = "Send to parents",
                    onClick = { if (body.isNotBlank()) onSend(body) },
                    full = true,
                    loading = sending,
                    enabled = body.isNotBlank() && !sending,
                )
            }
        }
    }
}

@Composable
private fun MiniStat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(c.ink.copy(alpha = 0.06f)).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = VTheme.type.data.colored(c.ink).copy(fontSize = 18.sp))
        Text(label, style = VTheme.type.label.colored(c.ink3).copy(letterSpacing = TextUnit.Unspecified, fontSize = 10.sp))
    }
}
