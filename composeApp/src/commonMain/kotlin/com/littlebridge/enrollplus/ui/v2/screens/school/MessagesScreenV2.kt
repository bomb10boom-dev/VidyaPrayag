package com.littlebridge.enrollplus.ui.v2.screens.school

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
import androidx.compose.foundation.shape.CircleShape
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
import com.littlebridge.enrollplus.feature.admin.domain.model.Message
import com.littlebridge.enrollplus.feature.admin.domain.model.MessageThread
import com.littlebridge.enrollplus.feature.admin.presentation.ComposeState
import com.littlebridge.enrollplus.feature.admin.presentation.ConversationState
import com.littlebridge.enrollplus.feature.admin.presentation.MessageRecipient
import com.littlebridge.enrollplus.feature.admin.presentation.MessagesState
import com.littlebridge.enrollplus.feature.admin.presentation.MessagesViewModel
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
 * MessagesScreenV2 — admin Messages inbox + conversation detail.
 *
 * Wired to the real [MessagesViewModel] (`GET /api/v1/school/messages/threads`,
 * `POST /api/v1/school/messages`, `POST /threads/{id}/read`).
 *
 * Two modes:
 *   • Thread list (default) — VCard rows with avatar, sender name/role, last message,
 *     time, unread VBadge (Arctic tone).
 *   • Conversation detail (when [ConversationState.threadId] is non-null) — bubble
 *     messages (mine = teal-tint, theirs = cream), bottom VInput + Send VButton.
 *
 * Rendered as a portal overlay; back chevron returns to [SchoolPortalV2] tabs.
 * No MockV2. Three states via [VStateHost] (LAW 3).
 */
@Composable
fun MessagesScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val isLoading by viewModel.isLoading.collectAsStateV2()
    val errorMessage by viewModel.errorMessage.collectAsStateV2()
    val conversation by viewModel.conversation.collectAsStateV2()
    val compose by viewModel.compose.collectAsStateV2()

    // RA-S07: the compose-new sheet is the topmost layer — back closes it first.
    if (compose.isOpen) {
        ComposeNewContent(
            compose = compose,
            isSending = state.isSending,
            onSend = viewModel::composeNew,
            onClose = viewModel::closeCompose,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    // If a conversation is open, back closes it first; otherwise back exits the overlay.
    val backHandler: () -> Unit = {
        if (conversation.threadId != null) viewModel.closeConversation() else onBack()
    }

    val title = if (conversation.threadId != null) {
        conversation.senderName.ifBlank { "Conversation" }
    } else {
        "Messages"
    }

    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = title, onBack = backHandler)

        if (conversation.threadId != null) {
            ConversationContent(
                conversation = conversation,
                onSend = viewModel::sendReply,
                onClearError = viewModel::clearConversationError,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ThreadListContent(
                state = state,
                isLoading = isLoading,
                error = errorMessage,
                onOpenThread = viewModel::openConversation,
                onRetry = viewModel::refresh,
                onClearError = viewModel::clearError,
                onCompose = viewModel::openCompose,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ThreadListContent(
    state: MessagesState,
    isLoading: Boolean,
    error: String?,
    onOpenThread: (String) -> Unit,
    onRetry: () -> Unit,
    onClearError: () -> Unit,
    onCompose: () -> Unit,
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
        // RA-S07: compose-new entry point — admins can now INITIATE a conversation (with a
        // teacher), not just reply. The recipient picker + send live in the compose sheet.
        VButton(
            text = "New message",
            onClick = onCompose,
            full = true,
            size = VButtonSize.Md,
            tone = VButtonTone.Teal,
            leading = {
                androidx.compose.material3.Icon(
                    VIcons.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        VStateHost(
            loading = isLoading,
            error = error,
            isEmpty = state.threads.isEmpty(),
            emptyTitle = "No messages yet",
            emptyBody = "Your inbox will populate as parents and teachers reach out.",
            emptyIcon = VIcons.Chat,
            onRetry = onRetry,
        ) {
            VCard {
                state.threads.forEachIndexed { i, t ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                    ThreadRow(thread = t, onClick = { onOpenThread(t.id) })
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: MessageThread, onClick: () -> Unit) {
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
            VBadge(text = thread.unreadCount.toString(), tone = VBadgeTone.Arctic)
        }
    }
}

@Composable
private fun ConversationContent(
    conversation: ConversationState,
    onSend: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var reply by remember { mutableStateOf("") }

    Column(modifier) {
        // Body
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VStateHost(
                loading = conversation.isLoading,
                error = conversation.error,
                isEmpty = conversation.messages.isEmpty(),
                emptyTitle = "No messages yet",
                emptyBody = "Start the conversation by sending a message below.",
                emptyIcon = VIcons.Chat,
                onRetry = onClearError,
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    conversation.messages.forEach { msg ->
                        MessageBubble(msg)
                    }
                }
            }
        }

        // Reply composer
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
                        enabled = !conversation.isSending,
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
                    tone = VButtonTone.Teal,
                    loading = conversation.isSending,
                    enabled = reply.isNotBlank() && !conversation.isSending,
                )
            }
        }
    }
}

/**
 * RA-S07 — compose-new screen: pick a recipient (a teacher in the school), type a message, send.
 * `onSend(recipientUserId, body)` starts a real 1:1 conversation via the two-row engine.
 */
@Composable
private fun ComposeNewContent(
    compose: ComposeState,
    isSending: Boolean,
    onSend: (String, String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var selected by remember { mutableStateOf<MessageRecipient?>(null) }
    var body by remember { mutableStateOf("") }

    Column(modifier) {
        VBackHeader(title = "New message", onBack = onClose)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            VStateHost(
                loading = compose.isLoadingRecipients,
                error = compose.error,
                isEmpty = compose.candidates.isEmpty(),
                emptyTitle = "No recipients",
                emptyBody = "Add teachers to your school to start a conversation.",
                emptyIcon = VIcons.Chat,
                onRetry = onClose,
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
                        compose.candidates.forEachIndexed { i, r ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                            RecipientRow(
                                recipient = r,
                                isSelected = selected?.id == r.id,
                                onClick = { selected = r },
                            )
                        }
                    }
                }
            }
        }

        // Composer (recipient must be chosen before sending).
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
                        enabled = selected != null && !isSending,
                    )
                }
                VButton(
                    text = "Send",
                    onClick = {
                        val r = selected
                        if (r != null && body.isNotBlank()) onSend(r.id, body)
                    },
                    size = VButtonSize.Md,
                    tone = VButtonTone.Teal,
                    loading = isSending,
                    enabled = selected != null && body.isNotBlank() && !isSending,
                )
            }
        }
    }
}

@Composable
private fun RecipientRow(recipient: MessageRecipient, isSelected: Boolean, onClick: () -> Unit) {
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
        VAvatar(name = recipient.name.ifBlank { "?" }, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(recipient.name, style = VTheme.type.bodyStrong.colored(c.ink))
            Text(recipient.subtitle, style = VTheme.type.caption.colored(c.ink3))
        }
        if (isSelected) {
            VBadge(text = "Selected", tone = VBadgeTone.Arctic)
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
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
                .background(if (isMine) c.teal.copy(alpha = 0.16f) else c.cream)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                msg.body,
                style = VTheme.type.body.colored(c.ink).copy(fontWeight = FontWeight.Normal),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                msg.time,
                style = VTheme.type.caption.colored(c.ink3),
            )
        }
    }
}
