package com.littlebridge.enrollplus.ui.v2.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * VMotion — the design's spring/entrance tokens (UI_FIDELITY_AUDIT §13.2/§13.3).
 *
 * React (`Auth.tsx`) orchestrates a timed reveal ladder using framer-motion springs with literal
 * stiffness/damping numbers. Compose springs use `dampingRatio` + `stiffness`, so we convert the
 * framer (stiffness, damping) pairs exactly:  dampingRatio = damping / (2 * sqrt(stiffness)).
 *
 * Nothing per-screen hardcodes a spring — screens read these tokens.
 */
object VMotion {
    /** framer spring(stiffness=240, damping=22) — logo/cards. */
    val springSoft: AnimationSpec<Float> = framerSpring(stiffness = 240f, damping = 22f)

    /** framer spring(stiffness=220, damping=28) — bottom sheet rise. */
    val springSheet: AnimationSpec<Float> = framerSpring(stiffness = 220f, damping = 28f)

    /** framer spring(stiffness=260, damping=30) — login card. */
    val springCard: AnimationSpec<Float> = framerSpring(stiffness = 260f, damping = 30f)

    /** framer spring(stiffness=300) — success tick / snappy. */
    val springSnappy: AnimationSpec<Float> = framerSpring(stiffness = 300f, damping = 20f)

    /**
     * The snappy spring as a *generic* spec, usable for any animatable type (Dp, Offset, Color…).
     *
     * [springSnappy] is typed `AnimationSpec<Float>`, so it cannot be passed to e.g.
     * `animateDpAsState` (which needs `AnimationSpec<Dp>`). A Compose `spring` is per-type, so this
     * factory rebuilds the same (stiffness=300, damping=20 → ratio≈0.577) feel for whatever `T`
     * the call site needs.
     */
    fun <T> snappy(): SpringSpec<T> =
        spring(dampingRatio = 20f / (2f * sqrt(300f)), stiffness = 300f)

    /** fadeUp(delayMs): opacity 0→1 + slide y from +12 (matches the React reveal ladder). */
    fun fadeUp(delayMs: Int, durationMs: Int = 450, fromY: Int = 12): EnterTransition =
        fadeIn(animationSpec = tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(durationMs, delayMillis = delayMs, easing = FastOutSlowInEasing),
                initialOffsetY = { fromY },
            )

    // ── Screen transitions (Feature 4) ────────────────────────────────────────
    // Subtle "momentum" cues: the incoming screen slides a short distance (NOT a
    // full-screen swipe) while fading in; the outgoing screen drifts the other way
    // while fading out. Offsets are intentionally small (≈30px) so the motion reads
    // as a directional hint, not a carousel. Easing/duration reuse the design tokens
    // (FastOutSlowInEasing) already used by fadeUp. All APIs are commonMain-safe.

    /** Momentum distance for screen transitions, in px. Within the 20–40px spec band. */
    private const val MOMENTUM_PX = 30
    private const val SCREEN_MS = 280

    private fun screenTween() = tween<Float>(SCREEN_MS, easing = FastOutSlowInEasing)
    private fun screenTweenInt() = tween<androidx.compose.ui.unit.IntOffset>(SCREEN_MS, easing = FastOutSlowInEasing)

    /**
     * Forward horizontal momentum: incoming enters from the right (+px) and the
     * outgoing exits to the left (−px), both cross-fading. Used for funnel screens
     * that advance "deeper" (landing → auth → discovery …).
     */
    fun forwardSlide(): ContentTransform =
        (fadeIn(screenTween()) + slideInHorizontally(screenTweenInt()) { MOMENTUM_PX }) togetherWith
            (fadeOut(screenTween()) + slideOutHorizontally(screenTweenInt()) { -MOMENTUM_PX })

    /**
     * Modal vertical momentum: incoming rises from below (+px) and fades in while the
     * outgoing fades out. Used for one-time gate steps (link-child / onboarding /
     * first-login) which read as modal sheets layered over the portal.
     */
    fun modalRise(): ContentTransform =
        (fadeIn(screenTween()) + slideInVertically(screenTweenInt()) { MOMENTUM_PX }) togetherWith
            (fadeOut(screenTween()) + slideOutVertically(screenTweenInt()) { -MOMENTUM_PX / 2 })

    /** Quiet cross-fade only — for the brief resolving frame (no directional cue). */
    fun quietFade(): ContentTransform =
        fadeIn(screenTween()) togetherWith fadeOut(screenTween())
}

/** Convert a framer-motion (stiffness, damping) pair to a Compose float spring. */
private fun framerSpring(stiffness: Float, damping: Float): AnimationSpec<Float> {
    val ratio = (damping / (2f * sqrt(stiffness))).coerceIn(0.05f, 1f)
    return spring(dampingRatio = ratio, stiffness = stiffness)
}

/**
 * pressScale — every tappable surface should visibly *give* on press (scale → .98), matching the
 * React `active:scale-[0.98]` micro-interaction (§13.3). Drive it from an [interactionSource] so
 * it works identically on Android touch and iOS.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.98f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    this.scale(scale)
}

// ─────────────────────────────────────────────────────────────────────────────
// Feature 5 — Card press scale + staggered list entrance
//
// Two stricter variants of the existing micro-interaction primitives, kept
// SEPARATE from [pressScale] (which is wired into ~30+ existing callsites and
// must not move) so we never regress visual rhythm anywhere it already lives.
// New navigable cards opt into [cardPressScale] (graphicsLayer-based, no
// ripple conflict, .97f spec target). New list screens opt into
// [staggeredItemEntrance] for the post-skeleton ladder.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * cardPressScale — navigable-card press feedback, spec-aligned to Feature 5.
 *
 * - Scales to **0.97f** on pointer down, springs back to 1.0f on release.
 * - Applied via [graphicsLayer] (NOT [scale]) so it composes cleanly with a
 *   `Modifier.clickable(indication = ripple())` underneath — no ripple
 *   clipping artefacts when the card scales.
 * - Spring is `Spring.DampingRatioMediumBouncy` for a tactile-not-rubbery feel.
 *
 * Caller responsibility (RULE-2 / RULE-3):
 *  - Apply ONLY to cards that are navigable (have an `onClick`). Static
 *    display cards never scale — they would feel broken on tap.
 *  - Stack BEFORE `clickable(...)` in the modifier chain so the press scale
 *    wraps the ripple visually, never the other way round.
 */
fun Modifier.cardPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cardPressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * staggeredItemEntrance — the post-skeleton ladder (Feature 5).
 *
 * When [trigger] flips from false → true, each item runs a 220ms `fadeIn +
 * slideInVertically(+16px → 0)` with a `index * 40ms` delay, capped at 200ms
 * (so item 6+ all enter together — the rhythm never drags). When [trigger] is
 * already true on first composition (e.g. the user returned to a list whose
 * data was cached), the animation runs once on mount; subsequent recompositions
 * do NOT re-run it (RULE-2: no recomposition loops).
 *
 * Apply via [Modifier.graphicsLayer] so it never causes layout shift — only
 * the alpha and translationY of the existing item box change per frame.
 *
 * ```
 * itemsIndexed(rows) { i, row ->
 *     RowCard(row, modifier = Modifier.staggeredItemEntrance(i, ready))
 * }
 * ```
 *
 * [trigger] should be wired to "data is loaded and non-empty" (i.e. the
 * skeleton → content transition has begun). It MUST NOT toggle back to false
 * on a refresh — otherwise items would shake on every pull-to-refresh.
 */
@Composable
fun Modifier.staggeredItemEntrance(
    index: Int,
    trigger: Boolean,
    stepMs: Int = 40,
    maxDelayMs: Int = 200,
    durationMs: Int = 220,
    fromY: Float = 16f,
): Modifier {
    val alpha = remember { Animatable(0f) }
    val ty = remember { Animatable(fromY) }
    // The `trigger` key + `index` key means each item animates exactly ONCE per
    // (data-load, position) pair. Compose deliberately calls LaunchedEffect's
    // block again only when a key changes — so a pull-to-refresh that keeps
    // `trigger = true` will NOT re-run the entrance.
    LaunchedEffect(trigger, index) {
        if (trigger) {
            val delayMs = (index * stepMs).coerceAtMost(maxDelayMs).toLong()
            kotlinx.coroutines.delay(delayMs)
            // LaunchedEffect's own CoroutineScope receiver: launch the two anims
            // in parallel so alpha and translationY run together (the spec's
            // "fade + slide").
            launch {
                alpha.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing))
            }
            launch {
                ty.animateTo(0f, tween(durationMs, easing = FastOutSlowInEasing))
            }
        }
    }
    return this.graphicsLayer {
        this.alpha = alpha.value
        translationY = ty.value * density
    }
}

/**
 * shakeOnError — horizontal field-shake on validation failure (Feature 7).
 *
 * When [trigger] flips to true the field container snaps through
 * `+8dp → -8dp → +4dp → -4dp → 0` over ~300ms via a single [Animatable], the
 * exact keyframe ladder in the spec. Applied via [graphicsLayer] translationX so
 * it never reflows neighbouring content (RULE-2: no layout shift). The shake
 * fires exactly once per `false → true` edge of [trigger]; holding [trigger] true
 * does not loop it.
 *
 * Pair this with the existing **error colour token** on the field's border
 * (`VTheme.colors.dangerInk`) — this modifier only owns the *motion*, never a
 * colour, so RULE-1 is preserved.
 *
 * ```
 * VInput(..., modifier = Modifier.shakeOnError(state.emailError != null))
 * ```
 */
@Composable
fun Modifier.shakeOnError(trigger: Boolean): Modifier {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            // Keyframe ladder: +8 → -8 → +4 → -4 → 0 (dp), each leg a short tween
            // so the whole shake lands in ~300ms. snapTo seeds the first frame so
            // there is no easing-in lag on the initial kick.
            val legs = listOf(8f, -8f, 4f, -4f, 0f)
            for (target in legs) {
                offsetX.animateTo(target, tween(durationMillis = 60, easing = FastOutSlowInEasing))
            }
        } else {
            offsetX.snapTo(0f)
        }
    }
    return this.graphicsLayer { translationX = offsetX.value * density }
}
