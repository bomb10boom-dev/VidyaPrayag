package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.shapeSm

// VTag active palette is fixed in the design (`primitives.tsx`): bg #dcf2ef, fg #006a60,
// border rgba(0,106,96,0.18) — independent of the night/warm tone remap. §2#6 / §13.10.
private val TagActiveBg = Color(0xFFDCF2EF)
private val TagActiveFg = Color(0xFF006A60)
private val TagActiveBorder = Color(0x2E006A60) // rgba(0,106,96,0.18)

/** Semantic tones for [VBadge]. Mirrors primitives.tsx `VBadge` tone union.
 *  `Accent` is the website's lavender/violet (#6C5CE0) family — the Parents Portal default
 *  so no parent surface ever renders the legacy teal `Arctic`. */
enum class VBadgeTone { Arctic, Accent, Success, Warning, Danger, Neutral }

/**
 * VBadge — a pill status chip. Background is a soft tint; foreground is the matching ink.
 * Translated from primitives.tsx → `VBadge`.
 */
@Composable
fun VBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: VBadgeTone = VBadgeTone.Arctic,
    // §4.2: optional leading glyph (React `<span className="inline-flex gap-1">{icon} {label}</span>`).
    leadingIcon: ImageVector? = null,
) {
    val c = VTheme.colors
    val (bg, fg) = when (tone) {
        VBadgeTone.Arctic -> c.teal.copy(alpha = 0.16f) to c.tealDeep
        VBadgeTone.Accent -> c.accent.copy(alpha = 0.14f) to c.accentDeep
        VBadgeTone.Success -> c.success.copy(alpha = 0.42f) to c.successInk
        VBadgeTone.Warning -> c.warning.copy(alpha = 0.55f) to c.warningInk
        VBadgeTone.Danger -> c.danger.copy(alpha = 0.55f) to c.dangerInk
        VBadgeTone.Neutral -> c.cream to c.ink2
    }
    // React VBadge: 11 / 600 / 0.04em — NOT uppercase, NOT 0.08em tracking. §matrix.
    val textStyle = VTheme.type.label.colored(fg).copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.04.em,
    )
    val pillModifier = modifier
        .clip(RoundedCornerShape(999.dp))
        .background(bg)
        .padding(horizontal = 10.dp, vertical = 4.dp)

    if (leadingIcon != null) {
        Row(
            modifier = pillModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp), // React gap-1
        ) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Text(text = text, style = textStyle)
        }
    } else {
        Text(text = text, style = textStyle, modifier = pillModifier)
    }
}

/**
 * VTag — a selectable filter chip (e.g. subject pills). Active state turns teal-tinted.
 * Translated from primitives.tsx → `VTag`.
 */
@Composable
fun VTag(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null, // §7.2: People filter chips carry an inline ChevronDown
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    // RA-PP-THEME: opt-in lavender active state for the Parents Portal. When true the active
    // chip uses the website violet (#6C5CE0) instead of the legacy fixed teal. Defaults to false
    // so every other portal is byte-for-byte unchanged (token reuse, no rework — LAW).
    accentActive: Boolean = false,
) {
    val c = VTheme.colors
    // §2#6 / §matrix: active bg is the fixed #dcf2ef, fg #006a60, border rgba(0,106,96,.18).
    val activeBg = if (accentActive) c.accent.copy(alpha = 0.14f) else TagActiveBg
    val activeFg = if (accentActive) c.accentDeep else TagActiveFg
    val activeBorder = if (accentActive) c.accent.copy(alpha = 0.30f) else TagActiveBorder
    val bg = if (active) activeBg else c.cream
    val fg = if (active) activeFg else c.ink2
    val borderColor = if (active) activeBorder else c.shadowTint.copy(alpha = 0.04f)

    var mod = modifier
        .clip(VTheme.dimens.shapeSm)
        .background(bg)
        .border(BorderStroke(1.dp, borderColor), VTheme.dimens.shapeSm)

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        mod = mod.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }

    Row(
        modifier = mod.padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = text,
            style = VTheme.type.caption.colored(fg).copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
        )
        if (trailingIcon != null) {
            // React renders `<ChevronDown size={12} />` inline-block after the label.
            Icon(trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        }
    }
}
