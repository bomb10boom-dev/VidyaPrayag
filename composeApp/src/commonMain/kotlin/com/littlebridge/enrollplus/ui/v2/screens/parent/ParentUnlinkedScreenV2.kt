package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.screens.auth.ParentLinkChildScreenV2
import com.littlebridge.enrollplus.ui.v2.screens.discovery.DiscoveryScreenV2
import com.littlebridge.enrollplus.ui.v2.theme.VMotion
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * ParentUnlinkedScreenV2 — the **first-run home for a parent with no child linked yet**.
 *
 * Instead of dropping a fresh parent straight into the 5-tab portal (where every tab is an empty
 * state), this is a focused, premium two-tab landing:
 *   • **Link a child** — the guided link flow ([ParentLinkChildScreenV2]); the fastest path for a
 *     parent whose child's school is already on VidyaPrayag.
 *   • **Explore schools** — the school marketplace ([DiscoveryScreenV2]); for a parent still
 *     choosing a school, or whose school isn't on the platform yet.
 *
 * Premium, consistent with the rest of the Parents Portal: a calm lavender canvas with a soft
 * brand-tinted aurora behind a warm hero header, and a glass segmented control with a liquid violet
 * pill that springs between the two tabs (the same language as the Conversations hub and the dock —
 * one motion vocabulary across the whole portal). No cheap pop-ups, no emoji glyphs.
 */
@Composable
fun ParentUnlinkedScreenV2(
    onLinked: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var tab by remember { mutableStateOf(UnlinkedTab.Link) }

    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            // A soft brand-tinted aurora glow from the top so the hero appears to emit light from
            // the lavender canvas — premium ambience, very low alpha, never a wall of purple.
            .drawWithContent {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(c.accent.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.04f),
                        radius = size.width * 0.95f,
                    ),
                )
                drawContent()
            },
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ── Hero header ─────────────────────────────────────────────────────
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 4.dp),
            ) {
                Text(
                    "Welcome to VidyaPrayag",
                    style = VTheme.type.label.colored(c.accentDeep)
                        .copy(fontWeight = FontWeight.Bold, letterSpacing = 0.08.sp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (tab) {
                        UnlinkedTab.Link -> "Follow your child's journey"
                        UnlinkedTab.Explore -> "Find the right school"
                    },
                    style = VTheme.type.h1.colored(c.ink),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (tab) {
                        UnlinkedTab.Link -> "Link your child to their school to see attendance, marks and more."
                        UnlinkedTab.Explore -> "Browse schools on VidyaPrayag, compare them, and enquire."
                    },
                    style = VTheme.type.body.colored(c.ink2),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Segmented control ───────────────────────────────────────────────
            UnlinkedTabBar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            Spacer(Modifier.height(8.dp))

            // ── Tab body ────────────────────────────────────────────────────────
            AnimatedContent(
                targetState = tab,
                transitionSpec = { VMotion.quietFade() },
                modifier = Modifier.fillMaxSize(),
                label = "unlinked-tab",
            ) { t ->
                when (t) {
                    UnlinkedTab.Link ->
                        ParentLinkChildScreenV2(
                            onDone = onLinked,
                            // No back from here — this IS the root for an unlinked parent. The
                            // segmented control is the only way to switch context.
                            onBack = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    UnlinkedTab.Explore ->
                        DiscoveryScreenV2(
                            // The marketplace's built-in "Exit" affordance returns the parent to
                            // the Link tab (there is nowhere to exit to — this is the root).
                            onExit = { tab = UnlinkedTab.Link },
                            // Hosted inside this landing: suppress Discovery's own hero/Exit and
                            // its status-bar padding so the two surfaces read as one premium screen.
                            embedded = true,
                            // Closing CTA at the foot of the school list: "already with a partner
                            // school? Link your child" — routes the parent straight to the Link tab.
                            onAlreadyLinked = { tab = UnlinkedTab.Link },
                            modifier = Modifier.fillMaxSize(),
                        )
                }
            }
        }
    }
}

/** The two faces of the unlinked landing. Link is primary (the fastest path in). */
enum class UnlinkedTab { Link, Explore }

/**
 * A two-up glass segmented control with a spring-sliding violet pill behind the active label —
 * the same premium motion as the Conversations hub and the dock. No toast, no pop-up.
 */
@Composable
private fun UnlinkedTabBar(
    selected: UnlinkedTab,
    onSelect: (UnlinkedTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val density = LocalDensity.current

    var trackWidth by remember { mutableStateOf(0.dp) }
    val pillX by animateDpAsState(
        targetValue = if (selected == UnlinkedTab.Link) 0.dp else trackWidth / 2f,
        animationSpec = spring(dampingRatio = 0.8f),
        label = "unlinked-pill-x",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.card)
            .border(1.dp, c.hairline, RoundedCornerShape(999.dp))
            .onGloballyPositioned { trackWidth = with(density) { it.size.width.toDp() } },
    ) {
        if (trackWidth > 0.dp) {
            Box(
                Modifier
                    .offset(x = pillX)
                    .padding(5.dp)
                    .width((trackWidth / 2f) - 10.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(c.accent.copy(alpha = 0.16f), c.accentSoft.copy(alpha = 0.12f)),
                        ),
                    ),
            )
        }
        Row(Modifier.fillMaxSize()) {
            UnlinkedTabButton(
                label = "Link a child",
                icon = VIcons.Users,
                active = selected == UnlinkedTab.Link,
                onClick = { onSelect(UnlinkedTab.Link) },
                modifier = Modifier.weight(1f),
            )
            UnlinkedTabButton(
                label = "Explore schools",
                icon = VIcons.Search,
                active = selected == UnlinkedTab.Explore,
                onClick = { onSelect(UnlinkedTab.Explore) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun UnlinkedTabButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val tint = if (active) c.accentDeep else c.ink3
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = VTheme.type.bodyStrong.colored(tint)
                .copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp),
        )
    }
}
