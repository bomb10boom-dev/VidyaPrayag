package com.littlebridge.enrollplus.ui.v2.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import vidyaprayag.composeapp.generated.resources.Res
import vidyaprayag.composeapp.generated.resources.dmmono_medium
import vidyaprayag.composeapp.generated.resources.dmmono_regular
import vidyaprayag.composeapp.generated.resources.plusjakartasans_bold
import vidyaprayag.composeapp.generated.resources.plusjakartasans_extrabold
import vidyaprayag.composeapp.generated.resources.plusjakartasans_medium
import vidyaprayag.composeapp.generated.resources.plusjakartasans_regular
import vidyaprayag.composeapp.generated.resources.plusjakartasans_semibold

/**
 * VidyaSetu type scale — from design `theme.css` base layer + UI_FIDELITY_AUDIT §0.1/§0.2/§0.4/§13.8.
 *
 * Primary UI typeface is **Plus Jakarta Sans** (400–800); data/number typeface is **DM Mono**
 * (400/500). Both are bundled as `.ttf` under `composeResources/font/` and loaded via
 * [vidyaSetuFontFamilies] (§0.1 fix). The token API is stable: call sites read `VTheme.type.*`.
 *
 * OpenType craft (§13.8): DM Mono data styles request tabular figures (`tnum`); display styles
 * carry the design's negative tracking (`h1 -0.02em`, `h2 -0.01em`) and pinned optical line-heights.
 */
@Immutable
data class VTypography(
    val uiFamily: FontFamily,
    val dataFamily: FontFamily,

    // UI scale (Plus Jakarta Sans)
    val h1: TextStyle,        // 32 / 800 / 1.1 / -0.02em
    val h2: TextStyle,        // 22 / 700 / -0.01em
    val h3: TextStyle,        // 17 / 700
    val h4: TextStyle,        // 14 / 600
    val body: TextStyle,      // 14 / 400
    val bodyStrong: TextStyle,// 14 / 600
    val caption: TextStyle,   // 12 / 500
    val label: TextStyle,     // 11 / 600 UPPER 0.08em  (global `label` element)
    val labelStrong: TextStyle,// 11 / 700 UPPER 0.10em (the React `Label` component) — §0.4
    val inputLabel: TextStyle,// 12 / 600 / none        (VInput label) — §0.4

    // Data scale (DM Mono — tabular)
    val data: TextStyle,      // 15 / 400
    val dataSm: TextStyle,    // 13 / 400
    val dataLg: TextStyle,    // 22 / 500 (hero numbers)
)

/** The two bundled font families. Must be called from a @Composable context (loads resources). */
@Composable
fun vidyaSetuUiFamily(): FontFamily = FontFamily(
    Font(Res.font.plusjakartasans_regular, FontWeight.Normal),
    Font(Res.font.plusjakartasans_medium, FontWeight.Medium),
    Font(Res.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(Res.font.plusjakartasans_bold, FontWeight.Bold),
    Font(Res.font.plusjakartasans_extrabold, FontWeight.ExtraBold),
)

@Composable
fun vidyaSetuDataFamily(): FontFamily = FontFamily(
    Font(Res.font.dmmono_regular, FontWeight.Normal),
    Font(Res.font.dmmono_medium, FontWeight.Medium),
)

/**
 * Build the bundled-font typography. Call once at the app root (e.g. inside the top-level
 * [VTheme]) and pass into `VTheme(typography = …)` so every screen inherits Plus Jakarta Sans /
 * DM Mono without per-call-site font handling.
 */
@Composable
fun vidyaSetuTypography(): VTypography {
    val ui = vidyaSetuUiFamily()
    val data = vidyaSetuDataFamily()
    return remember(ui, data) { defaultVTypography(ui, data) }
}

private const val TNUM = "tnum"

fun defaultVTypography(
    uiFamily: FontFamily = FontFamily.SansSerif,
    dataFamily: FontFamily = FontFamily.Monospace,
): VTypography = VTypography(
    uiFamily = uiFamily,
    dataFamily = dataFamily,
    h1 = TextStyle(fontFamily = uiFamily, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 35.2.sp, letterSpacing = (-0.02).em),
    h2 = TextStyle(fontFamily = uiFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.4.sp, letterSpacing = (-0.01).em),
    // Feature 8 — heading letter-spacing tightening. h1/h2 already carry the
    // design's em-based negative tracking; h3 had none. A subtle -0.3sp makes the
    // 17sp section headers feel more premium without touching size/weight/family.
    // h4 is deliberately LEFT at 0 — it doubles as VButton's label style, where
    // tightening would distort button text (RULE-2: stability over polish).
    h3 = TextStyle(fontFamily = uiFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp, letterSpacing = (-0.3).sp),
    h4 = TextStyle(fontFamily = uiFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    body = TextStyle(fontFamily = uiFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp),
    bodyStrong = TextStyle(fontFamily = uiFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    caption = TextStyle(fontFamily = uiFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 17.sp),
    label = TextStyle(fontFamily = uiFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, lineHeight = 14.sp, letterSpacing = 0.08.em),
    labelStrong = TextStyle(fontFamily = uiFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp, letterSpacing = 0.10.em),
    inputLabel = TextStyle(fontFamily = uiFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, letterSpacing = 0.sp),
    data = TextStyle(fontFamily = dataFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp, fontFeatureSettings = TNUM),
    dataSm = TextStyle(fontFamily = dataFamily, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp, fontFeatureSettings = TNUM),
    dataLg = TextStyle(fontFamily = dataFamily, fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 26.sp, fontFeatureSettings = TNUM),
)
