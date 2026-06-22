package com.littlebridge.enrollplus.ui.v2.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

/**
 * VidyaSetu design-system theme provider.
 *
 * Wraps content in the three token CompositionLocals ([LocalVColors], [LocalVType], [LocalVDimens])
 * plus the active [LocalVPortalTone]. Every `V*` component reads from these — there is no other
 * source of color/type/geometry in the new UI.
 *
 * Usage:  `VTheme(tone = VPortalTone.Light) { /* screens */ }`
 *
 * Access tokens anywhere via the [VTheme] accessor object:
 *   `VTheme.colors.tealDeep`, `VTheme.type.h1`, `VTheme.dimens.md`
 */
@Composable
fun VTheme(
    tone: VPortalTone = VPortalTone.Light,
    colors: VColors = vColorsFor(tone),
    typography: VTypography = vidyaSetuTypography(),
    dimens: VDimens = DefaultVDimens,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVColors provides colors,
        LocalVType provides typography,
        LocalVDimens provides dimens,
        LocalVPortalTone provides tone,
        content = content,
    )
}

val LocalVColors: ProvidableCompositionLocal<VColors> =
    staticCompositionLocalOf { LightVColors }

val LocalVType: ProvidableCompositionLocal<VTypography> =
    staticCompositionLocalOf { defaultVTypography() }

val LocalVDimens: ProvidableCompositionLocal<VDimens> =
    staticCompositionLocalOf { DefaultVDimens }

val LocalVPortalTone: ProvidableCompositionLocal<VPortalTone> =
    staticCompositionLocalOf { VPortalTone.Light }

/** Ergonomic accessor — `VTheme.colors`, `VTheme.type`, `VTheme.dimens`, `VTheme.tone`. */
object VTheme {
    val colors: VColors
        @Composable get() = LocalVColors.current
    val type: VTypography
        @Composable get() = LocalVType.current
    val dimens: VDimens
        @Composable get() = LocalVDimens.current
    val tone: VPortalTone
        @Composable get() = LocalVPortalTone.current
}

/** Helper: a TextStyle colored with an explicit color (keeps call sites terse). */
fun TextStyle.colored(color: androidx.compose.ui.graphics.Color): TextStyle = copy(color = color)
