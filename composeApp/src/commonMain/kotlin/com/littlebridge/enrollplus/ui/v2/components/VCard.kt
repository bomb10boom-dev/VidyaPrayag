package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.theme.VElevationLevel
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.shapeCard
import com.littlebridge.enrollplus.ui.v2.theme.vElevation

/**
 * VCard — the universal elevated surface.
 *
 * Translated from `UI screens/src/app/components/v/primitives.tsx` → `VCard`.
 * Renders the design's `--card` surface with a hairline border and a soft, low-opacity shadow.
 * In Night tone the shadow is suppressed (deep-black surfaces read flat by design).
 */
@Composable
fun VCard(
    modifier: Modifier = Modifier,
    padding: Dp = VTheme.dimens.md,
    shape: RoundedCornerShape = VTheme.dimens.shapeCard,
    background: Color = VTheme.colors.card,
    border: Boolean = true,
    elevated: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = VTheme.colors
    var base = modifier
    if (elevated) {
        base = base.vElevation(VElevationLevel.Card, radius = VTheme.dimens.radiusCard)
    }

    base = base
        .clip(shape)
        .background(background)

    if (border) {
        base = base.border(BorderStroke(1.dp, colors.hairline), shape)
    }

    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        base = base.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    }

    Column(modifier = base.padding(padding), content = content)
}


@Composable
fun VActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors


    VCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(20.dp)
            )
            .clickable(
                onClick = onClick
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                ,

            verticalAlignment =
                Alignment.CenterVertically
        ) {


            // Icon container
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(
                        RoundedCornerShape(14.dp)
                    )
                    .background(
                        c.tealDeep.copy(
                            alpha = 0.12f
                        )
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector = icon,

                    contentDescription = null,

                    tint = c.tealDeep,

                    modifier = Modifier
                        .size(24.dp)
                )
            }



            Spacer(
                Modifier.width(14.dp)
            )



            Column(
                modifier = Modifier.weight(1f),

                verticalArrangement =
                    Arrangement.spacedBy(3.dp)
            ) {

                Text(
                    text = title,

                    style =
                        VTheme.type.bodyStrong
                            .colored(c.ink)
                )


                Text(
                    text = subtitle,

                    style =
                        VTheme.type.caption
                            .colored(c.ink2),

                    maxLines = 2
                )
            }



            Spacer(
                Modifier.width(8.dp)
            )



            // Arrow circle
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(
                        CircleShape
                    )
                    .background(
                        c.card.copy(
                            alpha = 0.8f
                        )
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Icon(
                    imageVector = VIcons.ArrowRight,

                    contentDescription = null,

                    tint = c.ink3,

                    modifier = Modifier
                        .size(18.dp)
                )
            }
        }
    }
}