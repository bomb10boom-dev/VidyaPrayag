package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.components.VBrandLogo
import com.littlebridge.enrollplus.ui.v2.theme.VMotion
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import kotlinx.coroutines.launch

/**
 * SplashScreenV2 — the app's first frame (PHASE 2).
 *
 * A full-bleed teal hero carrying the single-source [VBrandLogo] bridge mark with a soft radial glow
 * and a pulsing halo, the "VidyaSetu" wordmark and tagline. The logo springs in immediately while the
 * **session check runs in parallel** (the host reads JWT + role from preferences). The instant the
 * session resolves, the host swaps Splash for the correct destination — there is no artificial hold
 * timer and no blank intermediate frame (LAW 5: session check is instant).
 *
 * This screen is purely presentational: it shows the brand and an indeterminate progress hint while
 * `isLoaded` is false. The host ([com.littlebridge.enrollplus.App]) owns the actual navigation
 * decision so Splash never needs to know about roles or destinations.
 */
@Composable
fun SplashScreenV2(modifier: Modifier = Modifier) = VTheme(tone = VPortalTone.Light) {
    val c = VTheme.colors

    // Entrance reveal — logo spring fires at once, wordmark/tagline follow shortly after.
    val logoScale = remember { Animatable(0.82f) }
    val logoAlpha = remember { Animatable(0f) }
    val logoY = remember { Animatable(10f) }
    val wordAlpha = remember { Animatable(0f) }
    val wordY = remember { Animatable(12f) }
    val taglineAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, VMotion.springSoft) }
        launch { logoAlpha.animateTo(1f, tween(380)) }
        launch { logoY.animateTo(0f, VMotion.springSoft) }
        launch { wordAlpha.animateTo(1f, tween(420, delayMillis = 260)) }
        launch { wordY.animateTo(0f, tween(420, delayMillis = 260)) }
        launch { taglineAlpha.animateTo(1f, tween(400, delayMillis = 380)) }
    }

    // Halo pulse — opacity [0,.6,0] over 2.4s, ∞.
    val halo = rememberInfiniteTransition(label = "splash-halo")
    val haloAlpha by halo.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 2400
                0f at 0
                0.6f at 1200
                0f at 2400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "splash-haloAlpha",
    )

    // Indeterminate "breathing" dots row to signal the parallel session check is in flight.
    val pulse = rememberInfiniteTransition(label = "splash-pulse")
    val dot by pulse.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "splash-dot",
    )

    Box(
        modifier
            .fillMaxSize()
            .background(c.teal)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.40f),
                        radius = size.maxDimension * 0.55f,
                    ),
                    radius = size.maxDimension * 0.55f,
                    center = Offset(size.width * 0.5f, size.height * 0.40f),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Two faint white cloud marks — top-start + bottom-end — exactly as the
        // brand reference (pixel-faithful to the splash design).
        SplashCloudMark(
            Modifier.align(Alignment.TopStart)
                .padding(start = 40.dp, top = 64.dp).size(width = 56.dp, height = 38.dp),
            alpha = 0.30f,
        )
        SplashCloudMark(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 120.dp).size(width = 46.dp, height = 32.dp),
            alpha = 0.25f,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            VBrandLogo(
                size = 152.dp,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        translationY = logoY.value * density
                        alpha = logoAlpha.value
                    }
                    .drawBehind {
                        // Soft halo ring just outside the cube (peaks at 0.10 alpha).
                        drawRoundRect(
                            color = Color.White.copy(alpha = (haloAlpha / 0.6f).coerceIn(0f, 1f) * 0.10f),
                            topLeft = Offset(-12.dp.toPx(), -12.dp.toPx()),
                            size = Size(size.width + 24.dp.toPx(), size.height + 24.dp.toPx()),
                            cornerRadius = CornerRadius(40.dp.toPx(), 40.dp.toPx()),
                            style = Stroke(width = 12.dp.toPx()),
                        )
                    },
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "VidyaSetu",
                style = VTheme.type.h1.colored(Color.White)
                    .copy(fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = wordAlpha.value
                    translationY = wordY.value * density
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Bridging gaps for a glorious future",
                style = VTheme.type.body.colored(Color.White.copy(alpha = 0.92f)).copy(fontSize = 14.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = taglineAlpha.value },
            )
        }

        // Bottom progress hint — three breathing dots while the session resolves.
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp)
                .graphicsLayer { alpha = taglineAlpha.value },
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) { i ->
                val a = (dot + i * 0.18f).coerceIn(0.3f, 1f)
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = a }
                        .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}

/**
 * SplashCloudMark — a single faint stroked cloud for the teal hero, byte-identical to the
 * landing/Welcome cloud (SVG `M6 24 Q12 14 22 18 Q28 8 38 14 Q46 14 44 24 Z`) so every brand
 * surface matches the reference exactly.
 */
@Composable
private fun SplashCloudMark(modifier: Modifier, alpha: Float) {
    Canvas(modifier) {
        val sx = size.width / 48f
        val sy = size.height / 32f
        fun x(v: Float) = v * sx
        fun y(v: Float) = v * sy
        val p = Path().apply {
            moveTo(x(6f), y(24f))
            quadraticTo(x(12f), y(14f), x(22f), y(18f))
            quadraticTo(x(28f), y(8f), x(38f), y(14f))
            quadraticTo(x(46f), y(14f), x(44f), y(24f))
            close()
        }
        drawPath(p, color = Color.White.copy(alpha = alpha), style = Stroke(width = 1.5f * sx))
    }
}
