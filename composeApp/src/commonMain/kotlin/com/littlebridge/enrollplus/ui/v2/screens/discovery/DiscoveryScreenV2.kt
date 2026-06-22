package com.littlebridge.enrollplus.ui.v2.screens.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.littlebridge.enrollplus.feature.schools.presentation.DiscoveredSchool
import com.littlebridge.enrollplus.feature.schools.presentation.SchoolDiscoveryState
import com.littlebridge.enrollplus.feature.schools.presentation.SchoolDiscoveryViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VComingSoon
import com.littlebridge.enrollplus.ui.v2.components.VDivider
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/** Internal view state for the discovery flow (mirrors React `DiscoveryApp` view union). */
private enum class DiscoveryView { List, Profile, Compare }

// React SRI pills use a fixed navy-blue ink (#0a3a76) on an arctic-blue (#C8DEFF) tint —
// independent of the warm/night remap. §8 / charts.tsx SRI styling.
private val SriInk = Color(0xFF0A3A76)
private val SriBg = Color(0xFFC8DEFF)

/**
 * DiscoveryScreenV2 — the marketplace, wired to the real
 * [SchoolDiscoveryViewModel] → `GET /api/v1/parent/schools/discover` endpoint
 * (BACKEND_GAPS.md §3 + Phase C batch 4). The previous MockV2 driver is gone;
 * the screen now renders genuine schools sourced from the server.
 *
 * Fields the backend does not currently send (board, type, fee range, medium, board result)
 * are surfaced as `Coming Soon` placeholders rather than fabricated — LAW 6 / no guessed
 * data. Per the React design, the search field + tag chips remain visible because they are
 * the page's primary navigation affordance, but server-side filtering is not yet wired
 * (Phase D will plumb `lat/lng` + chip filters into [SchoolDiscoveryViewModel.load]).
 */
@Composable
fun DiscoveryScreenV2(
    modifier: Modifier = Modifier,
    onOpenSchool: (String) -> Unit = {},
    // When set (e.g. parent-portal overlay host), the header "Exit" pops the overlay
    // instead of routing through [onOpenSchool] (the unauth NavGraphV2 path).
    onExit: (() -> Unit)? = null,
    // When true the marketplace is hosted *inside* another premium shell (the unlinked-parent
    // two-tab landing): its own hero title block and "Exit" pill are suppressed (the host already
    // owns the header + tab switcher), and it skips status-bar padding the host already applied —
    // so the two surfaces read as one continuous screen, not a screen-within-a-screen.
    embedded: Boolean = false,
    // When the marketplace is hosted in the unlinked-parent landing, this routes the parent to the
    // "Link a child" tab. It powers the "already with a partner school? Link your child" closing CTA
    // at the foot of the school list — the gentle off-ramp for a parent who discovers their child's
    // school is already on VidyaPrayag. Null (standalone / unauth) hides the CTA entirely.
    onAlreadyLinked: (() -> Unit)? = null,
    viewModel: SchoolDiscoveryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    var view by remember { mutableStateOf(DiscoveryView.List) }
    var active by remember { mutableStateOf<DiscoveredSchool?>(null) }
    var compare by remember { mutableStateOf(setOf<String>()) }

    // If the profile view was requested but `active` is null (defensive), fall back to List.
    val effectiveView = if (view == DiscoveryView.Profile && active == null) DiscoveryView.List else view
    when (effectiveView) {
        DiscoveryView.List -> DiscoveryList(
            // Embedded: the host (unlinked-parent landing) already consumed the status-bar inset,
            // so we don't re-apply it here (no double padding). Standalone keeps its own insets.
            modifier = (if (embedded) modifier else modifier.statusBarsPadding())
                .imePadding()
                .navigationBarsPadding(),
            state = state,
            onQuery = viewModel::setQuery,
            onRetry = viewModel::load,
            compare = compare,
            onToggleCompare = { id ->
                compare = if (id in compare) compare - id else if (compare.size < 3) compare + id else compare
            },
            onOpen = { s -> active = s; view = DiscoveryView.Profile },
            onCompare = { if (compare.isNotEmpty()) view = DiscoveryView.Compare },
            // Header "Exit" sends the user out of the marketplace; in the unauth flow we pass
            // an empty id to the host, in the parent portal the dedicated [onExit] pops the overlay.
            onExit = { onExit?.invoke() ?: onOpenSchool(active?.id.orEmpty()) },
            // Hide the duplicate hero header when hosted inside the two-tab landing.
            embedded = embedded,
            // The closing "already linked? Link your child" CTA — only when the host wires it.
            onAlreadyLinked = onAlreadyLinked,
        )
        DiscoveryView.Profile -> {
            // Smart-cast guard: `active` is a `var` in this scope, so we capture into a local.
            val s = active
            if (s != null) {
                SchoolProfile(
                    modifier = modifier.statusBarsPadding()
                        .imePadding()
                        .navigationBarsPadding(),
                    school = s,
                    onBack = { view = DiscoveryView.List },
                )
            }
        }
        DiscoveryView.Compare -> SchoolCompare(
            modifier = modifier.statusBarsPadding()
                .imePadding()
                .navigationBarsPadding(),
            items = state.schools.filter { it.id in compare },
            onBack = { view = DiscoveryView.List },
        )
    }
}

@Composable
private fun DiscoveryList(
    modifier: Modifier,
    state: SchoolDiscoveryState,
    onQuery: (String) -> Unit,
    onRetry: () -> Unit,
    compare: Set<String>,
    onToggleCompare: (String) -> Unit,
    onOpen: (DiscoveredSchool) -> Unit,
    onCompare: () -> Unit,
    onExit: () -> Unit,
    embedded: Boolean = false,
    onAlreadyLinked: (() -> Unit)? = null,
) {
    val c = VTheme.colors

    // Client-side query filter (substring against name + location). The endpoint already
    // supports `city=` for proper server-side filtering — Phase D will switch this over
    // once the design specifies per-key behaviour.
    val filtered = remember(state.schools, state.query) {
        val q = state.query.trim()
        if (q.isBlank()) state.schools
        else state.schools.filter {
            it.name.contains(q, ignoreCase = true) || it.location.contains(q, ignoreCase = true)
        }
    }

    Column(modifier.fillMaxSize().background(c.background)) {
        // header — when embedded inside the unlinked-parent landing the host already owns the
        // title + tab switcher, so we drop the duplicate hero title block and the "Exit" pill and
        // render a clean, transparent search-only header that sits flush under the segmented control.
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (embedded) Modifier else Modifier.background(c.card))
                .padding(horizontal = 20.dp)
                .padding(top = if (embedded) 8.dp else 20.dp, bottom = 12.dp),
        ) {
            if (!embedded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        VLabel("Discover")
                        Text("Find your child's school", style = VTheme.type.h2.colored(c.ink), modifier = Modifier.padding(top = 4.dp))
                    }
                    val exitInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.cream)
                            .clickable(interactionSource = exitInteraction, indication = null) { onExit() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        // React label is "Exit" (leaves the discovery flow).
                        Text("Exit", style = VTheme.type.caption.colored(c.ink2).copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            VInput(
                value = state.query,
                onValueChange = onQuery,
                placeholder = "Find schools near you or by name",
                leadingIcon = VIcons.Search,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // Tag chips are unfiltered today — they will become real filter pills once the
            // server-side filter contract for board / fee-range / SRI is finalised (Phase D).
            // Keeping the visual affordance preserves the React fidelity per LAW 5.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Clean, premium text pills (no emoji glyphs — those read cheap). Active chip uses
                // the Parents Portal lavender accent, consistent with every other filter row.
                listOf("Within 3 km", "CBSE", "Fee range", "Type", "SRI rating").forEachIndexed { i, f ->
                    com.littlebridge.enrollplus.ui.v2.components.VTag(text = f, active = i == 0, accentActive = true)
                }
            }
        }

        // body — Loading · Error · Empty · Content (LAW 3)
        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            isEmpty = !state.isLoading && state.errorMessage == null && filtered.isEmpty(),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            emptyTitle = if (state.query.isBlank()) "No schools yet" else "No matches",
            emptyBody = if (state.query.isBlank()) {
                "Schools registered on VidyaPrayag will appear here."
            } else {
                "Try another name or city."
            },
            emptyIcon = VIcons.Search,
            onRetry = onRetry,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                filtered.forEach { s ->
                    SchoolCard(
                        s,
                        inCompare = s.id in compare,
                        onToggleCompare = { onToggleCompare(s.id) },
                        onOpen = { onOpen(s) },
                    )
                }
                // ── Closing CTA: "already with a partner school? Link your child" ───────────
                // The gentle off-ramp for a parent browsing the marketplace who realises their
                // child's school is already on VidyaPrayag — sends them straight to the link flow.
                // Only when hosted in the unlinked-parent landing (onAlreadyLinked wired).
                onAlreadyLinked?.let { go ->
                    Spacer(Modifier.height(4.dp))
                    AlreadyLinkedCta(onLink = go)
                }
            }
        }

        if (compare.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.navy)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "${compare.size} school${if (compare.size > 1) "s" else ""} selected",
                    style = VTheme.type.caption.colored(Color.White),
                    modifier = Modifier.weight(1f),
                )
                VButton(text = "Compare now", onClick = onCompare, size = VButtonSize.Sm, tone = VButtonTone.Sky, soft = false)
            }
        }
    }
}

@Composable
private fun SchoolCard(
    s: DiscoveredSchool,
    inCompare: Boolean,
    onToggleCompare: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = VTheme.colors
    VCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(144.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            AsyncImage(
                model = s.image,
                contentDescription = s.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))),
                ),
            )
            // Distance pill — only shown when the server populated `distance_km`
            // (i.e. when lat/lng were available). LAW 6: don't fabricate "1.8 km" otherwise.
            s.distanceLabel?.let { dist ->
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(VIcons.MapPin, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(11.dp))
                    Text(dist, style = VTheme.type.label.colored(c.ink).copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = VTheme.type.h3.colored(c.ink))
                Spacer(Modifier.height(6.dp))
                // React card badges: board (arctic) + type/location (neutral). Board + medium
                // are now real `schools` columns surfaced by the discover endpoint.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.board?.let { VBadge(text = it, tone = VBadgeTone.Arctic) }
                    VBadge(text = s.location, tone = VBadgeTone.Neutral)
                }
                s.medium?.let { med ->
                    Spacer(Modifier.height(6.dp))
                    Text("$med medium", style = VTheme.type.caption.colored(c.ink2))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // React SRI pill: bg rgba(200,222,255,0.30) (arctic #C8DEFF@30%), ink #0a3a76.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFC8DEFF).copy(alpha = 0.30f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(VIcons.Star, contentDescription = null, tint = SriInk, modifier = Modifier.size(12.dp))
                    Text(formatRating(s.rating), style = VTheme.type.dataSm.colored(SriInk))
                }
                Text("SRI score", style = VTheme.type.label.colored(c.ink3), modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VButton(
                text = if (inCompare) "In compare" else "Compare",
                onClick = onToggleCompare,
                variant = VButtonVariant.Secondary,
                size = VButtonSize.Sm,
                tone = VButtonTone.Navy,
                full = true,
                modifier = Modifier.weight(1f),
                leading = if (inCompare) {
                    { Icon(VIcons.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(14.dp)) }
                } else null,
            )
            VButton(
                text = "Enquire",
                onClick = onOpen,
                size = VButtonSize.Sm,
                tone = VButtonTone.Sky,
                soft = false,
                full = true,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * AlreadyLinkedCta — the closing prompt at the foot of the marketplace list (unlinked-parent
 * landing only). A premium, brand-tinted [VCard] in the same lavender language as the rest of the
 * Parents Portal: a soft accent wash, a circular link glyph, a two-line pitch, and a full-width
 * primary "Link your child" button that routes back to the Link tab. No emoji, no pop-up — one
 * continuous, calm surface consistent with every other portal card.
 */
@Composable
private fun AlreadyLinkedCta(onLink: () -> Unit) {
    val c = VTheme.colors
    VCard {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(c.accent.copy(alpha = 0.18f), c.accentSoft.copy(alpha = 0.12f)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.Users, contentDescription = null, tint = c.accentDeep, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Already with a partner school?",
                style = VTheme.type.h3.colored(c.ink),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "If your child's school is already on VidyaPrayag, link your child to see attendance, marks and their full journey.",
                style = VTheme.type.caption.colored(c.ink2),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            VButton(
                text = "Link your child",
                onClick = onLink,
                size = VButtonSize.Md,
                tone = VButtonTone.Lavender,
                soft = false,
                full = true,
                leading = {
                    Icon(VIcons.User, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                },
            )
        }
    }
}

@Composable
private fun SchoolProfile(
    modifier: Modifier,
    school: DiscoveredSchool,
    onBack: () -> Unit,
) {
    val c = VTheme.colors
    // React `SchoolProfile` — the "Enquire now" button opens a bottom-sheet enquiry form.
    var enquireOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.background)) {
        VBackHeader(title = "School profile", onBack = onBack, action = {
            Icon(VIcons.Share, contentDescription = "Share", tint = c.ink2, modifier = Modifier.size(18.dp))
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(224.dp)) {
                AsyncImage(
                    model = school.image,
                    contentDescription = school.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
            }
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(school.name, style = VTheme.type.h2.colored(c.ink))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        school.board?.let { VBadge(text = it, tone = VBadgeTone.Arctic) }
                        VBadge(text = school.location, tone = VBadgeTone.Neutral)
                        // Dedicated SRI pill (real `rating` from the endpoint).
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(SriBg.copy(alpha = 0.30f)).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(VIcons.Star, contentDescription = null, tint = SriInk, modifier = Modifier.size(11.dp))
                            Text("SRI ${formatRating(school.rating)}", style = VTheme.type.label.colored(SriInk).copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = FontWeight.SemiBold))
                        }
                    }
                    school.distanceLabel?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = VTheme.type.caption.colored(c.ink2))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Save/favourite — a proper circular icon button (was a raw "♡" glyph in a
                    // text button, which read cheap). Outlined heart, brand-accent tinted.
                    val saveIx = remember { MutableInteractionSource() }
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(c.accent.copy(alpha = 0.10f))
                            .border(BorderStroke(1.dp, c.accent.copy(alpha = 0.28f)), RoundedCornerShape(999.dp))
                            .clickable(interactionSource = saveIx, indication = null) {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(VIcons.Heart, contentDescription = "Save school", tint = c.accentDeep, modifier = Modifier.size(18.dp))
                    }
                    VButton(text = "Compare", onClick = {}, variant = VButtonVariant.Secondary, size = VButtonSize.Sm, tone = VButtonTone.Navy, full = true, modifier = Modifier.weight(1f))
                    VButton(text = "Enquire now", onClick = { enquireOpen = true }, size = VButtonSize.Sm, tone = VButtonTone.Sky, soft = false, full = true, modifier = Modifier.weight(1f))
                }

                // About / academics / fees / reviews / location need richer endpoints
                // (school profile API, fees API, reviews API). Until they ship, surface
                // the real "we don't have this yet" message rather than fabricating data.
                ProfileSection("About") {
                    VComingSoon(
                        title = "School profile",
                        description = "Rich school descriptions and tags will appear here once schools complete their public profile in the admin portal.",
                    )
                }
                // Academics — board / medium / co-ed are now REAL columns surfaced by the
                // discover endpoint; fields the backend still lacks stay "Coming Soon" (LAW 6).
                ProfileSection("Academics") {
                    VCard {
                        ProfileRow("Board", school.board ?: "—")
                        ProfileRow("Medium", school.medium?.let { "$it medium" } ?: "—")
                        ProfileRow("Co-ed", coEdLabel(school.schoolGender))
                        ProfileRow("Classes offered", "Coming Soon")
                        ProfileRow("Teacher–student ratio", "Coming Soon")
                    }
                }
                ProfileSection("Fee structure") {
                    VComingSoon(
                        title = "Fee structure",
                        description = "Tuition and one-time fees will appear once the school admin publishes its fee plan.",
                    )
                }
                ProfileSection("SRI breakdown") {
                    VComingSoon(
                        title = "School Reputation Index",
                        description = "Our 11-signal score lets you compare schools on academics, safety, facilities and parent sentiment.",
                        preview = { SriPreview(score = school.rating.toFloat()) },
                    )
                }
                ProfileSection("Parent reviews") {
                    VComingSoon(
                        title = "Parent reviews",
                        description = "Verified-parent reviews launch alongside the family link-child flow.",
                    )
                }
                ProfileSection("Location") {
                    // Capture into a local — `address` lives in the shared module, so a direct
                    // smart cast across the module boundary is not permitted by the compiler.
                    val address = school.address
                    if (address.isNullOrBlank()) {
                        VComingSoon(
                            title = "On the map",
                            description = "Map embedding ships with the upcoming Maps integration. City: ${school.location}.",
                        )
                    } else {
                        VCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(VIcons.MapPin, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(16.dp))
                                Text(address, style = VTheme.type.caption.colored(c.ink))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    // ── Enquiry bottom sheet (React `SchoolProfile → enquireOpen`) ──────────────
    if (enquireOpen) {
        EnquirySheet(onDismiss = { enquireOpen = false })
    }
    }
}

/**
 * EnquirySheet — faithful copy of the React enquiry modal (Discovery.tsx lines 194-214):
 * scrim + bottom card with name / child / class inputs and a stateful "Submit enquiry"
 * button (successLabel "Sent"). The dedicated parent-enquiry endpoint ships in a later
 * phase; like the React reference, submission is a stateful UI confirmation for now.
 */
@Composable
private fun EnquirySheet(onDismiss: () -> Unit) {
    val c = VTheme.colors
    var name by remember { mutableStateOf("") }
    var childName by remember { mutableStateOf("") }
    var currentClass by remember { mutableStateOf("") }
    var applyClass by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val scrimInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF080808).copy(alpha = 0.45f))
            .clickable(interactionSource = scrimInteraction, indication = null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheetInteraction = remember { MutableInteractionSource() }
        Column(
            Modifier
                .fillMaxWidth()
                // consume taps so the scrim's dismiss doesn't fire through the sheet
                .clickable(interactionSource = sheetInteraction, indication = null) {}
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(c.card)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Send enquiry", style = VTheme.type.h3.colored(c.ink))
            Text(
                "The admissions team will respond within 2 working days.",
                style = VTheme.type.caption.colored(c.ink2),
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                VInput(value = name, onValueChange = { name = it }, label = "Your name")
                VInput(value = childName, onValueChange = { childName = it }, label = "Child's name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VInput(value = currentClass, onValueChange = { currentClass = it }, label = "Current class", placeholder = "—", modifier = Modifier.weight(1f))
                    VInput(value = applyClass, onValueChange = { applyClass = it }, label = "Apply for class", placeholder = "—", modifier = Modifier.weight(1f))
                }
                VInput(value = message, onValueChange = { message = it }, label = "Message (optional)", placeholder = "Any specific question?")
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VButton(text = "Cancel", onClick = onDismiss, variant = VButtonVariant.Ghost, full = true, modifier = Modifier.weight(1f))
                VButton(
                    text = "Submit enquiry",
                    onClick = onDismiss,
                    tone = VButtonTone.Sky,
                    soft = false,
                    full = true,
                    stateful = true,
                    successLabel = "Sent",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * SchoolCompare — faithful copy of the React compare table (Discovery.tsx lines 237-282):
 * avatar header per school, then label/value rows. The SRI-score row highlights the best
 * value with the arctic tint. Fields the discover endpoint does not yet send (board, type,
 * medium, fee range, board result, co-ed) render as "—" — LAW 6, no fabricated data.
 */
@Composable
private fun SchoolCompare(
    modifier: Modifier,
    items: List<DiscoveredSchool>,
    onBack: () -> Unit,
) {
    val c = VTheme.colors
    data class CompareRow(val label: String, val pick: (DiscoveredSchool) -> String)
    val rows = listOf(
        CompareRow("Board") { it.board ?: "—" },
        CompareRow("City") { it.location },
        CompareRow("Medium") { it.medium ?: "—" },
        CompareRow("Fee range") { "—" },
        CompareRow("SRI score") { formatRating(it.rating) },
        CompareRow("Distance") { it.distanceLabel ?: "—" },
        CompareRow("Board result") { "—" },
        CompareRow("Co-ed") { coEdLabel(it.schoolGender) },
    )
    val bestSri = items.maxOfOrNull { it.rating } ?: 0.0

    Column(modifier.fillMaxSize().background(c.background)) {
        VBackHeader(title = "Compare schools", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            // ── School avatar header ───────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Spacer(Modifier.width(96.dp))
                items.forEach { s ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.card)
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).background(SriBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(VIcons.GraduationCap, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            s.name,
                            style = VTheme.type.caption.colored(c.ink).copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 2,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // ── Comparison table ───────────────────────────────────────────────
            VCard {
                rows.forEachIndexed { i, r ->
                    if (i > 0) VDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            r.label.uppercase(),
                            style = VTheme.type.label.colored(c.ink2),
                            modifier = Modifier.width(96.dp),
                        )
                        items.forEach { s ->
                            val v = r.pick(s)
                            val isBest = r.label == "SRI score" && s.rating == bestSri && bestSri > 0.0
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBest) SriBg.copy(alpha = 0.30f) else Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(v, style = VTheme.type.caption.colored(c.ink).copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
            // Honest footnote — these columns fill in as schools complete their public profile.
            Text(
                "Fee range and board results populate once schools publish their fee plan.",
                style = VTheme.type.caption.colored(c.ink3),
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            VButton(
                text = "Enquire to all selected",
                onClick = {},
                size = VButtonSize.Lg,
                tone = VButtonTone.Sky,
                soft = false,
                full = true,
                stateful = true,
                successLabel = "Enquiries sent",
            )
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    Column {
        VLabel(title)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ProfileRow(k: String, v: String) {
    val c = VTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(k, style = VTheme.type.caption.colored(c.ink2))
            Text(v, style = VTheme.type.caption.colored(c.ink).copy(fontWeight = FontWeight.SemiBold))
        }
        VDivider()
    }
}

/** "co_ed" → "Yes", "girls" → "Girls only", "boys" → "Boys only" (React `coed` row). */
private fun coEdLabel(gender: String?): String = when (gender?.lowercase()) {
    "co_ed", "coed", "co-ed" -> "Yes"
    "girls" -> "Girls only"
    "boys" -> "Boys only"
    else -> "—"
}

/** "8.4" / "9.0" — server sends a Double rating; format to one decimal. */
private fun formatRating(r: Double): String {
    if (r <= 0.0) return "—"
    val rounded = (r * 10).toLong()
    return "${rounded / 10}.${rounded % 10}"
}
