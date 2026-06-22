package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import androidx.compose.foundation.Image
import com.littlebridge.enrollplus.ui.v2.theme.VTheme

/** Deterministic, soft pastel palette — mirrors primitives.tsx `VAvatar` palette. */
private val AvatarPalette = listOf(
    Color(0xFFC8DEFF),
    Color(0xFFA8E6CF),
    Color(0xFFFFD4A3),
    Color(0xFFFFADA8),
    Color(0xFFE0D4FF),
    Color(0xFFD4F3FF),
)

/** Initials from a display name: first letter of the first two words, upper-cased. */
private fun initialsOf(name: String): String =
    name.trim().split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

/** Stable background color picked by summing char codes — same name always yields same color. */
private fun avatarColorFor(name: String): Color {
    val hash = name.sumOf { it.code }
    return AvatarPalette[((hash % AvatarPalette.size) + AvatarPalette.size) % AvatarPalette.size]
}

/**
 * VAvatar — the single circular-avatar primitive (FEATURE 6).
 *
 * Behaviour:
 *  - **Image present + loads** → circular-cropped image, **crossfaded in over 300ms** so it
 *    never pops. Uses the repo's existing Coil 3 image loader (no new avatar library — RULE-3).
 *  - **Image null or load fails** → a circle filled with a colour derived **deterministically**
 *    from the name (`avatarColorFor` hashes the name into the existing pastel palette — RULE-1,
 *    no new colour tokens) with the user's initial(s) in the heading typography family.
 *  - **Image loading** → a circular [ShimmerBox] (reused from FEATURE 2) so the slot never
 *    flashes empty. The moment the load resolves (success or error) the shimmer crossfades to
 *    the final content.
 *
 * Size variants are exposed as [VAvatarSize] constants (Small 32 / Medium 48 / Large 72) per the
 * spec, but the `size: Dp` parameter remains free-form so the ~24 existing callsites keep working
 * unchanged (RULE-3: extend, don't break).
 *
 * RULE-5 (CMP-safe): `rememberAsyncImagePainter`, `Crossfade`, `Image`, `ShimmerBox` are all
 * commonMain-compatible — no Android-only API.
 */
@Composable
fun VAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = VAvatarSize.Medium,
    src: String? = null,
    ring: Boolean = false,
) {
    val bg = avatarColorFor(name)
    var mod = modifier
        .size(size)
        .clip(CircleShape)
        .background(bg)
    if (ring) {
        mod = mod.border(BorderStroke(3.dp, VTheme.colors.card), CircleShape)
    }

    Box(mod, contentAlignment = Alignment.Center) {
        // The deterministic initial fallback. Always composed underneath the image so that if the
        // image is null, errors, or is mid-load the user still sees a meaningful, stable chip
        // rather than a blank/odd-coloured circle.
        InitialFallback(name = name, size = size)

        if (!src.isNullOrBlank()) {
            val painter = rememberAsyncImagePainter(model = src)
            val painterState by painter.state.collectAsState()

            // Crossfade across the three load phases. Only on success do we draw the bitmap; on
            // loading we show a circular shimmer; on error/empty we fall through to the initial
            // chip already drawn beneath (so we render nothing extra and let it show through).
            Crossfade(
                targetState = painterState,
                animationSpec = tween(durationMillis = 300),
                label = "avatarLoad",
            ) { state ->
                when (state) {
                    is AsyncImagePainter.State.Success ->
                        Image(
                            painter = painter,
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )

                    is AsyncImagePainter.State.Loading ->
                        ShimmerBox(
                            modifier = Modifier.fillMaxSize(),
                            width = size,
                            height = size,
                            shape = CircleShape,
                        )

                    // Error / Empty → render nothing; the InitialFallback below shows through.
                    else -> Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The deterministic colour + initial chip drawn under every avatar. */
@Composable
private fun InitialFallback(name: String, size: Dp) {
    Text(
        text = initialsOf(name),
        style = TextStyle(
            color = Color(0xFF080808),
            fontSize = (size.value * 0.36f).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = VTheme.type.uiFamily,
        ),
    )
}

/**
 * VAvatarSize — the three canonical avatar sizes from the FEATURE 6 spec. Exposed as named
 * constants so callsites can read `VAvatarSize.Small` instead of magic numbers; the underlying
 * type is still [Dp] so they drop straight into the `size` parameter.
 */
object VAvatarSize {
    val Small: Dp = 32.dp
    val Medium: Dp = 48.dp
    val Large: Dp = 72.dp
}
