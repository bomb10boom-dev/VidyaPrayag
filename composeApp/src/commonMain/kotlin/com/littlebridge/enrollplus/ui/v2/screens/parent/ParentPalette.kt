package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.littlebridge.enrollplus.ui.v2.theme.VColors

/**
 * ParentPalette — the single source of truth for the Parents Portal's *harmonious accent rotation*.
 *
 * The portal's brand accent is lavender/violet, but a wall-to-wall violet tint reads cheap and
 * monotone. To give list rows, subject chips, competency bars and metric tiles individual identity
 * — while still harmonising with the lavender brand — every "per-item" colour is drawn from this
 * one rotating palette (violet → teal → sky → peach → deep-teal). One palette, used identically
 * everywhere (Academics, Covered-today, detail overlays) so the whole portal reads consistent.
 *
 * Colours mirror the website design tokens: `colors.sky` (#6C8DF5) and `colors.peach` (#FF8A65)
 * plus the app theme's accent / teal / tealDeep tokens.
 */
@Composable
fun parentSubjectPalette(c: VColors): List<Color> = listOf(
    c.accent,          // violet (brand)
    c.teal,            // teal
    Color(0xFF6C8DF5), // sky (website `colors.sky`)
    Color(0xFFFF8A65), // peach (website `colors.peach`)
    c.tealDeep,        // deep teal
)

/**
 * Deterministically pick a harmonious accent for an item, keyed off a stable string (e.g. a
 * subject name). Using the name's hash — rather than list position — means the SAME subject keeps
 * the SAME colour wherever it appears across the portal (a "Maths" chip is the same hue on the
 * dashboard, in Academics and in the detail overlay), which reads intentional and premium.
 */
@Composable
fun parentSubjectColor(c: VColors, key: String): Color {
    val palette = parentSubjectPalette(c)
    if (key.isBlank()) return palette.first()
    // Stable, platform-independent hash (kotlin.String.hashCode is consistent on KMP targets).
    val idx = ((key.hashCode() % palette.size) + palette.size) % palette.size
    return palette[idx]
}
