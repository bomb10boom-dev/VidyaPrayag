package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored

// ─────────────────────────────────────────────────────────────────────────────
// VTopTabs — horizontally scrollable underline tab bar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * VTopTabs — a row of text tabs with a teal underline indicator under the active tab.
 * Translated from primitives.tsx → `VTopTabs`.
 */
@Composable
fun VTopTabs(
    tabs: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color? = VTheme.colors.tealDeep
) {
    val c = VTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = c.card,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = 12.dp,
                    vertical = 8.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            tabs.forEach { tab ->

                val active = tab == selected

                val bgColor by animateColorAsState(
                    targetValue = if (active)
                        activeColor?:c.tealDeep
                    else
                        Color.Transparent,
                    animationSpec = tween(220),
                    label = "tabBackground"
                )

                val textColor by animateColorAsState(
                    targetValue = if (active)
                        Color.White
                    else
                        c.ink3,
                    animationSpec = tween(220),
                    label = "tabText"
                )

                val scale by animateFloatAsState(
                    targetValue = if (active) 1f else 0.98f,
                    animationSpec = tween(220),
                    label = "tabScale"
                )


                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(
                            RoundedCornerShape(50)
                        )
                        .background(bgColor)
                        .clickable(
                            indication = null,
                            interactionSource = remember {
                                MutableInteractionSource()
                            }
                        ) {
                            onSelect(tab)
                        }
                        .padding(
                            horizontal = 18.dp,
                            vertical = 10.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = tab,
                        style = VTheme.type.dataSm.copy(
                            fontFamily = VTheme.type.uiFamily,
                            fontWeight = if (active)
                                FontWeight.Bold
                            else
                                FontWeight.SemiBold,
                            color = textColor
                        )
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VBottomNav — sticky bottom navigation with optional per-item badge
// ─────────────────────────────────────────────────────────────────────────────

/** One destination in [VBottomNav]. */
data class VNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int = 0,
)

/**
 * VBottomNav — the five-or-fewer tab bar pinned to the screen bottom.
 * Translated from primitives.tsx → `VBottomNav`. original
 */
@Composable
fun VBottomNav2(
    items: List<VNavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    // RA-PP-THEME: the active-state accent. Defaults to the legacy primary
    // (tealDeep) so every other portal is untouched, but the Parents Portal —
    // the first to migrate to the website's lavender/navy/violet palette —
    // passes `accentDeep` (the violet #544AB8) so the active tab/pill matches
    // the reference dashboard instead of the old green.
    activeColor: Color? = null,
) {
    val c = VTheme.colors
    val active0 = activeColor ?: c.tealDeep
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    // §14/§13.1 — the design floats the bar with a soft UPWARD navy-tinted
    // shadow (`0 -4px 20px navy@4%`), not the default downward Material shadow.
    // Modifier.shadow can only cast downward + can't tint, so we draw a short
    // navy gradient that fades upward just above the bar's top edge.
    val topShadow = if (c.isNight) {
        Modifier
    } else {
        Modifier.drawBehind {
            val h = 20.dp.toPx()
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, c.shadowTint.copy(alpha = 0.04f)),
                    startY = -h,
                    endY = 0f,
                ),
                topLeft = Offset(0f, -h),
                size = androidx.compose.ui.geometry.Size(size.width, h),
            )
        }
    }

    val itemXs = remember { mutableStateMapOf<String, Dp>() }
    val itemWidths = remember { mutableStateMapOf<String, Dp>() }
    val targetX = itemXs[selected] ?: 0.dp
    val targetWidth = itemWidths[selected] ?: 0.dp
    val pillX by animateDpAsState(
        targetValue = targetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navPillX",
    )
    val pillWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "navPillWidth",
    )

    Column(
        modifier
            .fillMaxWidth()
            .then(topShadow)
            .background(c.card),
    ) {
        VDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            if (pillWidth > 0.dp) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = pillX)
                        .width(pillWidth)
                        .height(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        // tint of the active accent (portal-configurable)
                        .background(active0.copy(alpha = if (c.isNight) 0.18f else 0.10f)),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val active = item.id == selected
                    val tint = if (active) active0 else c.ink3
                    val interaction = remember { MutableInteractionSource() }
                    // Selected icon scales to 1.1f, unselected to 1.0f, with a soft spring.
                    val iconScale by animateFloatAsState(
                        targetValue = if (active) 1.1f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "navIconScale",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .onGloballyPositioned { coords ->
                                itemXs[item.id] = with(density) { coords.boundsInParent().left.toDp() }
                                itemWidths[item.id] = with(density) { coords.size.width.toDp() }
                            }
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(interactionSource = interaction, indication = null) {
                                if (!active) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onSelect(item.id)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Box {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                tint = tint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            if (item.badge > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(start = 10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFC14A44))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                ) {
                                    Text(
                                        text = if (item.badge > 99) "99+" else item.badge.toString(),
                                        style = VTheme.type.dataSm.colored(Color.White).copy(fontSize = 9.sp),
                                    )
                                }
                            }
                        }
                        Text(
                            text = item.label,
                            style = VTheme.type.label.colored(tint).copy(
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                letterSpacing = TextUnit.Unspecified,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/*Telegrams*/
@Composable
fun VBottomNav(
    items: List<VNavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val itemPositions = remember {
        mutableStateMapOf<String, Dp>()
    }

    val itemWidths = remember {
        mutableStateMapOf<String, Dp>()
    }

    val pillX by animateDpAsState(
        targetValue = itemPositions[selected] ?: 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pillX"
    )

    val pillWidth by animateDpAsState(
        targetValue = itemWidths[selected] ?: 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pillWidth"
    )


    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = 18.dp,
                vertical = 14.dp
            ),
        contentAlignment = Alignment.Center
    ) {

        Box(
            modifier = Modifier
                .height(68.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(34.dp),
                    clip = false
                )
                .clip(
                    RoundedCornerShape(34.dp)
                )
                .background(
                    c.card.copy(
                        alpha = if (c.isNight) 0.96f else 0.98f
                    )
                )
                .padding(horizontal = 8.dp),
        ) {

            // Telegram style active bubble
            if (pillWidth > 0.dp) {

                Box(
                    modifier = Modifier
                        .offset(
                            x = pillX
                        )
                        .width(
                            pillWidth
                        )
                        .height(52.dp)
                        .align(
                            Alignment.CenterStart
                        )
                        .clip(
                            RoundedCornerShape(26.dp)
                        )
                        .background(
                            c.tealDeep.copy(
                                alpha = if (c.isNight)
                                    0.22f
                                else
                                    0.12f
                            )
                        )
                )
            }


            Row(
                modifier = Modifier
                    .fillMaxSize(),

                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {

                items.forEach { item ->

                    val active =
                        item.id == selected


                    val scale by animateFloatAsState(
                        targetValue =
                            if (active) 1.15f else 1f,

                        animationSpec =
                            spring(
                                dampingRatio =
                                    Spring.DampingRatioMediumBouncy
                            ),

                        label = "iconScale"
                    )


                    val tint =
                        if (active)
                            c.tealDeep
                        else
                            c.ink3


                    val interaction =
                        remember {
                            MutableInteractionSource()
                        }


                    Column(

                        modifier =
                            Modifier
                                .onGloballyPositioned {

                                    itemPositions[item.id] =
                                        with(density) {
                                            it.boundsInParent()
                                                .left
                                                .toDp()
                                        }

                                    itemWidths[item.id] =
                                        with(density) {
                                            it.size.width
                                                .toDp()
                                        }
                                }

                                .clip(
                                    RoundedCornerShape(26.dp)
                                )

                                .clickable(
                                    interactionSource =
                                        interaction,
                                    indication = null
                                ) {

                                    if (!active) {
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }

                                    onSelect(item.id)
                                }

                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 8.dp
                                ),

                        horizontalAlignment =
                            Alignment.CenterHorizontally,

                        verticalArrangement =
                            Arrangement.Center

                    ) {


                        Box {

                            Icon(
                                imageVector = item.icon,
                                contentDescription =
                                    item.label,

                                tint = tint,

                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                            )


                            if (item.badge > 0) {

                                Box(
                                    modifier =
                                        Modifier
                                            .align(
                                                Alignment.TopEnd
                                            )
                                            .offset(
                                                x = 8.dp,
                                                y = (-4).dp
                                            )
                                            .clip(
                                                CircleShape
                                            )
                                            .background(
                                                Color(0xFFC14A44)
                                            )
                                            .padding(
                                                horizontal = 5.dp,
                                                vertical = 1.dp
                                            )
                                ) {

                                    Text(
                                        text =
                                            if(item.badge > 99)
                                                "99+"
                                            else
                                                item.badge.toString(),

                                        style =
                                            VTheme.type.dataSm.colored(
                                                Color.White
                                            ).copy(
                                                fontSize = 9.sp
                                            )
                                    )
                                }
                            }
                        }


                        AnimatedVisibility(
                            visible = active
                        ) {

                            Text(
                                text = item.label,

                                style =
                                    VTheme.type.label.colored(
                                        tint
                                    ).copy(
                                        fontSize = 10.sp,
                                        fontWeight =
                                            FontWeight.Bold,
                                        letterSpacing =
                                            TextUnit.Unspecified
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VBottomNav4(
    items: List<VNavItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    val haptic = LocalHapticFeedback.current


    val selectedIndex = remember(selected, items) {
        items.indexOfFirst { it.id == selected }
            .coerceAtLeast(0)
    }


    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "navPillPosition"
    )


    Column(
        modifier = modifier
            .fillMaxWidth()
    ) {


        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(
                    RoundedCornerShape(
                        topStart = 28.dp,
                        topEnd = 28.dp
                    )
                )
                .background(c.lavenderLight)
                .drawBehind {

                    // soft upward shadow
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                c.shadowTint.copy(
                                    alpha = 0.08f
                                ),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = 18.dp.toPx()
                        )
                    )
                }
                .padding(
                    top = 10.dp,
                    bottom = 8.dp,
                    start = 8.dp,
                    end = 8.dp
                )
                .navigationBarsPadding()

        ) {


            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {


                val itemWidth =
                    maxWidth / items.size


                // Animated selected background pill
                Box(
                    modifier = Modifier
                        .offset(
                            x = itemWidth * animatedIndex
                        )
                        .width(itemWidth)
                        .height(44.dp)
                        .align(
                            Alignment.CenterStart
                        )
                        .clip(
                            RoundedCornerShape(
                                24.dp
                            )
                        )
                        .background(
                            c.tealDeep.copy(
                                alpha =
                                    if (c.isNight)
                                        0.20f
                                    else
                                        0.12f
                            )
                        )
                )


                Row(
                    modifier = Modifier.fillMaxSize(),

                    horizontalArrangement =
                        Arrangement.Center,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {


                    items.forEach { item ->


                        val active =
                            item.id == selected


                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),

                            contentAlignment =
                                Alignment.Center
                        ) {


                            VBottomBarItem(
                                item = item,
                                active = active,
                                onClick = {

                                    if (!active) {
                                        haptic.performHapticFeedback(
                                            HapticFeedbackType.TextHandleMove
                                        )
                                    }

                                    onSelect(item.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun VBottomBarItem(
    item: VNavItem,
    active: Boolean,
    onClick: () -> Unit,
) {
    val c = VTheme.colors

    val tint =
        if (active) c.tealDeep else c.ink3


    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconScale"
    )


    val labelAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(150),
        label = "labelAlpha"
    )


    // icon moves down when label disappears
    val iconOffsetY by animateDpAsState(
        targetValue =
            if (active)
                (-6).dp
            else
                7.dp,

        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),

        label = "iconOffset"
    )


    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                indication = null,
                interactionSource = remember {
                    MutableInteractionSource()
                }
            ) {
                onClick()
            }
            .padding(
                horizontal = 12.dp,
                vertical = 6.dp
            ),

        horizontalAlignment = Alignment.CenterHorizontally,

        verticalArrangement = Arrangement.Center
    ) {


        Box(
            modifier = Modifier
                .height(42.dp),
            contentAlignment = Alignment.Center
        ) {


            Icon(
                imageVector = item.icon,

                contentDescription = item.label,

                tint = tint,

                modifier = Modifier
                    .size(23.dp)
                    .offset(y = iconOffsetY)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )


            Text(
                text = item.label,

                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .alpha(labelAlpha),

                style =
                    VTheme.type.label
                        .colored(tint)
                        .copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = TextUnit.Unspecified
                        )
            )
        }


        // badge stays independent
    }
}
// ─────────────────────────────────────────────────────────────────────────────
// VBackHeader — a back chevron, centered title, and optional trailing action
// ─────────────────────────────────────────────────────────────────────────────

/**
 * VBackHeader — top app bar with a circular back button, centered title, trailing slot.
 * Translated from primitives.tsx → `VBackHeader`.
 */
@Composable
fun VBackHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val c = VTheme.colors
    Column(modifier.fillMaxWidth().background(c.card)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // back button
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(c.cream)
                    .clickable(interactionSource = interaction, indication = null, enabled = onBack != null) { onBack?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(VIcons.ChevronLeft, contentDescription = "Back", tint = c.ink, modifier = Modifier.size(20.dp))
            }
            Text(title, style = VTheme.type.h3.colored(c.ink))
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                action?.invoke()
            }
        }
        VDivider()
    }
}
