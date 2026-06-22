package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.NotificationsViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.ParentDashboardViewModel
import com.littlebridge.enrollplus.feature.parent.presentation.TrackProgressViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VDivider
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VNavItem
import com.littlebridge.enrollplus.ui.v2.components.VScreenScaffold
import com.littlebridge.enrollplus.ui.v2.components.VStatusDot
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.screens.auth.ParentLinkChildScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.discovery.AcademicCalendarScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.discovery.DiscoveryScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.notifications.NotificationsScreenV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/** Full-screen overlays a portal can push above its tab content (back returns to the tabs). */
private enum class ParentOverlay { None, Notifications, Calendar, Scholarships, Profile, Leave, Messages, LinkChild, Discovery }

/**
 * ParentPortalV2 — the 5-tab parent shell, a faithful copy of `Parent.tsx → ParentApp`.
 *
 * Owns the header (child identity from the real [TrackProgressViewModel]) and the bottom nav
 * (Home · Academics · Fees · Conversations · Profile). Each leaf is now wired to its own real
 * ViewModel via `koinViewModel()` — no MockV2 in any production path. Notifications & Calendar are
 * pushed as full-screen overlays; the flagship collectible player card lives on the Profile tab.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParentPortalV2(
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    // RA-PP-FIX: child identity in the shared header now comes from the real
    // /parent/dashboard child_summary (the single source of truth the rest of the
    // dashboard already uses), NOT from /track-progress — which never returned a
    // child name and used to crash on the EI field. `headerViewModel` (track-progress)
    // still supplies the holistic level/journey copy as a graceful enrichment.
    dashboardViewModel: ParentDashboardViewModel = koinViewModel(),
    headerViewModel: TrackProgressViewModel = koinViewModel(),
    // RA-S06: drives the header bell's unread dot from the real notifications feed.
    notificationsViewModel: NotificationsViewModel = koinViewModel(),
) {
    var tab by remember { mutableStateOf("home") }
    var overlay by remember { mutableStateOf(ParentOverlay.None) }
    val dashboard by dashboardViewModel.state.collectAsStateV2()
    val progress by headerViewModel.state.collectAsStateV2()
    val notifications by notificationsViewModel.state.collectAsStateV2()

    // ── Unlinked-parent gate ────────────────────────────────────────────────────
    // A parent with NO child linked yet shouldn't land in the 5-tab portal where every tab is an
    // empty state. Once the dashboard has resolved (not loading, no error) and reports zero
    // children, we hand off to the focused two-tab landing — Link a child / Explore schools.
    // (While the very first load is still in flight we fall through to the portal's own skeletons,
    // so the unlinked screen never flashes for an existing parent.)
    val hasResolved = !dashboard.isLoading && dashboard.error == null
    if (hasResolved && dashboard.children.isEmpty()) {
        ParentUnlinkedScreenV2(
            // After a successful link request the dashboard reloads — once the school approves and
            // a child appears, this gate falls through to the full portal automatically.
            onLinked = { dashboardViewModel.load() },
            modifier = modifier,
        )
        return
    }

    // §11 cross-platform — predictive back / edge-swipe dismisses the full-screen overlay back to
    // the tabs, not the portal.
    BackHandler(enabled = overlay != ParentOverlay.None) {
        overlay = ParentOverlay.None
    }

    when (overlay) {
        ParentOverlay.Notifications -> {
            NotificationsScreenV2(onBack = { overlay = ParentOverlay.None }, modifier = modifier)
            return
        }
        ParentOverlay.Calendar -> {
            AcademicCalendarScreenV2(onBack = { overlay = ParentOverlay.None }, modifier = modifier)
            return
        }
        ParentOverlay.Scholarships -> {
            ScholarshipsScreenV2(onBack = { overlay = ParentOverlay.None }, modifier = modifier)
            return
        }
        ParentOverlay.Profile -> {
            // §7 finding K — the avatar opens the real profile screen; logout now lives
            // on an explicit button inside it (no more "tap your photo = logout").
            // RA-S04 (directive): linking a child is opt-in from the profile, never forced
            // after signup/login — the "Linked children" row opens the link flow as an overlay.
            ParentProfileScreenV2(
                onBack = { overlay = ParentOverlay.None },
                onLogout = onLogout,
                onLinkChild = { overlay = ParentOverlay.LinkChild },
                onDiscoverSchools = { overlay = ParentOverlay.Discovery },
                modifier = modifier,
            )
            return
        }
        ParentOverlay.LinkChild -> {
            // RA-S04 (directive): parent-initiated child linking — reached only from the
            // Profile screen, never pushed automatically after auth. Done/back both return
            // to the tabs (the link request is a PENDING admin approval server-side).
            ParentLinkChildScreenV2(
                onDone = { overlay = ParentOverlay.None },
                onBack = { overlay = ParentOverlay.Profile },
                modifier = modifier,
            )
            return
        }
        ParentOverlay.Leave -> {
            // RA-44: the parent leg of the leave workflow.
            ParentLeaveScreenV2(onBack = { overlay = ParentOverlay.None }, modifier = modifier)
            return
        }
        ParentOverlay.Messages -> {
            // RA-51: parent ↔ teacher/admin messaging.
            ParentMessagesScreenV2(onBack = { overlay = ParentOverlay.None }, modifier = modifier)
            return
        }
        ParentOverlay.Discovery -> {
            // Marketplace browsing for AUTHENTICATED parents — the same DiscoveryScreenV2 that
            // serves the unauth flow, hosted as a portal overlay. Reached from the Profile's
            // "Discover schools" row and Home's "View all" featured-schools link. The header
            // "Exit" + system back both pop the overlay back to the tabs.
            DiscoveryScreenV2(
                onExit = { overlay = ParentOverlay.None },
                modifier = modifier,
            )
            return
        }
        ParentOverlay.None -> Unit
    }

    val items = listOf(
        VNavItem("home", "Home", VIcons.Home),
        VNavItem("academics", "Academics", VIcons.School),
        VNavItem("fees", "Fees", VIcons.Wallet),
        // Phase 3 (commit 9): "Activity" → "Conversations". The tab now leads with real two-way
        // messaging (Chat icon), with announcements one segment away — see ParentConversationsScreenV2.
        // The dock badge rides the real unread notifications count so the parent always sees pending
        // conversation activity at a glance.
        VNavItem("conversations", "Conversations", VIcons.Chat, badge = notifications.unreadCount),
        // Phase 4 (commit 10): the flagship house-colored collectible player card lives on its own
        // tab — see ParentProfileCardScreenV2.
        VNavItem("profile", "Profile", VIcons.User),
    )

    VScreenScaffold(
        modifier = modifier,
        topBar = {
            // ONE CANONICAL HEADER (design law): the exact same identity header renders on EVERY
            // parent tab — Home included. The child switcher lives here and nowhere else; the
            // identity chip is a dropdown that switches the active child for the whole portal
            // (the pick flows through the shared SelectedChildHolder to every tab's ViewModel).
            val child = dashboard.selectedChild
            // Prefer the dashboard's authoritative level; fall back to the holistic level.
            val level = child?.currentLevel?.takeIf { it > 0 } ?: progress.currentLevel
            val rawProgress = child?.overallProgress
                ?.let { if (it <= 1.0) it * 100.0 else it }
                ?.toInt()
                ?: (progress.overallProgress * 100).toInt()
            val subline = when {
                level > 0 && rawProgress > 0 -> "Level $level · $rawProgress% journey"
                level > 0 -> "Level $level"
                progress.journeyDescription.isNotBlank() -> progress.journeyDescription
                else -> "Your child"
            }
            ParentHeader(
                childName = child?.name?.takeIf { it.isNotBlank() } ?: "Your child",
                childSubline = subline,
                childPhoto = child?.profilePic,
                children = dashboard.children,
                selectedChildId = dashboard.selectedChildId,
                onSelectChild = dashboardViewModel::selectChild,
                // RA-S06: real account name (RA-S03 pref) + real unread count.
                accountName = progress.accountName,
                unreadCount = notifications.unreadCount,
                onOpenNotifications = { overlay = ParentOverlay.Notifications },
                onOpenMessages = { overlay = ParentOverlay.Messages },
                onExit = { overlay = ParentOverlay.Profile },
            )
        },
        bottomBar = {
            // The Parents Portal's signature premium FLOATING DOCK (ParentDock) — a detached glass
            // bar with a liquid violet active-lozenge. The shared VBottomNav stays in place for the
            // Admin/Teacher portals; this bespoke dock is exclusive to the parent experience.
            ParentDock(
                items = items,
                selected = tab,
                onSelect = { tab = it },
            )
        },
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            when (tab) {
                "home" -> ParentHomeScreenV2(
                    onDiscoverSchools = { overlay = ParentOverlay.Discovery },
                    onOpenNotifications = { overlay = ParentOverlay.Notifications },
                    onOpenFees = { tab = "fees" },
                    onOpenAcademics = { tab = "academics" },
                )
                "academics" -> ParentAcademicsScreenV2(onOpenLeave = { overlay = ParentOverlay.Leave })
                "fees" -> ParentFeesScreenV2()
                // Phase 3 (commit 9): the Conversations hub — messaging-first, announcements second.
                "conversations" -> ParentConversationsScreenV2()
                // Phase 4 (commits 10–11): the flagship collectible player card, with a
                // swipe-down account-options reveal (logout / link child / discover schools).
                "profile" -> ParentProfileCardScreenV2(
                    onLogout = onLogout,
                    onLinkChild = { overlay = ParentOverlay.LinkChild },
                    onDiscoverSchools = { overlay = ParentOverlay.Discovery },
                )
            }
        }
    }
}

/**
 * ParentHeader — THE single canonical header for the whole Parents Portal. Renders identically on
 * every tab (Home included). It carries:
 *   • a tappable identity chip (child avatar + name + level/journey) that opens a **dropdown child
 *     switcher** — the ONLY place in the portal a parent switches child. The pick flows through the
 *     shared SelectedChildHolder, so every tab re-scopes to the new child reactively.
 *   • an icon cluster on the right: Conversations, Notifications (with a real unread dot) and the
 *     account avatar (opens the Profile/account screen).
 *
 * Surface: a clean white bar on the lavender canvas with a hairline divider — lavender is the brand
 * accent (chip tint, active dot), not a wall-to-wall fill.
 */
@Composable
private fun ParentHeader(
    childName: String,
    childSubline: String,
    childPhoto: String?,
    children: List<com.littlebridge.enrollplus.feature.parent.domain.model.DashboardChildSummary>,
    selectedChildId: String?,
    onSelectChild: (String) -> Unit,
    accountName: String,
    unreadCount: Int,
    onOpenNotifications: () -> Unit,
    onOpenMessages: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var switcherOpen by remember { mutableStateOf(false) }
    val canSwitch = children.size > 1

    Column(
        modifier
            .fillMaxWidth()
            .background(c.card)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // ── Identity chip → dropdown child switcher ──────────────────────────
            Box {
                val chipInteraction = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.cream)
                        .clickable(
                            interactionSource = chipInteraction,
                            indication = null,
                            enabled = canSwitch,
                        ) { switcherOpen = true }
                        .padding(start = 6.dp, end = if (canSwitch) 10.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VAvatar(name = childName.ifBlank { "?" }, src = childPhoto, size = 34.dp)
                    Column {
                        Text(
                            childName.ifBlank { "Your child" },
                            style = VTheme.type.bodyStrong.colored(c.ink)
                                .copy(fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold),
                        )
                        Text(
                            childSubline,
                            style = VTheme.type.caption.colored(c.ink2).copy(fontSize = 10.5.sp),
                        )
                    }
                    if (canSwitch) {
                        Icon(
                            VIcons.ChevronDown,
                            contentDescription = "Switch child",
                            tint = c.ink3,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                // The dropdown anchors under the chip. Real children only — no fabricated entries.
                androidx.compose.material3.DropdownMenu(
                    expanded = switcherOpen,
                    onDismissRequest = { switcherOpen = false },
                    containerColor = c.card,
                ) {
                    children.forEach { ch ->
                        val selected = ch.id == selectedChildId
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    VAvatar(name = ch.name.ifBlank { "?" }, src = ch.profilePic, size = 30.dp)
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            ch.name.ifBlank { "—" },
                                            style = VTheme.type.bodyStrong.colored(c.ink)
                                                .copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                        )
                                        if (ch.currentLevel > 0) {
                                            Text(
                                                "Level ${ch.currentLevel}",
                                                style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 10.sp),
                                            )
                                        }
                                    }
                                    if (selected) {
                                        Icon(
                                            VIcons.Check,
                                            contentDescription = "Selected",
                                            tint = c.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectChild(ch.id)
                                switcherOpen = false
                            },
                        )
                    }
                }
            }

            // ── Icon cluster: conversations · notifications · account ────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIconButton(VIcons.Chat, "Messages", onOpenMessages)
                Box {
                    HeaderIconButton(VIcons.Bell, "Notifications", onOpenNotifications)
                    // Real unread dot only — never a permanent cry-wolf indicator.
                    if (unreadCount > 0) {
                        VStatusDot(color = c.dangerInk, size = 7.dp, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp))
                    }
                }
                val exitInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(interactionSource = exitInteraction, indication = null) { onExit() },
                ) {
                    VAvatar(name = accountName.ifBlank { "Parent" }, size = 36.dp, ring = true)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        VDivider()
    }
}

/** A circular header action button — cream surface, navy glyph, no ripple. */
@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = VTheme.colors
    val ix = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(c.cream)
            .clickable(interactionSource = ix, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = c.ink, modifier = Modifier.size(16.dp))
    }
}
