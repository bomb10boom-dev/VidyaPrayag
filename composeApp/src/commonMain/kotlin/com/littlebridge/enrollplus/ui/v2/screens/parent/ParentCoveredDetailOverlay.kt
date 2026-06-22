package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentSyllabusData
import com.littlebridge.enrollplus.feature.parent.presentation.CoveredUnit
import com.littlebridge.enrollplus.ui.v2.components.VStatusDot
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * In-app detail overlay for "covered today" — NOT a separate screen. It mounts above the
 * dashboard (scrim + slide-up sheet), animated in/out, and is dismissed by the back handler
 * or the scrim/close button (wired by the host). Shows today's covered topics first, then
 * the full per-subject syllabus progress, all from the real /syllabus feed.
 */
@Composable
fun ParentCoveredDetailOverlay(
    visible: Boolean,
    coveredToday: List<CoveredUnit>,
    syllabus: ParentSyllabusData?,
    schoolDayEnded: Boolean,
    onDismiss: () -> Unit,
) {
    val c = VTheme.colors

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            // scrim — tap to dismiss
            val scrimIx = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.navyDeep.copy(alpha = 0.34f))
                    .clickable(interactionSource = scrimIx, indication = null) { onDismiss() },
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Sheet(
                    coveredToday = coveredToday,
                    syllabus = syllabus,
                    schoolDayEnded = schoolDayEnded,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun Sheet(
    coveredToday: List<CoveredUnit>,
    syllabus: ParentSyllabusData?,
    schoolDayEnded: Boolean,
    onDismiss: () -> Unit,
) {
    val c = VTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.82f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(c.lavender)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp),
    ) {
        // grabber + header
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(999.dp)).background(c.hairline))
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Covered today",
                    style = VTheme.type.h2.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    if (schoolDayEnded) "End-of-day summary" else "Updating live through the day",
                    style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp),
                )
            }
            val closeIx = remember { MutableInteractionSource() }
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.card)
                    .clickable(interactionSource = closeIx, indication = null) { onDismiss() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(com.littlebridge.enrollplus.ui.v2.components.VIcons.Close, contentDescription = "Close", tint = c.ink2, modifier = Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── today's topics ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Today's topics", style = VTheme.type.labelStrong.colored(c.ink2))
                if (coveredToday.isEmpty()) {
                    Text(
                        "No topics logged today yet",
                        style = VTheme.type.body.colored(c.ink3).copy(fontSize = 13.sp),
                    )
                } else {
                    coveredToday.forEach { u ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Semantic: a topic that's been COVERED reads as success green (the same
                            // "done = green" language used by attendance present + syllabus checks).
                            VStatusDot(color = c.successInk, size = 7.dp)
                            Column {
                                Text(u.title, style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp))
                                Text(u.subject, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp))
                            }
                        }
                    }
                }
            }

            // ── per-subject coverage ────────────────────────────────────────
            val subjects = syllabus?.subjects.orEmpty()
            if (subjects.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Syllabus coverage", style = VTheme.type.labelStrong.colored(c.ink2))
                    subjects.forEach { s ->
                        // Each subject carries its own harmonious hue (keyed by name → stable
                        // everywhere), so the coverage list reads expressive, not a violet block.
                        val tint = parentSubjectColor(c, s.subject)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(s.subject, style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp))
                                Text(
                                    "${s.progress}%",
                                    style = VTheme.type.dataSm.colored(tint).copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                )
                            }
                            OverlayTintedBar(value = s.progress.toFloat(), fill = tint)
                            val coveredCount = s.units.count { it.isCovered }
                            Text(
                                "$coveredCount of ${s.units.size} units covered",
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
 * A premium tinted progress bar with an animated fill — used for per-subject coverage in the
 * sheet so each subject can carry its own harmonious hue (VProgressBar only takes a tone enum).
 */
@Composable
private fun OverlayTintedBar(value: Float, fill: Color) {
    val c = VTheme.colors
    val clamped = value.coerceIn(0f, 100f) / 100f
    val animated by animateFloatAsState(targetValue = clamped, animationSpec = tween(600))
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.cream),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(fill),
        )
    }
}
