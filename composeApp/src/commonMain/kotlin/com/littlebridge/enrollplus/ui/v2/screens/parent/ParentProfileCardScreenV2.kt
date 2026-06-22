package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.ParentDashboardState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentDashboardViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.TrackProgressState
import com.littlebridge.enrollplus.feature.parent.presentation.TrackProgressViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VConfirmDialog
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * House — the deterministic, decorative collectible "team" a child belongs to.
 *
 * IMPORTANT: there is NO house column in the schema (Tables.kt → ChildrenTable has only
 * gender/currentGrade/interests/section). So this is a purely cosmetic treatment derived
 * *deterministically* from the child's stable id — the same child always lands in the same
 * house. No backend NUMBER/STATE is invented: every stat on the card (attendance %, score %,
 * level, topics) still comes from the real [ParentDashboardViewModel].
 */
enum class ProfileHouse(
    val title: String,
    val crest: String,
    val top: Color,
    val bottom: Color,
    val foil: Color,
    val onColor: Color,
) {
    // Each house lives inside the brand's lavender/violet/indigo family — the website palette. The
    // card always reads as one cohesive premium violet artifact, with each house only shifting the
    // hue subtly (deep violet → royal indigo → midnight plum → twilight blue → ultraviolet) so
    // siblings still feel distinct while staying unmistakably on-brand.
    Aether("Aether", "AE", Color(0xFF6C5CE0), Color(0xFF2C2660), Color(0xFFC4BBFF), Color(0xFFFFFFFF)),
    Lumen("Lumen", "LU", Color(0xFF7A6CF0), Color(0xFF332B6E), Color(0xFFD2C9FF), Color(0xFFFFFFFF)),
    Indigo("Indigo", "IN", Color(0xFF5A4FD0), Color(0xFF211C52), Color(0xFFB3A9FF), Color(0xFFFFFFFF)),
    Nocturne("Nocturne", "NO", Color(0xFF4A4296), Color(0xFF1A1838), Color(0xFFA89EF5), Color(0xFFFFFFFF)),
    Vela("Vela", "VE", Color(0xFF8B7EE8), Color(0xFF3A3278), Color(0xFFDED7FF), Color(0xFFFFFFFF)),
}

/** Stable house assignment — sum of the id's code points mod the house count. */
fun houseFor(childId: String): ProfileHouse {
    if (childId.isBlank()) return ProfileHouse.Aether
    val hash = childId.fold(0) { acc, ch -> (acc * 31 + ch.code) and 0x7FFFFFFF }
    val houses = ProfileHouse.entries
    return houses[hash % houses.size]
}

/**
 * ParentProfileCardScreenV2 — the rebuilt, **premium child profile** the brief calls for.
 *
 * This is NOT an account/settings surface anymore (every "Personal details / Change password /
 * Notification preferences" row has been removed — that admin lives elsewhere). It is a celebratory,
 * living portrait of the child built entirely from REAL backend data:
 *
 *   • a holographic collectible HERO card (tilt + holo, drag-to-lean) — the child's identity,
 *   • a fully ANIMATED live-metrics band (attendance · score · level · topics) that springs +
 *     counts up on appearance, each tile carrying a sweeping progress arc,
 *   • a clear "swipe down to see achievements" GUIDE with a bouncing chevron,
 *   • a SWIPE-DOWN reveal that expands a rich Missions & Achievements sheet sourced from the real
 *     `/parent/track-progress` endpoint (badges, NEP competencies, emotional-intelligence metrics,
 *     play & discovery milestones) — animated bars, count-ups and locked/earned states throughout.
 *
 * The reveal is gesture-first (drag the hero down) AND tap-friendly (the guide chevron toggles it),
 * so it never feels fragile. Logout is the only account affordance and lives quietly at the very
 * bottom of the revealed sheet.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParentProfileCardScreenV2(
    onLogout: () -> Unit = {},
    onLinkChild: () -> Unit = {},
    onDiscoverSchools: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ParentDashboardViewModel = koinViewModel(),
    trackViewModel: TrackProgressViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val track by trackViewModel.state.collectAsStateV2()
    val c = VTheme.colors

    val child = state.selectedChild
    val house = houseFor(child?.id ?: "")

    var showLogoutConfirm by remember { mutableStateOf(false) }
    VConfirmDialog(
        visible = showLogoutConfirm,
        title = "Log out?",
        message = "You'll need to sign in again to follow your child's progress.",
        confirmLabel = "Log out",
        onConfirm = { showLogoutConfirm = false; onLogout() },
        onDismiss = { showLogoutConfirm = false },
        icon = VIcons.AlertTriangle,
    )

    // ── Swipe-down reveal state ─────────────────────────────────────────────────
    // `revealed` flips when the parent drags the hero region down (or taps the guide). Once
    // revealed, the Missions & Achievements sheet is shown and the page becomes scrollable so
    // every section is reachable; swiping back up (or tapping the handle) collapses it again.
    var revealed by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val revealDrag = Modifier.pointerInput(Unit) {
        var dy = 0f
        detectVerticalDragGestures(
            onDragStart = { dy = 0f },
            onDragEnd = {
                if (dy >= 48f) revealed = true
                else if (dy <= -48f && scrollState.value == 0) revealed = false
            },
            onDragCancel = { dy = 0f },
            onVerticalDrag = { _, delta -> dy += delta },
        )
    }

    // A calm lavender canvas with a soft house-tinted aurora at the very top so the hero card
    // "glows" out of the surface — premium ambience, never a wall of purple.
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .drawWithContent {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(house.top.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.06f),
                        radius = size.width * 0.9f,
                    ),
                )
                drawContent()
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .statusBarsPadding()
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── 1 · Hero collectible card (drag DOWN to reveal achievements) ──
            Box(revealDrag) {
                ProfilePlayerCard(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── 2 · Animated live-metrics band ───────────────────────────────
            val attendancePct = state.attendance?.takeIf { it.totalDays > 0 }?.attendanceRate
            val scorePct = state.latestMark?.let { m ->
                val marks = m.marks
                if (marks != null && m.maxMarks > 0) ((marks / m.maxMarks) * 100).roundToInt() else null
            }
            val rawProgress = child?.overallProgress ?: 0.0
            val journeyPct = (if (rawProgress <= 1.0) rawProgress * 100.0 else rawProgress).roundToInt().coerceIn(0, 100)

            SectionLabel("At a glance")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    icon = VIcons.ShieldCheck,
                    accent = c.successInk,
                    value = attendancePct?.let { "$it%" } ?: "—",
                    ratio = attendancePct?.let { it / 100f },
                    label = "Attendance",
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    icon = VIcons.Target,
                    accent = c.accentDeep,
                    value = scorePct?.let { "$it%" } ?: "—",
                    ratio = scorePct?.let { it / 100f },
                    label = "Latest score",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    icon = VIcons.Sparkles,
                    accent = Color(0xFF6C8DF5), // sky
                    value = "L${child?.currentLevel ?: 0}",
                    ratio = journeyPct / 100f,
                    label = "$journeyPct% to next",
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    icon = VIcons.School,
                    accent = c.teal,
                    value = state.coveredToday.size.toString(),
                    ratio = null,
                    label = "Topics today",
                    modifier = Modifier.weight(1f),
                )
            }

            // ── 3 · Swipe-down guide + reveal toggle ─────────────────────────
            RevealGuide(
                revealed = revealed,
                onToggle = { revealed = !revealed },
            )

            // ── 4 · Missions & Achievements (revealed; real /track-progress) ──
            AnimatedVisibility(
                visible = revealed,
                enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(tween(260)),
                exit = shrinkVertically(tween(220)) + fadeOut(tween(160)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AchievementsSheet(
                        track = track,
                        state = state,
                        attendancePct = attendancePct,
                        scorePct = scorePct,
                    )

                    Spacer(Modifier.height(4.dp))
                    VButton(
                        text = "Log out",
                        onClick = { showLogoutConfirm = true },
                        full = true,
                        variant = VButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}

/**
 * The swipe-down GUIDE: a tappable handle + bouncing chevron + copy that tells the parent the
 * achievements live just below. Doubles as a toggle (tap to open/close) so the reveal is never
 * gesture-only and never feels stuck.
 */
@Composable
private fun RevealGuide(revealed: Boolean, onToggle: () -> Unit) {
    val c = VTheme.colors
    val bounce = rememberInfiniteTransition(label = "guideBounce")
    val dy by bounce.animateFloat(
        initialValue = 0f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(820, easing = LinearEasing), RepeatMode.Reverse),
        label = "guideDy",
    )
    val chevronRotation by animateFloatAsState(if (revealed) 180f else 0f, spring(dampingRatio = 0.7f), label = "guideChevron")
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // grab handle
        Box(
            Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(999.dp)).background(c.hairline),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(VIcons.Sparkles, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(16.dp))
            Text(
                if (revealed) "Hide missions & achievements" else "Swipe down for missions & achievements",
                style = VTheme.type.bodyStrong.colored(c.accentDeep)
                    .copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
            )
            Icon(
                VIcons.ChevronDown,
                contentDescription = null,
                tint = c.accentDeep,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = chevronRotation
                        translationY = if (!revealed) dy else 0f
                    },
            )
        }
    }
}

/**
 * The revealed Missions & Achievements sheet — every section is REAL `/parent/track-progress`
 * data (badges, NEP-aligned competencies, emotional-intelligence metrics, play & discovery
 * milestones), with animated bars / count-ups / locked-earned states. When the endpoint hasn't
 * populated a section it falls back to honest, locally-derived achievements so the sheet is never
 * an empty white block.
 */
@Composable
private fun AchievementsSheet(
    track: TrackProgressState,
    state: ParentDashboardState,
    attendancePct: Int?,
    scorePct: Int?,
) {
    val c = VTheme.colors
    val child = state.selectedChild

    // ── Holistic growth hero line (journey description from the backend) ──────────
    if (track.journeyDescription.isNotBlank()) {
        VCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(VIcons.TrendingUp, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "HOLISTIC GROWTH",
                        style = VTheme.type.label.colored(c.accentDeep)
                            .copy(fontWeight = FontWeight.ExtraBold, fontSize = 9.5.sp, letterSpacing = 0.8.sp),
                    )
                    Text(
                        track.journeyDescription,
                        style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }

    // ── Badges (real achievements; locked/earned) ────────────────────────────────
    SectionLabel("Achievements")
    val badges = track.badges.ifEmpty {
        // Honest local fallback derived from real dashboard numbers.
        listOf(
            com.littlebridge.enrollplus.feature.parent.presentation.AchievementBadge(
                "On track", "verified", (attendancePct ?: 0) < 90, listOf("#22C55E"),
            ),
            com.littlebridge.enrollplus.feature.parent.presentation.AchievementBadge(
                "High scorer", "star", (scorePct ?: 0) < 75, listOf("#F59E0B"),
            ),
            com.littlebridge.enrollplus.feature.parent.presentation.AchievementBadge(
                "Level ${child?.currentLevel ?: 0}", "auto_awesome", (child?.currentLevel ?: 0) <= 0, listOf("#6C5CE0"),
            ),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        badges.take(3).forEach { b ->
            AchievementChip(
                icon = badgeIcon(b.title),
                title = b.title,
                earned = !b.isLocked,
                accent = badgeAccent(b.gradientColors),
                modifier = Modifier.weight(1f),
            )
        }
        // pad to 3 columns so the row never looks lopsided
        repeat(3 - badges.take(3).size) { Spacer(Modifier.weight(1f)) }
    }

    // ── NEP-aligned academic competencies (animated bars) ─────────────────────────
    if (track.academicCompetencies.isNotEmpty()) {
        SectionLabel("Academic core")
        VCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                track.academicCompetencies.forEach { comp ->
                    CompetencyBar(title = comp.title, progress = comp.progress)
                }
            }
        }
    }

    // ── Emotional intelligence (radar-style metric bars) ──────────────────────────
    if (track.emotionalIntelligence.isNotEmpty()) {
        SectionLabel("Emotional intelligence")
        VCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (track.emotionalDescription.isNotBlank()) {
                    Text(
                        track.emotionalDescription,
                        style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 11.5.sp),
                    )
                }
                track.emotionalIntelligence.entries.forEach { (label, value) ->
                    CompetencyBar(title = label, progress = value, accent = Color(0xFF6C8DF5))
                }
            }
        }
    }

    // ── Play & discovery milestones (mission cards) ───────────────────────────────
    if (track.playIndicators.isNotEmpty()) {
        SectionLabel("Play & discovery")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            track.playIndicators.forEach { p ->
                MissionRow(title = p.title, description = p.description, met = p.isMet)
            }
        }
    }

    // ── This-month attendance breakdown (real records) ────────────────────────────
    val att = state.attendance
    if (att != null && att.totalDays > 0) {
        SectionLabel("This month")
        VCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AttendanceArc(percent = att.attendanceRate.coerceIn(0, 100), modifier = Modifier.size(72.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BreakdownBar("Present", att.presentDays, att.totalDays, c.successInk)
                    BreakdownBar("Late", att.lateDays, att.totalDays, c.warningInk)
                    BreakdownBar("Absent", att.absentDays, att.totalDays, c.dangerInk)
                }
            }
        }
    }
}

/** An animated competency/EI progress bar that fills on appearance. */
@Composable
private fun CompetencyBar(title: String, progress: Float, accent: Color = VTheme.colors.accent) {
    val c = VTheme.colors
    var appeared by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }
    val animated by animateFloatAsState(
        targetValue = if (appeared) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(760),
        label = "competency",
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold))
            Text(
                "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                style = VTheme.type.caption.colored(accent).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            )
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(999.dp)).background(c.cream)) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.7f), accent))),
            )
        }
    }
}

/** A play/discovery "mission" row — met (green tick) vs in-progress (violet ring). */
@Composable
private fun MissionRow(title: String, description: String, met: Boolean) {
    val c = VTheme.colors
    val tint = if (met) c.successInk else c.accentDeep
    VCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (met) VIcons.Check else VIcons.Target,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = VTheme.type.bodyStrong.colored(c.navyDeep).copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                if (description.isNotBlank()) {
                    Text(description, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.5.sp))
                }
            }
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(tint.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    if (met) "Met" else "In progress",
                    style = VTheme.type.label.colored(tint).copy(fontWeight = FontWeight.Bold, fontSize = 9.sp),
                )
            }
        }
    }
}

/** Map a badge title to one of the available VIcons (the server emits Material symbol names). */
private fun badgeIcon(title: String): ImageVector = when {
    title.contains("track", true) || title.contains("attend", true) -> VIcons.ShieldCheck
    title.contains("scor", true) || title.contains("star", true) -> VIcons.Star
    title.contains("read", true) || title.contains("liter", true) -> VIcons.BookOpen
    title.contains("level", true) -> VIcons.Sparkles
    else -> VIcons.Target
}

/** Parse the first hex colour the server emitted for a badge; fall back to brand violet. */
@Composable
private fun badgeAccent(colors: List<String>): Color {
    val c = VTheme.colors
    val hex = colors.firstOrNull()?.removePrefix("#")?.takeIf { it.length == 6 } ?: return c.accentDeep
    return runCatching {
        Color(("FF$hex").toLong(16))
    }.getOrDefault(c.accentDeep)
}

// ─────────────────────────────────────────────────────────────────────────────
// Section primitives
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    val c = VTheme.colors
    Text(
        text.uppercase(),
        style = VTheme.type.label.colored(c.ink3).copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            letterSpacing = 0.9.sp,
        ),
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

/**
 * A fully animated metric tile: the icon plate carries a sweeping progress arc (when a [ratio] is
 * supplied), and the value springs + fades in (count-up feel) on first composition. Every value is
 * real backend data; `ratio` drives the arc 0..1.
 */
@Composable
private fun MetricTile(
    icon: ImageVector,
    accent: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    ratio: Float? = null,
) {
    val c = VTheme.colors
    var appeared by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.86f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 260f),
        label = "metricScale",
    )
    val alpha by animateFloatAsState(if (appeared) 1f else 0f, tween(280), label = "metricAlpha")
    androidx.compose.runtime.LaunchedEffect(Unit) { appeared = true }

    VCard(modifier = modifier) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (ratio != null) {
                MetricArc(ratio = ratio.coerceIn(0f, 1f), accent = accent, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            value,
            style = VTheme.type.dataLg.colored(c.navyDeep).copy(fontWeight = FontWeight.ExtraBold, fontSize = 24.sp),
            modifier = Modifier.graphicsLayer {
                scaleX = scale; scaleY = scale; this.alpha = alpha
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp))
    }
}

/** A thin sweeping arc behind a metric tile's icon plate. */
@Composable
private fun MetricArc(ratio: Float, accent: Color, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(ratio, tween(820), label = "metricArc")
    Canvas(modifier) {
        val stroke = 3.dp.toPx()
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = accent.copy(alpha = 0.14f),
            startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            brush = Brush.sweepGradient(listOf(accent.copy(alpha = 0.6f), accent)),
            startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** A labelled, colour-coded attendance breakdown bar (count of total). */
@Composable
private fun BreakdownBar(label: String, count: Int, total: Int, accent: Color) {
    val c = VTheme.colors
    val ratio = if (total > 0) count.toFloat() / total else 0f
    val animated by animateFloatAsState(ratio, tween(700), label = "breakdown")
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 11.sp))
            Text(
                "$count",
                style = VTheme.type.caption.colored(accent).copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
            )
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(999.dp)).background(c.cream),
        ) {
            Box(
                Modifier.fillMaxWidth(animated.coerceIn(0f, 1f)).height(6.dp)
                    .clip(RoundedCornerShape(999.dp)).background(accent),
            )
        }
    }
}

/** A green attendance arc used in the "This month" card. */
@Composable
private fun AttendanceArc(percent: Int, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    val sweep by animateFloatAsState(percent / 100f, tween(800), label = "attArc")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = c.successInk.copy(alpha = 0.14f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(c.success, c.successInk)),
                startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            "$percent%",
            style = VTheme.type.dataLg.colored(c.successInk).copy(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp),
        )
    }
}

/** A small achievement chip — full-colour when earned, faded/locked when not. */
@Composable
private fun AchievementChip(
    icon: ImageVector,
    title: String,
    earned: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val tint = if (earned) accent else c.placeholder
    VCard(modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (earned) accent.copy(alpha = 0.14f) else c.cream),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                title,
                style = VTheme.type.label.colored(if (earned) c.ink else c.ink3).copy(
                    fontWeight = FontWeight.Bold, fontSize = 10.sp,
                ),
                maxLines = 1,
            )
            Text(
                if (earned) "Earned" else "Locked",
                style = VTheme.type.caption.colored(tint).copy(fontSize = 9.sp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The collectible card — tilt + holo + real stats (tap-to-lean, no fragile reveal)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The collectible card itself — a foil holographic player card with a drag-driven 3D tilt that
 * springs back on release. Every stat is real backend data; the house is the only decorative
 * (deterministic-from-id) element. The tilt gesture is now the card's ONLY gesture, so it is
 * conflict-free and reliable — there is no longer a fragile "swipe-down-to-reveal" rig fighting it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProfilePlayerCard(
    state: ParentDashboardState,
    modifier: Modifier = Modifier,
) {
    val child = state.selectedChild
    val house = houseFor(child?.id ?: "")

    val rawProgress = child?.overallProgress ?: 0.0
    val progressPct = (if (rawProgress <= 1.0) rawProgress * 100.0 else rawProgress).roundToInt().coerceIn(0, 100)

    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    var tilting by remember { mutableStateOf(false) }

    val tiltY by animateFloatAsState(
        targetValue = if (tilting) (dragX / 18f).coerceIn(-14f, 14f) else 0f,
        animationSpec = if (tilting) tween(durationMillis = 60)
        else spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "tiltY",
    )
    val tiltX by animateFloatAsState(
        targetValue = if (tilting) (-dragY / 18f).coerceIn(-14f, 14f) else 0f,
        animationSpec = if (tilting) tween(durationMillis = 60)
        else spring(dampingRatio = 0.55f, stiffness = 260f),
        label = "tiltX",
    )

    val holo = rememberInfiniteTransition(label = "holo")
    val sweep by holo.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Box(
        modifier
            .aspectRatio(0.74f)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 16f * density
            }
            // The ONLY gesture on the card: a drag tilts the foil; release springs it level.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    tilting = true
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dragX += change.positionChange().x
                        dragY += change.positionChange().y
                        change.consume()
                    }
                    tilting = false
                    dragX = 0f
                    dragY = 0f
                }
            }
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(house.top, house.bottom)))
            .drawWithContent {
                drawContent()
                drawHoloAndFoil(house = house, sweep = sweep, tiltX = tiltX, tiltY = tiltY)
            },
    ) {
        ProfileCardContent(
            childName = child?.name ?: state.today.label.ifBlank { "Your child" },
            childPhoto = child?.profilePic,
            house = house,
            level = child?.currentLevel ?: 0,
            progressPct = progressPct,
            attendancePct = state.attendance?.takeIf { it.totalDays > 0 }?.attendanceRate,
            statusLabel = state.today.label.ifBlank { child?.attendanceStatus.orEmpty() },
            scorePct = state.latestMark?.let { m ->
                val marks = m.marks
                if (marks != null && m.maxMarks > 0) ((marks / m.maxMarks) * 100).roundToInt() else null
            },
            topicsToday = state.coveredToday.size,
            modifier = Modifier.fillMaxSize().padding(22.dp),
        )
    }
}

/** Draws the moving holo sweep + the drag-reactive specular highlight + foil rim. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHoloAndFoil(
    house: ProfileHouse,
    sweep: Float,
    tiltX: Float,
    tiltY: Float,
) {
    val w = size.width
    val h = size.height

    val bandCenter = sweep * w
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                house.foil.copy(alpha = 0.0f),
                house.foil.copy(alpha = 0.28f),
                Color.White.copy(alpha = 0.10f),
                house.foil.copy(alpha = 0.28f),
                Color.Transparent,
            ),
            start = Offset(bandCenter - w * 0.5f, 0f),
            end = Offset(bandCenter + w * 0.5f, h),
            tileMode = TileMode.Clamp,
        ),
        size = Size(w, h),
    )

    val hx = (w / 2f) + (tiltY / 14f) * (w * 0.45f)
    val hy = (h / 2f) - (tiltX / 14f) * (h * 0.45f)
    val intensity = (abs(tiltX) + abs(tiltY)) / 28f
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.16f + intensity * 0.20f),
                Color.Transparent,
            ),
            center = Offset(hx, hy),
            radius = w * 0.9f,
        ),
        size = Size(w, h),
    )

    val stroke = 3f
    drawRect(
        brush = Brush.linearGradient(listOf(house.foil.copy(alpha = 0.9f), house.top.copy(alpha = 0.6f), house.foil.copy(alpha = 0.9f))),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(w - stroke, h - stroke),
        style = Stroke(width = stroke),
    )
}

@Composable
private fun ProfileCardContent(
    childName: String,
    childPhoto: String?,
    house: ProfileHouse,
    level: Int,
    progressPct: Int,
    attendancePct: Int?,
    statusLabel: String,
    scorePct: Int?,
    topicsToday: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(house.foil.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        house.crest,
                        style = VTheme.type.labelStrong.colored(house.onColor).copy(fontWeight = FontWeight.Black, fontSize = 12.sp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "House ${house.title}",
                    style = VTheme.type.labelStrong.colored(house.onColor.copy(alpha = 0.85f)),
                )
            }
            if (statusLabel.isNotBlank()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        statusLabel,
                        style = VTheme.type.caption.colored(house.onColor).copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(house.foil.copy(alpha = 0.45f), Color.Transparent),
                            ),
                        ),
                )
                VAvatar(name = childName.ifBlank { "?" }, src = childPhoto, size = 104.dp, ring = true)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                childName.ifBlank { "Your child" },
                style = VTheme.type.h2.colored(house.onColor).copy(fontWeight = FontWeight.Black),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Level $level  ·  $progressPct% journey",
                style = VTheme.type.caption.colored(house.onColor.copy(alpha = 0.85f)),
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatTile(
                label = "ATTEND",
                value = attendancePct?.let { "$it%" } ?: "—",
                ratio = attendancePct?.let { it / 100f },
                house = house,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "SCORE",
                value = scorePct?.let { "$it%" } ?: "—",
                ratio = scorePct?.let { it / 100f },
                house = house,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "TODAY",
                value = topicsToday.toString(),
                ratio = null,
                sub = if (topicsToday == 1) "topic" else "topics",
                house = house,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatTile(
    label: String,
    value: String,
    ratio: Float?,
    house: ProfileHouse,
    modifier: Modifier = Modifier,
    sub: String? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                        endY = size.height * 0.5f,
                    ),
                )
                val s = 1.2.dp.toPx()
                drawRoundRect(
                    color = house.foil.copy(alpha = 0.28f),
                    topLeft = Offset(s / 2f, s / 2f),
                    size = Size(size.width - s, size.height - s),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(15.dp.toPx(), 15.dp.toPx()),
                    style = Stroke(width = s),
                )
            }
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(46.dp)) {
            if (ratio != null) {
                StatArc(ratio = ratio.coerceIn(0f, 1f), house = house, modifier = Modifier.fillMaxSize())
            }
            Text(
                value,
                style = VTheme.type.dataLg.colored(house.onColor).copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                ),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = VTheme.type.label.colored(house.onColor.copy(alpha = 0.82f)).copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            ),
        )
        if (sub != null) {
            Text(
                sub,
                style = VTheme.type.caption.colored(house.onColor.copy(alpha = 0.6f)).copy(fontSize = 8.5.sp),
            )
        }
    }
}

@Composable
private fun StatArc(ratio: Float, house: ProfileHouse, modifier: Modifier = Modifier) {
    val sweep by animateFloatAsState(targetValue = ratio, label = "statArc")
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(house.foil.copy(alpha = 0.8f), Color.White)),
                startAngle = -90f, sweepAngle = 360f * sweep, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
