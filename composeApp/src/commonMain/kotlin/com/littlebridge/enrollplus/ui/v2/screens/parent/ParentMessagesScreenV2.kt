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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentMessageDto
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentMessageThreadDto
import com.littlebridge.enrollplus.feature.parent.domain.model.ParentRecipientDto
import com.littlebridge.enrollplus.feature.parent.presentation.ParentMessageViewModel
import com.littlebridge.enrollplus.ui.v2.components.VAvatar
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * RA-51: parent Messages inbox + conversation detail. Mirror of the admin
 * [com.littlebridge.enrollplus.ui.v2.screens.school.MessagesScreenV2] but on
 * the parent endpoints. Wired to the real [ParentMessageViewModel]
 * (`GET /api/v1/parent/messages/threads`, `…/{id}/messages`, `POST /parent/messages`).
 *
 * No MockV2 — replaces the old hardcoded fake-thread stub. Three states via
 * [VStateHost] (LAW 3) for both the list and the open conversation.
 */
@Composable
fun ParentMessagesScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ParentMessageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadThreads() }

    // Back peels layers in order: compose-new → open conversation → exit.
    val backHandler: () -> Unit = {
        when {
            state.composeOpen -> viewModel.closeCompose()
            state.openThreadId != null -> viewModel.closeThread()
            else -> onBack()
        }
    }
    val title = when {
        state.composeOpen -> "New message"
        state.openThreadId != null -> state.openThreadName.ifBlank { "Conversation" }
        else -> "Messages"
    }

    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = title, onBack = backHandler)

        ParentMessagesBody(viewModel = viewModel, modifier = Modifier.weight(1f).fillMaxWidth())
    }
}

/**
 * Phase 3 (commit 9) — the chrome-less messaging body, hosted *inside* the Conversations tab.
 *
 * Identical messaging surface as [ParentMessagesScreenV2] (inbox → conversation → compose-new
 * layers driven by the SAME [ParentMessageViewModel]), but WITHOUT the standalone status-bar
 * padding + [VBackHeader] — the Conversations hub owns that chrome. Drilling into a thread or
 * compose-new is handled by the shared VM state, so the segmented hub's back is layered by the
 * caller via [ParentMessageViewModel.composeOpen]/[ParentMessageViewModel.openThreadId].
 */
@Composable
fun ParentMessagesBody(
    viewModel: ParentMessageViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateV2()

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.loadThreads() }

    Column(modifier.imePadding()) {
        when {
            // RA-S07: compose-new is the topmost layer (back closes it first).
            state.composeOpen -> {
                ParentComposeNewContent(
                    recipients = state.composeRecipients,
                    loading = state.composeLoadingRecipients,
                    error = state.composeError,
                    isEmpty = state.composeEmpty,
                    sending = state.sending,
                    onSend = viewModel::composeNew,
                    onRetry = viewModel::openCompose,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            state.openThreadId != null -> {
                ParentConversationContent(
                    messages = state.messages,
                    loading = state.conversationLoading,
                    error = state.conversationError,
                    isEmpty = state.conversationEmpty,
                    sending = state.sending,
                    onSend = viewModel::reply,
                    onRetry = { state.openThreadId?.let { viewModel.openThread(it, state.openThreadName) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                ParentThreadListContent(
                    threads = state.threads,
                    loading = state.loading,
                    error = state.error,
                    isEmpty = state.isEmpty,
                    onOpenThread = { t -> viewModel.openThread(t.id, t.senderName) },
                    onCompose = viewModel::openCompose,
                    onRetry = viewModel::loadThreads,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ParentThreadListContent(
    threads: List<ParentMessageThreadDto>,
    loading: Boolean,
    error: String?,
    isEmpty: Boolean,
    onOpenThread: (ParentMessageThreadDto) -> Unit,
    onCompose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // RA-S07: compose-new entry point — parents can now INITIATE a conversation
        // with their child's teacher / the school office, not just reply.
        VButton(
            text = "New message",
            onClick = onCompose,
            full = true,
            size = VButtonSize.Md,
            tone = VButtonTone.Lavender,
            leading = {
                androidx.compose.material3.Icon(
                    VIcons.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        VStateHost(
            loading = loading,
            error = error,
            isEmpty = isEmpty,
            emptyTitle = "No messages yet",
            emptyBody = "Messages from your child's teachers and the school office will appear here.",
            emptyIcon = VIcons.Chat,
            onRetry = onRetry,
        ) {
            VCard {
                threads.forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                    ParentThreadRow(thread = t, onClick = { onOpenThread(t) })
                }
            }
        }
    }
}

@Composable
private fun ParentThreadRow(thread: ParentMessageThreadDto, onClick: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VAvatar(name = thread.senderName.ifBlank { "?" }, src = thread.senderImageUrl, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    thread.senderName,
                    style = VTheme.type.bodyStrong.colored(c.ink),
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(thread.time, style = VTheme.type.caption.colored(c.ink3))
            }
            Spacer(Modifier.height(2.dp))
            Text(
                thread.lastMessage,
                style = VTheme.type.caption.colored(if (thread.isRead) c.ink3 else c.ink2),
                maxLines = 1,
            )
        }
        if (thread.unreadCount > 0) {
            VBadge(text = thread.unreadCount.toString(), tone = VBadgeTone.Accent)
        }
    }
}

/**
 * RA-S07 — parent compose-NEW: pick a recipient (the child's class teacher / school office),
 * type a message, send. `onSend(recipientUserId, body)` starts a real 1:1 conversation.
 */
@Composable
private fun ParentComposeNewContent(
    recipients: List<ParentRecipientDto>,
    loading: Boolean,
    error: String?,
    isEmpty: Boolean,
    sending: Boolean,
    onSend: (String, String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var selected by remember { mutableStateOf<ParentRecipientDto?>(null) }
    var body by remember { mutableStateOf("") }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VStateHost(
                loading = loading,
                error = error,
                isEmpty = isEmpty,
                emptyTitle = "No one to message yet",
                emptyBody = "Link your child to a school to message their teachers and the office.",
                emptyIcon = VIcons.Chat,
                onRetry = onRetry,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("To", style = VTheme.type.caption.colored(c.ink3))
                    VCard {
                        recipients.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                            ParentRecipientRow(
                                recipient = r,
                                isSelected = selected?.id == r.id,
                                onClick = { selected = r },
                            )
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    VInput(
                        value = body,
                        onValueChange = { body = it },
                        placeholder = if (selected == null) "Pick a recipient above…" else "Message ${selected!!.name}…",
                        enabled = selected != null && !sending,
                    )
                }
                VButton(
                    text = "Send",
                    onClick = {
                        val r = selected
                        if (r != null && body.isNotBlank()) onSend(r.id, body)
                    },
                    size = VButtonSize.Md,
                    tone = VButtonTone.Lavender,
                    loading = sending,
                    enabled = selected != null && body.isNotBlank() && !sending,
                )
            }
        }
    }
}

@Composable
private fun ParentRecipientRow(recipient: ParentRecipientDto, isSelected: Boolean, onClick: () -> Unit) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VAvatar(name = recipient.name.ifBlank { "?" }, src = recipient.imageUrl, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(recipient.name, style = VTheme.type.bodyStrong.colored(c.ink))
            Text(recipient.subtitle, style = VTheme.type.caption.colored(c.ink3))
        }
        if (isSelected) {
            VBadge(text = "Selected", tone = VBadgeTone.Accent)
        }
    }
}

@Composable
private fun ParentConversationContent(
    messages: List<ParentMessageDto>,
    loading: Boolean,
    error: String?,
    isEmpty: Boolean,
    sending: Boolean,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var reply by remember { mutableStateOf("") }

    Column(modifier) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VStateHost(
                loading = loading,
                error = error,
                isEmpty = isEmpty,
                emptyTitle = "No messages yet",
                emptyBody = "Send a message below to start the conversation.",
                emptyIcon = VIcons.Chat,
                onRetry = onRetry,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    messages.forEach { msg -> ParentMessageBubble(msg) }
                }
            }
        }

        Box(Modifier.fillMaxWidth().background(c.card).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    VInput(
                        value = reply,
                        onValueChange = { reply = it },
                        placeholder = "Type a message…",
                        enabled = !sending,
                    )
                }
                VButton(
                    text = "Send",
                    onClick = {
                        if (reply.isNotBlank()) {
                            val body = reply
                            reply = ""
                            onSend(body)
                        }
                    },
                    size = VButtonSize.Md,
                    tone = VButtonTone.Lavender,
                    loading = sending,
                    enabled = reply.isNotBlank() && !sending,
                )
            }
        }
    }
}

@Composable
private fun ParentMessageBubble(msg: ParentMessageDto) {
    val c = VTheme.colors
    val isMine = msg.isMine
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isMine) 16.dp else 4.dp,
            bottomEnd = if (isMine) 4.dp else 16.dp,
        )
        Column(
            Modifier
                .clip(bubbleShape)
                .background(if (isMine) c.accent.copy(alpha = 0.16f) else c.cream)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                msg.body,
                style = VTheme.type.body.colored(c.ink).copy(fontWeight = FontWeight.Normal),
            )
            Spacer(Modifier.height(2.dp))
            Text(msg.time, style = VTheme.type.caption.colored(c.ink3))
        }
    }
}
