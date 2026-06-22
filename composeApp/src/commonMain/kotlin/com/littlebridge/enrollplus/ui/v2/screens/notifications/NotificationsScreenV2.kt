package com.littlebridge.enrollplus.ui.v2.screens.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.feature.parent.presentation.NotificationsState
import com.littlebridge.enrollplus.feature.parent.presentation.NotificationsViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VElevationLevel
import com.littlebridge.enrollplus.ui.v2.theme.VMotion
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.vElevation
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * NotificationsScreenV2 — faithful Compose translation of `Notifications.tsx → NotificationsScreen`.
 *
 * Reproduces the React layout exactly (UI_FIDELITY_AUDIT §9):
 *  - navy→indigo (135°) "Inbox" hero with a top-right radial teal blob, blurred bell chip, an
 *    `INBOX` overline (12sp / 0.05em / 70% white) and a **mono** unread count (28sp / 600) + "unread";
 *  - `all / unread` filter pills — **navy fill + white** when active, **cream + ink-2** when not
 *    (NOT the teal VTag chip), 12sp / 700, "unread · N" suffix;
 *  - per-item card (category badge + time, title, body, unread teal-deep dot, chevron, category-tinted
 *    icon tile) with the two React shadow levels (raised for unread, resting for read) and a staggered
 *    fade-up entrance (delay i*0.04s);
 *  - the "You're all caught up" empty state and the "Notification preferences" footer (13/600).
 *
 * **Wired to the real [NotificationsViewModel]** (`shared/`) →
 * `ParentRepository.getNotifications` → `GET /api/v1/parent/notifications`, which aggregates the
 * parent's school announcements and outstanding fee reminders. MockV2 is no longer referenced; the
 * three UI states (loading / error / empty) are handled by [VStateHost] (report §5.3, SWEEP-A).
 */
@Composable
fun NotificationsScreenV2(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    NotificationsContent(
        state = state,
        onBack = onBack,
        onMarkAll = viewModel::markAllRead,
        onMarkRead = viewModel::markRead,
        onRetry = viewModel::load,
        modifier = modifier.statusBarsPadding()
            .imePadding()
            .navigationBarsPadding(),
    )
}

/** Stateless body — also used by the @Preview with seeded state (no MockV2 in the live path). */
@Composable
private fun NotificationsContent(
    state: NotificationsState,
    onBack: () -> Unit,
    onMarkAll: () -> Unit,
    onMarkRead: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val d = VTheme.dimens

    var filterUnread by remember { mutableStateOf(false) }

    val items = state.notifications.map {
        VNotification(
            id = it.id,
            category = it.category,
            title = it.title,
            body = it.body,
            time = it.time,
            unread = it.unread,
        )
    }
    val unread = items.count { it.unread }
    val visible = if (filterUnread) items.filter { it.unread } else items

    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(
            title = "Notifications",
            onBack = onBack,
            // React action: `<Check 14/> Mark all` — a text+icon button in teal-deep / 700.
            action = {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onMarkAll() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(VIcons.Check, contentDescription = null, tint = c.tealDeep, modifier = Modifier.size(14.dp))
                    Text(
                        "Mark all",
                        style = VTheme.type.caption.colored(c.tealDeep).copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Inbox hero ──────────────────────────────────────────────────────────
            // React: `px-5 pt-2 pb-5` = 20 / 8 / 20px wrapper around an 18px-radius gradient card.
            Box(Modifier.padding(start = 20.dp, end = 20.dp, top = d.sm, bottom = 20.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            // React: linear-gradient(135deg, --navy 0%, #3b3870 100%)
                            Brush.linearGradient(
                                colors = listOf(c.navy, Color(0xFF3B3870)),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                            ),
                        )
                        // §9#2: decorative top-right radial teal blob (rgba(60,185,169,0.45)→transparent).
                        .drawBehind {
                            val blobR = 88.dp.toPx() // React w-44/h-44 = 176px → radius 88, offset -40
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(BlobTeal, Color.Transparent),
                                    center = Offset(size.width + 40.dp.toPx(), -40.dp.toPx()),
                                    radius = blobR * 2f,
                                ),
                                radius = blobR * 2f,
                                center = Offset(size.width + 40.dp.toPx(), -40.dp.toPx()),
                            )
                        }
                        .padding(d.lg),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Blurred white bell chip (React rgba(255,255,255,0.14) + backdrop-blur).
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(VIcons.Bell, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(d.md))
                        Column(Modifier.weight(1f)) {
                            // React: 12sp / 0.05em / uppercase / opacity .7 — NOT the 11sp label token.
                            Text(
                                "INBOX",
                                style = VTheme.type.body.colored(Color.White.copy(alpha = 0.7f)).copy(
                                    fontSize = 12.sp,
                                    letterSpacing = 0.05.em,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            // React: mono 28 / 600 / line-height 1.1, with "unread" at 14 / opacity .7.
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    unread.toString(),
                                    style = VTheme.type.dataLg.colored(Color.White).copy(
                                        fontSize = 28.sp,
                                        lineHeight = 30.8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "unread",
                                    style = VTheme.type.body.colored(Color.White.copy(alpha = 0.7f)),
                                    modifier = Modifier.padding(bottom = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Filter pills ──────────────────────────────────────────────────────
            // React: `px-5 mb-3 flex gap-2`. Pills: navy active / cream inactive, 12/700, capitalize.
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(d.sm),
            ) {
                FilterPill(label = "All", active = !filterUnread) { filterUnread = false }
                FilterPill(
                    label = if (unread > 0) "Unread · $unread" else "Unread",
                    active = filterUnread,
                ) { filterUnread = true }
            }

            // ── List · loading · error · empty (LAW 3 via VStateHost) ──────────────
            // React: `px-5 pb-8 space-y-2`.
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(d.sm),
            ) {
                VStateHost(
                    loading = state.isLoading,
                    error = state.error,
                    isEmpty = visible.isEmpty(),
                    emptyIcon = VIcons.Check,
                    emptyTitle = "You're all caught up",
                    emptyBody = if (filterUnread) "No unread notifications." else "No notifications yet.",
                    onRetry = onRetry,
                ) {
                    visible.forEachIndexed { i, n ->
                        // React: staggered fade-up entrance (delay i*0.04s).
                        var shown by remember(n.id) { mutableStateOf(false) }
                        LaunchedEffect(n.id) {
                            delay(i * 40L)
                            shown = true
                        }
                        AnimatedVisibility(
                            visible = shown,
                            enter = VMotion.fadeUp(delayMs = 0, fromY = 8),
                        ) {
                            NotificationRow(n, onClick = { onMarkRead(n.id) })
                        }
                    }
                }
            }

            // ── Preferences footer ──────────────────────────────────────────────────
            // React: `px-5 pb-10` wrapper; pb-8 on the list above → extra gap before footer.
            Spacer(Modifier.height(d.md))
            Box(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.cream)
                        .clickable {}
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(VIcons.Close, contentDescription = null, tint = c.ink2, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(d.sm))
                    // React: 13 / 600 — slightly larger than the 12/500 caption token.
                    Text(
                        "Notification preferences",
                        style = VTheme.type.bodyStrong.colored(c.ink2).copy(fontSize = 13.sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) c.navy else c.cream)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            // React: `px-3.5 py-1.5` = 14 / 6.
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            // React: 12 / 700; active white, inactive ink-2.
            style = VTheme.type.caption
                .colored(if (active) Color.White else c.ink2)
                .copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
private fun NotificationRow(n: VNotification, onClick: () -> Unit) {
    val c = VTheme.colors
    val d = VTheme.dimens
    val (tileBg, tileFg) = categoryTile(n.category)
    val interaction = remember { MutableInteractionSource() }
    // React shadow levels: unread → raised (0 6px 14px -6px navy@12%); read → resting (0 2px 6px navy@4%).
    Box(
        Modifier
            .fillMaxWidth()
            .vElevation(if (n.unread) VElevationLevel.Raised else VElevationLevel.Card, radius = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp), // React `p-3.5`
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tileBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(categoryIcon(n.category), contentDescription = null, tint = tileFg, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(d.sm + d.xs)) // React gap-3 = 12
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VBadge(text = n.category, tone = categoryBadgeTone(n.category))
                    Spacer(Modifier.width(d.sm))
                    Text(n.time, style = VTheme.type.label.colored(c.ink3).copy(fontSize = 11.sp, letterSpacing = 0.sp))
                }
                Spacer(Modifier.height(6.dp)) // React mt-1.5
                Text(n.title, style = VTheme.type.bodyStrong.colored(c.ink)) // 14 / 600
                Spacer(Modifier.height(2.dp)) // React mt-0.5
                Text(n.body, style = VTheme.type.caption.colored(c.ink2)) // 12 / ink-2
            }
            if (n.unread) {
                // React: 8px teal-deep dot top-right.
                Box(
                    Modifier
                        .padding(start = d.sm, top = 0.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c.tealDeep),
                )
            } else {
                Icon(
                    VIcons.ChevronRight,
                    contentDescription = null,
                    tint = c.ink3,
                    modifier = Modifier.padding(start = d.xs, top = 4.dp).size(16.dp),
                )
            }
        }
    }
}

/** UI-only notification model. Mirrors the server feed shape so a real feed can map to it. */
data class VNotification(
    val id: String,
    val category: String, // "attendance" | "academic" | "fees" | "announcement"
    val title: String,
    val body: String,
    val time: String,
    val unread: Boolean,
)

// §9 one-off literals (lifted verbatim from Notifications.tsx; not part of the global palette):
//  - hero blob = rgba(60,185,169,0.45)
//  - icon-tile foregrounds: attendance #7a3f00, fees #7a1c18, academic --teal-deep, default #155e3a.
private val BlobTeal = Color(0x733CB9A9)        // rgba(60,185,169,0.45)
private val TileFgAttendance = Color(0xFF7A3F00)
private val TileFgFees = Color(0xFF7A1C18)
private val TileFgDefault = Color(0xFF155E3A)

private fun categoryIcon(cat: String): ImageVector = when (cat.lowercase()) {
    "attendance" -> VIcons.Calendar
    "academic" -> VIcons.BookOpen // React iconFor: academic → <BookOpen/>
    "fees" -> VIcons.Wallet
    else -> VIcons.Megaphone
}

private fun categoryBadgeTone(cat: String): VBadgeTone = when (cat.lowercase()) {
    "fees" -> VBadgeTone.Danger
    "attendance" -> VBadgeTone.Warning
    "academic" -> VBadgeTone.Arctic
    else -> VBadgeTone.Success
}

/** Category-tinted icon-tile colors, matching the React `toneFor()` map (§9#4) verbatim. */
@Composable
private fun categoryTile(cat: String): Pair<Color, Color> {
    val c = VTheme.colors
    return when (cat.lowercase()) {
        "attendance" -> c.warning.copy(alpha = 0.55f) to TileFgAttendance // rgba(255,212,163,0.55) / #7a3f00
        "fees" -> c.danger.copy(alpha = 0.55f) to TileFgFees              // rgba(255,173,168,0.55) / #7a1c18
        "academic" -> c.teal.copy(alpha = 0.18f) to c.tealDeep            // rgba(60,185,169,0.18) / --teal-deep
        else -> c.success.copy(alpha = 0.42f) to TileFgDefault            // rgba(168,230,207,0.42) / #155e3a
    }
}
