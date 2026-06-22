package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.LinkChildState
import com.littlebridge.enrollplus.feature.parent.presentation.LinkChildViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * ParentLinkChildScreenV2 — pixel-faithful Compose copy of `Auth.tsx → ParentLinkChild`.
 *
 * The 3-step "link your child" wizard the Figma prototype renders:
 *   1. **Tell us about you** — full name + preferred language tags.
 *   2. **Find your child's school** — search field + a real "Match" school result card.
 *   3. **Link your child** — roll/admission field + the resolved-child preview card.
 *
 * **Wired to the real [LinkChildViewModel]** (`shared/`) →
 * `GET /api/v1/parent/schools/search` + `POST /api/v1/parent/link-child`. MockV2 is no longer
 * referenced (report §5.3, SWEEP-A). The bottom CTA searches in step 2, links in step 3, and only
 * calls [onDone] once the backend confirms the link.
 */
@Composable
fun ParentLinkChildScreenV2(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: LinkChildViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    ParentLinkChildContent(
        state = state,
        onDone = onDone,
        onBack = onBack,
        onFullNameChange = viewModel::onFullNameChange,
        onLanguageChange = viewModel::onLanguageChange,
        onSchoolQueryChange = viewModel::onSchoolQueryChange,
        onRollNumberChange = viewModel::onRollNumberChange,
        onSearch = viewModel::searchSchools,
        onLink = viewModel::linkChild,
        modifier = modifier.statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    )
}

/** Stateless body — also used by the @Preview (no MockV2 in the live path). */
@Composable
private fun ParentLinkChildContent(
    state: LinkChildState,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onFullNameChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onSchoolQueryChange: (String) -> Unit,
    onRollNumberChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLink: (() -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val d = VTheme.dimens
    val total = 3

    var step by remember { mutableIntStateOf(1) }
    val fullName = state.fullName
    val language = state.language
    val schoolQuery = state.schoolQuery
    val rollNo = state.rollNumber

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            // §11 cross-platform safe areas (Android + iOS, common code).
            .statusBarsPadding()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(40.dp))
        // §5: React `Label` component = labelStrong (uppercase 11/700/0.10em).
        VLabel("Step $step of $total")
        Spacer(Modifier.height(d.sm))
        StepBars(current = step, total = total)

        // §13.2 — Crossfade step content for a smooth swap (no slide inside a verticalScroll,
        // which would break the parent's height measurement). 240ms tween matches the React
        // step indicator timing.
        Crossfade(targetState = step, animationSpec = tween(240), label = "linkStep") { current ->
        Column {
        when (current) {
            1 -> {
                Spacer(Modifier.height(d.lg))
                Text("Tell us about you", style = VTheme.type.h1.colored(c.ink))
                Text(
                    "So your child's school knows who to send updates to.",
                    style = VTheme.type.body.colored(c.ink2),
                )
                Spacer(Modifier.height(d.lg))
                VInput(
                    value = fullName,
                    onValueChange = onFullNameChange,
                    label = "Your full name",
                    placeholder = "e.g. Sneha Sharma",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(d.md))
                VLabel("Preferred language")
                Spacer(Modifier.height(d.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(d.sm)) {
                    VTag(text = "English", active = language == "English", onClick = { onLanguageChange("English") })
                    VTag(text = "हिन्दी", active = language == "हिन्दी", onClick = { onLanguageChange("हिन्दी") })
                }
            }

            2 -> {
                Spacer(Modifier.height(d.lg))
                Text("Find your child's school", style = VTheme.type.h1.colored(c.ink))
                Text(
                    "Type the school name. We'll match it against schools using VidyaSetu.",
                    style = VTheme.type.body.colored(c.ink2),
                )
                Spacer(Modifier.height(d.lg))
                VInput(
                    value = schoolQuery,
                    onValueChange = onSchoolQueryChange,
                    placeholder = "Search by school name",
                    leadingIcon = VIcons.Search,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(d.sm))
                // §5: search action — runs the real GET /schools/search.
                VButton(
                    text = if (state.isSearching) "Searching…" else "Search",
                    onClick = onSearch,
                    full = true,
                    size = VButtonSize.Md,
                    tone = VButtonTone.Navy,
                    soft = true,
                    enabled = !state.isSearching && schoolQuery.isNotBlank(),
                )
                Spacer(Modifier.height(d.sm))
                when {
                    state.searchError != null -> {
                        Text(
                            state.searchError ?: "Something went wrong",
                            style = VTheme.type.caption.colored(Color(0xFF7A1C18)),
                        )
                    }
                    state.matches.isEmpty() -> {
                        Text(
                            "Search for your child's school to see matches.",
                            style = VTheme.type.caption.colored(c.ink2),
                        )
                    }
                    else -> {
                        state.matches.forEach { match ->
                            val selected = state.selectedSchool?.id == match.id
                            VCard(modifier = Modifier.fillMaxWidth()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // §5: React match-icon circle = solid var(--arctic)=teal, dark glyph (Auth.tsx L294).
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(c.teal),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(VIcons.GraduationCap, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(d.md))
                                    Column(Modifier.weight(1f)) {
                                        Text(match.name, style = VTheme.type.bodyStrong.colored(c.ink))
                                        Text("${match.city} • ${match.board}", style = VTheme.type.caption.colored(c.ink2))
                                    }
                                    if (selected) {
                                        VBadge(text = "Match", tone = VBadgeTone.Arctic)
                                    }
                                }
                            }
                            Spacer(Modifier.height(d.sm))
                        }
                    }
                }
            }

            else -> {
                Spacer(Modifier.height(d.lg))
                Text("Link your child", style = VTheme.type.h1.colored(c.ink))
                Text(
                    "Enter the roll or admission number assigned by the school.",
                    style = VTheme.type.body.colored(c.ink2),
                )
                Spacer(Modifier.height(d.lg))
                VInput(
                    value = rollNo,
                    onValueChange = onRollNumberChange,
                    label = "Roll / admission number",
                    placeholder = "e.g. 02",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(d.md))
                val linked = state.linkedChild
                when {
                    state.linkError != null -> {
                        Text(
                            state.linkError ?: "Could not link your child",
                            style = VTheme.type.caption.colored(Color(0xFF7A1C18)),
                        )
                    }
                    // RA-48: a submitted request that the school admin must approve.
                    // We DON'T route into the dashboard; we confirm it's awaiting review.
                    state.linkPending && linked != null -> {
                        VCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VAvatar(name = linked.childName, src = linked.profilePhotoUrl, size = 48.dp)
                                Spacer(Modifier.width(d.md))
                                Column(Modifier.weight(1f)) {
                                    Text(linked.childName, style = VTheme.type.bodyStrong.colored(c.ink))
                                    Text(
                                        "Request sent — awaiting ${state.selectedSchool?.name ?: "school"} approval",
                                        style = VTheme.type.caption.colored(c.ink2),
                                    )
                                }
                                Icon(VIcons.Clock, contentDescription = null, tint = Color(0xFFB7791F), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    linked != null -> {
                        // §5: resolved-child preview — only shown once the backend confirms the link.
                        VCard(modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                VAvatar(name = linked.childName, src = linked.profilePhotoUrl, size = 48.dp)
                                Spacer(Modifier.width(d.md))
                                Column(Modifier.weight(1f)) {
                                    Text(linked.childName, style = VTheme.type.bodyStrong.colored(c.ink))
                                    Text(
                                        "Class ${linked.className} • Roll ${linked.roll}",
                                        style = VTheme.type.caption.colored(c.ink2),
                                    )
                                }
                                // §5: React resolved-child check = #155e3a (Auth.tsx L319).
                                Icon(VIcons.Check, contentDescription = null, tint = Color(0xFF155E3A), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    else -> {
                        Text(
                            "We'll match this against ${state.selectedSchool?.name ?: "your school"}'s records when you tap Finish.",
                            style = VTheme.type.caption.colored(c.ink2),
                        )
                    }
                }
            }
        }
        }
        }

        Spacer(Modifier.height(d.xl))
        // §5: React has a SINGLE CTA (Continue / Finish) with a trailing ArrowRight; no Back button.
        // Step 2 requires a selected school before advancing; step 3 links via the backend and only
        // calls onDone() once the link is confirmed (handled in the VM's onSuccess callback).
        val ctaText = when {
            step < total -> "Continue"
            state.isLinking -> "Linking…"
            // RA-48: once a request is pending approval the only forward action is
            // to leave the wizard; a fresh roll cannot be re-submitted from here.
            state.linkPending -> "Done"
            else -> "Finish & open dashboard"
        }
        val ctaEnabled = when {
            step == 2 -> state.selectedSchool != null
            step < total -> true
            state.linkPending -> true
            else -> !state.isLinking && rollNo.isNotBlank()
        }
        VButton(
            text = ctaText,
            onClick = {
                when {
                    step < total -> step++
                    // RA-48: a pending request returns the parent to wherever onDone
                    // routes (typically the parent home), where they'll see no child
                    // yet and the "awaiting approval" empty state.
                    state.linkPending -> onDone()
                    else -> onLink(onDone)
                }
            },
            full = true,
            size = VButtonSize.Lg,
            tone = VButtonTone.Teal,
            soft = false,
            enabled = ctaEnabled,
            trailing = { Icon(VIcons.ArrowRight, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
        Spacer(Modifier.height(d.xl))
    }
}

@Composable
private fun StepBars(current: Int, total: Int) {
    val c = VTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d6())) {
        repeat(total) { i ->
            // §13.2 — animate the bar fill instead of swapping colors instantly.
            val active = i + 1 <= current
            val targetColor by animateColorAsState(
                targetValue = if (active) c.teal else Color(0x14080808),
                animationSpec = tween(durationMillis = 250),
                label = "linkStepBar$i",
            )
            Box(
                Modifier
                    .weight(1f)
                    // React: h-1 (4dp) bar — filled var(--arctic)=teal, empty rgba(8,8,8,0.08).
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(targetColor),
            )
        }
    }
}

private fun d6() = 6.dp
