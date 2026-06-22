package com.littlebridge.enrollplus.ui.v2.screens.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.components.VBrandLogo
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.theme.VMotion
import com.littlebridge.enrollplus.ui.v2.theme.VPortalTone
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import kotlinx.coroutines.launch

/**
 * AuthScaffoldV2 — the shared chrome for the role-scoped auth screens (PHASE 4).
 *
 * The canonical auth silhouette — a teal brand hero carrying the single-source
 * [VBrandLogo], a back chip, the screen [title]/[subtitle], and a rounded form sheet that overlaps
 * the hero — but without the portal selector, so each scoped screen renders only its own role's UI
 * (LAW 3: no role leakage). The caller supplies the role-specific fields + CTA via [content].
 */
@Composable
fun AuthScaffoldV2(
    title: String,
    subtitle: String,
    error: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) = VTheme(tone = VPortalTone.Light) {
    val c = VTheme.colors

    val logoScale = remember { Animatable(0.85f) }
    val logoAlpha = remember { Animatable(0f) }
    val sheetY = remember { Animatable(40f) }
    val sheetAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoScale.animateTo(1f, VMotion.springSoft) }
        launch { logoAlpha.animateTo(1f, tween(400)) }
        launch { sheetY.animateTo(0f, VMotion.springCard) }
        launch { sheetAlpha.animateTo(1f, tween(300)) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ── teal brand hero ──
        Box(
            Modifier.fillMaxWidth().heightIn(min = 300.dp).background(c.teal),
            contentAlignment = Alignment.Center,
        ) {
            // back chip — top-start, above the status bar inset
            val backInteraction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .clickable(interactionSource = backInteraction, indication = null, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.ArrowLeft, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Column(
                Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 44.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VBrandLogo(
                    size = 132.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    title,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.01).em,
                        fontFamily = VTheme.type.uiFamily,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 14.sp,
                        fontFamily = VTheme.type.uiFamily,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }

        // ── form sheet (overlaps hero) ──
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
                .graphicsLayer {
                    translationY = sheetY.value * density
                    alpha = sheetAlpha.value
                }
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(c.background)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp, bottom = 32.dp),
        ) {
            content()

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    error,
                    style = VTheme.type.caption.colored(c.dangerInk),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = TextAlign.Center,
                )
            }

            // Consistent secured footer across every auth surface — quiet, premium trust signal.
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(VIcons.ShieldCheck, contentDescription = null, tint = c.ink3, modifier = Modifier.size(13.dp))
                Text(
                    "Secured with end-to-end encryption",
                    style = VTheme.type.caption.colored(c.ink3).copy(fontSize = 11.sp),
                )
            }
        }
    }
}

/** The "‹ Back" text link used inside the auth form sheet to step back through the VM flow. */
@Composable
fun AuthBackLink(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = VTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Text(
        "‹ Back",
        style = VTheme.type.caption.colored(c.ink3).copy(fontWeight = FontWeight.SemiBold),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
