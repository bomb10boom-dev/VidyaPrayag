package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * VScreenScaffold — the phone-frame root for every screen.
 *
 * Translated from primitives.tsx → `PhoneFrame`. Centers a max-440dp column on wide displays
 * (desktop/web), paints the portal background, and stacks an optional [topBar] + scrolling
 * [content] with an optional floating [bottomBar]. The content area fills remaining height
 * and receives enough bottom padding to scroll above the floating navigation.
 */
@Composable
fun VScreenScaffold(
    modifier: Modifier = Modifier,
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = VTheme.colors
    val d = VTheme.dimens
    val floatingBottomBarPadding = if (bottomBar != null) {
        112.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    Box(
        modifier.fillMaxSize().background(c.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .widthIn(max = d.maxContentWidth)
                .background(c.background),
        ) {
            Column(Modifier.fillMaxSize()) {
                topBar?.invoke()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content(
                        PaddingValues(
                            start = d.screenPadding,
                            top = d.screenPadding,
                            end = d.screenPadding,
                            bottom = d.screenPadding + floatingBottomBarPadding,
                        ),
                    )
                }
            }
            if (bottomBar != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    bottomBar()
                }
            }
        }
    }
}

/**
 * VEmptyState — a centered icon-in-circle + title + body + optional action for empty lists.
 * Translated from primitives.tsx → `VEmptyState`.
 */
@Composable
fun VEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    body: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = VTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(c.ink.copy(alpha = if (c.isNight) 0.06f else 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = c.ink3, modifier = Modifier.size(28.dp))
            }
        }
        Text(title, style = VTheme.type.h3.colored(c.ink), textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                body,
                style = VTheme.type.caption.colored(c.ink2),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (action != null) {
            Box(Modifier.padding(top = 8.dp)) { action() }
        }
    }
}

/**
 * VComingSoon — a "PREVIEW" card for not-yet-shipped features, with an optional preview slot
 * and a "Notify me" affordance. Translated from primitives.tsx → `VComingSoon`.
 */
@Composable
fun VComingSoon(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    preview: (@Composable () -> Unit)? = null,
    onNotifyMe: (() -> Unit)? = null,
) {
    val c = VTheme.colors
    VCard(modifier = modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VBadge(text = "● PREVIEW", tone = VBadgeTone.Arctic)
            Text(title, style = VTheme.type.h3.colored(c.ink), textAlign = TextAlign.Center)
            Text(description, style = VTheme.type.caption.colored(c.ink2), textAlign = TextAlign.Center)
            preview?.invoke()
            val interaction = remember { MutableInteractionSource() }
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(c.cream)
                    .clickable(interactionSource = interaction, indication = null, enabled = onNotifyMe != null) { onNotifyMe?.invoke() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VStatusDot(color = c.teal, size = 6.dp)
                Text(
                    "Notify me when ready",
                    style = VTheme.type.caption.colored(c.ink2),
                )
            }
        }
    }
}

/**
 * VConfirmDialog — the universal confirmation gate for destructive actions
 * (logout, delete, remove). RA-21: every confirmation-less destructive action
 * must route through this dialog first.
 *
 * Built entirely from the frozen design language (RULE-6): the panel is a [VCard]
 * on the `--card` surface, actions are [VButton]s ([VButtonVariant.Destructive] for
 * the confirm CTA, [VButtonVariant.Ghost] for cancel), text uses [VTheme.type]
 * tokens and the scrim uses the existing navy `shadowTint` token. Zero new tokens.
 *
 * Renders nothing when [visible] is false. Dismissing (scrim tap / back) calls
 * [onDismiss]; the confirm CTA calls [onConfirm].
 */
@Composable
fun VConfirmDialog(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelLabel: String = "Cancel",
    icon: ImageVector? = null,
) {
    if (!visible) return
    val c = VTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        VCard(modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(c.danger.copy(alpha = if (c.isNight) 0.18f else 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = c.dangerInk, modifier = Modifier.size(26.dp))
                    }
                }
                Text(title, style = VTheme.type.h3.colored(c.ink), textAlign = TextAlign.Center)
                Text(
                    message,
                    style = VTheme.type.caption.colored(c.ink2),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Spacer(Modifier.height(8.dp))
                VButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    full = true,
                    variant = VButtonVariant.Destructive,
                )
                VButton(
                    text = cancelLabel,
                    onClick = onDismiss,
                    full = true,
                    variant = VButtonVariant.Ghost,
                )
            }
        }
    }
}
