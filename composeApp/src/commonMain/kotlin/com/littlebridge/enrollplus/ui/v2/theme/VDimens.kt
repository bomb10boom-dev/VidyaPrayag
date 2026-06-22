package com.littlebridge.enrollplus.ui.v2.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing (base-4) + border radii, from design `theme.css` @theme + rebuild plan §3.5.
 * Single source for all geometry so screens never hardcode dp values.
 */
@Immutable
data class VDimens(
    // Spacing scale
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,

    // Screen padding
    val screenPadding: Dp = 16.dp,

    // Radii
    val radiusSm: Dp = 6.dp,    // chip / tag         (theme.css --radius-sm)
    val radiusMd: Dp = 10.dp,   // legacy md
    val radiusInput: Dp = 12.dp,// VInput              (React `rounded-[12px]`) — §0 / matrix
    val radiusLg: Dp = 14.dp,   // theme.css --radius-lg
    val radiusCard: Dp = 16.dp, // VCard               (React `rounded-[16px]`) — §0.3
    val radiusXl: Dp = 20.dp,   // sheet / modal
    val radiusSheet: Dp = 32.dp,// bottom sheet over hero (React `borderTopRadius:32`)
    val radiusPill: Dp = 999.dp,

    // Frame
    val maxContentWidth: Dp = 440.dp,
)

val DefaultVDimens = VDimens()

// Convenient shape helpers (computed from radii)
val VDimens.shapeSm get() = RoundedCornerShape(radiusSm)
val VDimens.shapeMd get() = RoundedCornerShape(radiusMd)
val VDimens.shapeInput get() = RoundedCornerShape(radiusInput)
val VDimens.shapeLg get() = RoundedCornerShape(radiusLg)
val VDimens.shapeCard get() = RoundedCornerShape(radiusCard)
val VDimens.shapeXl get() = RoundedCornerShape(radiusXl)
val VDimens.shapePill get() = RoundedCornerShape(radiusPill)
