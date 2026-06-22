package com.littlebridge.enrollplus.ui.v2.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * VElevation — the design's 3-tier navy-tinted shadow system (UI_FIDELITY_AUDIT §13.1).
 *
 * React (`theme.css:41-43`) defines THREE elevation tokens, each a **two-layer** stack
 * (tight contact + soft ambient) tinted with navy `#26234d` — never pure black:
 *   --shadow-light-1: 0 1px 3px navy@5%, 0 1px 2px navy@3%   → resting cards (VCard)
 *   --shadow-light-2: 0 8px 24px navy@8%                     → raised sheets / popovers / active rows
 *   --shadow-light-3: 0 16px 40px navy@14%                   → modals / bottom sheets / dialogs
 *
 * Compose's `Modifier.shadow()` can't tint its color and behaves differently on Android vs iOS
 * (Skia), so we draw the falloff ourselves as a small stack of concentric, ever-fainter rounded
 * rects below the surface. This is pure common code — identical render on Android & iOS (parity).
 */
enum class VElevationLevel { Card, Raised, Modal }

private data class ShadowSpec(val dy: Dp, val spread: Dp, val alpha: Float, val steps: Int)

private fun specFor(level: VElevationLevel): ShadowSpec = when (level) {
    // resting card: very tight, barely-there 1-3px soft shadow
    VElevationLevel.Card -> ShadowSpec(dy = 2.dp, spread = 4.dp, alpha = 0.06f, steps = 4)
    // raised: 8px down, 24px soft ambient
    VElevationLevel.Raised -> ShadowSpec(dy = 8.dp, spread = 24.dp, alpha = 0.09f, steps = 8)
    // modal: 16px down, 40px soft ambient
    VElevationLevel.Modal -> ShadowSpec(dy = 16.dp, spread = 40.dp, alpha = 0.15f, steps = 12)
}

/**
 * Draw the navy-tinted, multi-layer soft shadow for [level] behind a rounded surface of [radius].
 * Apply BEFORE clip/background so the shadow sits behind the surface fill.
 */
@Composable
fun Modifier.vElevation(
    level: VElevationLevel,
    radius: Dp = VTheme.dimens.radiusCard,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || VTheme.colors.isNight) return this
    val tint = VTheme.colors.shadowTint
    val spec = specFor(level)
    return this.drawBehind {
        val steps = spec.steps
        val spreadPx = spec.spread.toPx()
        val dyPx = spec.dy.toPx()
        val rPx = radius.toPx()
        // Outer (faintest, largest) → inner (densest, tightest). Summed alphas approximate a blur.
        for (i in steps downTo 1) {
            val t = i / steps.toFloat()              // 1f outermost … →0 innermost
            val grow = spreadPx * t                  // how far this ring extends beyond the surface
            val a = spec.alpha * (1f - t) * (1f - t) // quadratic falloff toward the edge
            if (a <= 0f) continue
            drawRoundRect(
                color = tint.copy(alpha = a),
                topLeft = Offset(-grow, dyPx * t - grow * 0.25f),
                size = Size(size.width + grow * 2f, size.height + grow * 2f),
                cornerRadius = CornerRadius(rPx + grow, rPx + grow),
            )
        }
    }
}
