package com.littlebridge.enrollplus.ui.v2.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * VidyaSetu authoritative color tokens.
 *
 * Lifted verbatim from the design source of truth: `UI screens/src/styles/theme.css`.
 * Three brand families (teal / navy / lavender) + ink scale + data-only semantics, with a
 * deep-black "night" variant. NOTHING in the UI hardcodes a hex value — everything reads from
 * [VColors] provided via [LocalVColors].
 */
@Immutable
data class VColors(
    // ── Brand ───────────────────────────────────────────────────────────────
    val teal: Color,
    val tealDeep: Color,
    val navy: Color,
    val navyDeep: Color,
    val lavender: Color,   // app background (light/parent)
    val lavenderLight: Color,   // app background (light/parent)
    val cream: Color,      // secondary / input surface
    val warmOrange: Color,

    // ── Accent (lavender / violet family) — the website's `--accent` ──────────
    // Ported verbatim from `website/tailwind.config.ts → colors.accent` and the
    // reference parent dashboard (`PhoneMockup.tsx`), where the violet `#6C5CE0`
    // is the dominant active-state accent (rings, pills, active tabs, sparklines).
    // Structured as a first-class brand family so ANY portal can adopt it later
    // (Parents Portal is the first to migrate). NOTHING per-screen hardcodes the
    // hex — every accent read goes through [VColors.accent*] / [accentTint].
    val accent: Color,      // #6C5CE0 — primary active accent
    val accentSoft: Color,  // #8B7EE8 — soft step (gradients, hovers)
    val accentDeep: Color,  // #544AB8 — deep step (ink on accent tints, eyebrow text)
    val accentTint: Color,  // canvas behind floating cards (cool off-white lavender, #F4F3FA)

    // ── Ink (text) ──────────────────────────────────────────────────────────
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val placeholder: Color,

    // ── Surfaces ──────────────────────────────────────────────────────────────
    val background: Color,   // page background
    val card: Color,         // elevated card surface
    val border1: Color,      // subtle border
    val border2: Color,      // stronger border
    val hairline: Color,     // navy@6% (#26234d) — every 1px border/divider per §13.9
    val shadowTint: Color,   // navy (#26234d) base for the tinted elevation system (§13.1)

    // ── Semantic (data states ONLY — never branding) ─────────────────────────
    val success: Color,
    val successInk: Color,
    val warning: Color,
    val warningInk: Color,
    val danger: Color,
    val dangerInk: Color,

    // Convenience: is this a dark (night) palette?
    val isNight: Boolean,
)

/** Which surface aesthetic a portal renders with. */
enum class VPortalTone { Light, Warm, Night }

// ─────────────────────────────────────────────────────────────────────────────
// Raw token values (single declaration site — mirrors theme.css :root / .theme-night)
// ─────────────────────────────────────────────────────────────────────────────

private object Raw {
    // Brand (light)
    val teal = Color(0xFF3CB9A9)
    val tealDeep = Color(0xFF006A60)
    val navy = Color(0xFF26234D)
    val navyDeep = Color(0xFF1A1838)
    val lavender = Color(0xFFFCF8FF)
    val lavenderLight = Color(0xFFEAE6FA)
    val cream = Color(0xFFF5F5F3)
    val warmOrange = Color(0xFF9E421A)

    // Accent (lavender/violet) — website tailwind `colors.accent`.
    val accent = Color(0xFF6C5CE0)
    val accentSoft = Color(0xFF8B7EE8)
    val accentDeep = Color(0xFF544AB8)
    val accentTint = Color(0xFFF4F3FA) // website `colors.canvas` — cool lavender card canvas

    // Ink (light)
    val ink = Color(0xFF1A2422)
    val ink2 = Color(0xFF3D4947)
    val ink3 = Color(0xFF6D7A77)
    val placeholder = Color(0xFFBCC9C6)

    // Surfaces (light)
    val cardLight = Color(0xFFFFFFFF)
    val border1Light = Color(0x0F080808) // rgba(8,8,8,.06)
    val border2Light = Color(0x1A080808) // rgba(8,8,8,.10)
    val hairlineLight = Color(0x0F26234D) // rgba(38,35,77,.06) — navy-tinted hairline (§13.9)
    val shadowNavy = Color(0xFF26234D)    // navy base for tinted elevation (§13.1)

    // Semantic fills + inks (shared light)
    val success = Color(0xFFA8E6CF)
    val successInk = Color(0xFF1F7A4D)
    val warning = Color(0xFFFFD4A3)
    val warningInk = Color(0xFFB3651A)
    val danger = Color(0xFFFFADA8)
    val dangerInk = Color(0xFFB3261E)

    // ── Night (.theme-night) ──
    val nightBg = Color(0xFF050505)
    val nightCard = Color(0xFF0E0E10)
    val nightTinted = Color(0xFF141416) // cream equivalent
    val nightNavy = Color(0xFFF3F0FF)   // inverted brand-solid → near-white
    val nightNavyDeep = Color(0xFFFFFFFF)
    val nightTeal = Color(0xFF3CD1BE)
    val nightWarmOrange = Color(0xFFFFB37A)
    // Accent stays punchy on black (theme.css §"keep accents punchy on black").
    val nightAccent = Color(0xFF8B7EE8)
    val nightAccentSoft = Color(0xFFA99EF0)
    val nightAccentDeep = Color(0xFF6C5CE0)
    val nightAccentTint = Color(0xFF15141C) // violet-tinted near-black card canvas
    val nightInk = Color(0xFFF4F4F6)
    val nightInk2 = Color(0xFFB9BCC4)
    val nightInk3 = Color(0xFF7A7E89)
    val nightPlaceholder = Color(0xFF50545E)
    val nightBorder1 = Color(0x0FFFFFFF) // rgba(255,255,255,.06)
    val nightBorder2 = Color(0x1AFFFFFF) // rgba(255,255,255,.10)
    val nightSuccessInk = Color(0xFF5FD6A4)
    val nightWarningInk = Color(0xFFFFC275)
    val nightDangerInk = Color(0xFFFF8A82)
}

/** Light / warm palette (Parent, Discovery, and the warm Admin/Teacher aesthetic). */
val LightVColors = VColors(
    teal = Raw.teal,
    tealDeep = Raw.tealDeep,
    navy = Raw.navy,
    navyDeep = Raw.navyDeep,
    lavender = Raw.lavender,
    lavenderLight = Raw.lavenderLight,
    cream = Raw.cream,
    warmOrange = Raw.warmOrange,
    accent = Raw.accent,
    accentSoft = Raw.accentSoft,
    accentDeep = Raw.accentDeep,
    accentTint = Raw.accentTint,
    ink = Raw.ink,
    ink2 = Raw.ink2,
    ink3 = Raw.ink3,
    placeholder = Raw.placeholder,
    background = Raw.lavender,
    card = Raw.cardLight,
    border1 = Raw.border1Light,
    border2 = Raw.border2Light,
    hairline = Raw.hairlineLight,
    shadowTint = Raw.shadowNavy,
    success = Raw.success,
    successInk = Raw.successInk,
    warning = Raw.warning,
    warningInk = Raw.warningInk,
    danger = Raw.danger,
    dangerInk = Raw.dangerInk,
    isNight = false,
)

/** Deep-black premium night palette. */
val NightVColors = VColors(
    teal = Raw.nightTeal,
    tealDeep = Raw.nightTeal,
    navy = Raw.nightNavy,
    navyDeep = Raw.nightNavyDeep,
    lavender = Raw.nightBg,
    lavenderLight = Raw.lavenderLight,
    cream = Raw.nightTinted,
    warmOrange = Raw.nightWarmOrange,
    accent = Raw.nightAccent,
    accentSoft = Raw.nightAccentSoft,
    accentDeep = Raw.nightAccentDeep,
    accentTint = Raw.nightAccentTint,
    ink = Raw.nightInk,
    ink2 = Raw.nightInk2,
    ink3 = Raw.nightInk3,
    placeholder = Raw.nightPlaceholder,
    background = Raw.nightBg,
    card = Raw.nightCard,
    border1 = Raw.nightBorder1,
    border2 = Raw.nightBorder2,
    hairline = Raw.nightBorder1,
    shadowTint = Color(0xFF000000),
    success = Raw.success,
    successInk = Raw.nightSuccessInk,
    warning = Raw.warning,
    warningInk = Raw.nightWarningInk,
    danger = Raw.danger,
    dangerInk = Raw.nightDangerInk,
    isNight = true,
)

fun vColorsFor(tone: VPortalTone): VColors = when (tone) {
    VPortalTone.Light, VPortalTone.Warm -> LightVColors
    VPortalTone.Night -> NightVColors
}
