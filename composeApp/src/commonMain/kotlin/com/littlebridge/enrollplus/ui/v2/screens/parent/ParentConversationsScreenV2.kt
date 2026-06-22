package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.parent.presentation.ParentMessageViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VMotion
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.pressScale
import org.koin.compose.viewmodel.koinViewModel

/**
 * ParentConversationsScreenV2 — Phase 3 (commit 9).
 *
 * The renamed **Conversations** tab (was "Activity"). "Conversations" now means *real two-way
 * messaging first* — the parent ↔ teacher/office threads from [ParentMessageViewModel] — with the
 * one-way school **Announcements** feed kept a tap away on a second segment. This routes messaging
 * properly *through the tab* instead of hiding it behind a header chat-icon overlay.
 *
 * LAW (no floating toasts): the segment switch is an in-place [AnimatedContent] crossfade with a
 * spring-sliding pill indicator — no popups. Both surfaces read their own real ViewModel (no MockV2).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ParentConversationsScreenV2(
    modifier: Modifier = Modifier,
    messageViewModel: ParentMessageViewModel = koinViewModel(),
) {
    val c = VTheme.colors
    var segment by remember { mutableStateOf(ConversationsSegment.Messages) }

    val messageState by messageViewModel.state.collectAsStateV2()
    val unreadThreads = messageState.threads.count { it.unreadCount > 0 }

    // §11 — within the Messages segment, system/predictive back peels the drilled-in layers
    // (compose-new → open conversation) before the portal lets back exit the tab. On the
    // Announcements segment there is nothing to peel, so back falls through to the shell.
    val onMessages = segment == ConversationsSegment.Messages
    BackHandler(enabled = onMessages && (messageState.composeOpen || messageState.openThreadId != null)) {
        when {
            messageState.composeOpen -> messageViewModel.closeCompose()
            messageState.openThreadId != null -> messageViewModel.closeThread()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp),
    ) {
        Text(
            "Conversations",
            style = VTheme.type.h1.colored(c.ink),
            modifier = Modifier.padding(bottom = 14.dp),
        )

        ConversationsSegmentBar(
            selected = segment,
            messagesBadge = unreadThreads,
            onSelect = { segment = it },
            modifier = Modifier.padding(bottom = 16.dp),
        )

        AnimatedContent(
            targetState = segment,
            transitionSpec = { VMotion.quietFade() },
            modifier = Modifier.fillMaxSize(),
            label = "conversations-segment",
        ) { seg ->
            when (seg) {
                ConversationsSegment.Messages ->
                    // The chrome-less messaging surface — inbox → conversation → compose,
                    // all driven by the SAME shared ParentMessageViewModel.
                    ParentMessagesBody(
                        viewModel = messageViewModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                ConversationsSegment.Announcements ->
                    // The existing one-way announcement feed (its own ViewModel via koinViewModel()).
                    ParentActivityScreenV2(modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** The two faces of the Conversations hub. Messages is primary (the tab's reason to exist). */
enum class ConversationsSegment { Messages, Announcements }

/**
 * A two-up segmented control with a spring-sliding pill behind the active label — premium,
 * native-feeling, no toast. The Messages segment carries a live unread badge.
 */
@Composable
private fun ConversationsSegmentBar(
    selected: ConversationsSegment,
    messagesBadge: Int,
    onSelect: (ConversationsSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val density = LocalDensity.current

    // Measure the track width so the pill is exactly half of it; animate its x-offset.
    var trackWidth by remember { mutableStateOf(0.dp) }
    val pillX by animateDpAsState(
        targetValue = if (selected == ConversationsSegment.Messages) 0.dp else trackWidth / 2f,
        // animateDpAsState needs an AnimationSpec<Dp>; VMotion.springSnappy is Float-typed, so use
        // the generic snappy factory rebuilt for Dp (same stiffness/damping feel).
        animationSpec = VMotion.snappy(),
        label = "segment-pill-x",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(c.cream)
            .onGloballyPositioned { trackWidth = with(density) { it.size.width.toDp() } },
    ) {
        // Sliding pill — half the track, padded inside.
        if (trackWidth > 0.dp) {
            Box(
                Modifier
                    .offset(x = pillX)
                    .padding(4.dp)
                    .width((trackWidth / 2f) - 8.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.card),
            )
        }
        Row(Modifier.fillMaxSize()) {
            SegmentTab(
                label = "Messages",
                active = selected == ConversationsSegment.Messages,
                badge = messagesBadge,
                onClick = { onSelect(ConversationsSegment.Messages) },
                modifier = Modifier.weight(1f),
            )
            SegmentTab(
                label = "Announcements",
                active = selected == ConversationsSegment.Announcements,
                badge = 0,
                onClick = { onSelect(ConversationsSegment.Announcements) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SegmentTab(
    label: String,
    active: Boolean,
    badge: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(999.dp))
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = VTheme.type.bodyStrong
                .colored(if (active) c.ink else c.ink3)
                .copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
        )
        if (badge > 0) {
            Spacer(Modifier.width(6.dp))
            VBadge(text = badge.toString(), tone = VBadgeTone.Accent)
        }
    }
}
