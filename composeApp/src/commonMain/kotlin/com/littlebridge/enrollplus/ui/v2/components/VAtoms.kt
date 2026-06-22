package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

/**
 * VDivider — a hairline rule using the design's subtle border token.
 * Translated from primitives.tsx → `VDivider`.
 *
 * Feature 8 — divider refinement: weight reduced 1dp → 0.5dp. This is the single
 * divider primitive in the app (no raw Material `Divider`/`HorizontalDivider`
 * anywhere), so every rule across every screen becomes a true hairline in one
 * change. The colour token ([VTheme.colors.hairline], navy@6%) is unchanged —
 * only the weight (RULE-1: no new colour). On all densities Compose renders a
 * 0.5dp box as the thinnest physically-resolvable line, never zero, so there is
 * no risk of dividers disappearing (RULE-2).
 */
@Composable
fun VDivider(
    modifier: Modifier = Modifier,
    color: Color = VTheme.colors.hairline,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color),
    )
}

/**
 * VLabel — the all-caps, letter-spaced micro-label (e.g. "TODAY'S TIMETABLE").
 * Translated from primitives.tsx → `Label`.
 */
@Composable
fun VLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = VTheme.colors.ink3,
) {
    Text(
        text = text.uppercase(),
        // §0.4: the React `Label` component is 11/700/0.10em uppercase ink-3 → labelStrong.
        style = VTheme.type.labelStrong.colored(color),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * VStatusDot — a small filled circle used as a live/status indicator.
 * Translated from primitives.tsx → `VStatusDot`.
 */
@Composable
fun VStatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp,
    ring: Boolean = false,
) {
    val ringColor = if (ring) color.copy(alpha = 0.22f) else Color.Transparent
    Box(
        modifier
            .size(if (ring) size + 8.dp else size)
            .clip(CircleShape)
            .background(ringColor),
    ) {
        Box(
            Modifier
                .padding(if (ring) 4.dp else 0.dp)
                .size(size)
                .clip(CircleShape)
                .background(color),
        )
    }
}
