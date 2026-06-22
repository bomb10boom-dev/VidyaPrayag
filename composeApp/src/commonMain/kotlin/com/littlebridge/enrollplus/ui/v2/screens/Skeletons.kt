package com.littlebridge.enrollplus.ui.v2.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.ui.v2.components.ShimmerBox
import com.littlebridge.enrollplus.ui.v2.components.VCard

/**
 * FEATURE 2 — per-screen skeleton layouts.
 *
 * Each skeleton mirrors the real content's layout with [ShimmerBox] placeholders, so the
 * loading state reads as a faithful silhouette of the loaded screen (matching proportions),
 * and the [VStateHost] crossfade (300ms) lands without a visible jump.
 *
 * Everything here is composed from the frozen [VCard]/[ShimmerBox] primitives only (RULE-1/RULE-3).
 * The skeletons are pure layout — no data, no state — so they can never desync from a ViewModel.
 */

/** Card shapes reused across skeletons so the silhouette matches the real cards' radii. */
private val Pill = RoundedCornerShape(999.dp)
private val LineShape = RoundedCornerShape(5.dp)
private val ChipShape = RoundedCornerShape(10.dp)
private val TileShape = RoundedCornerShape(14.dp)

/** A single shimmer text line at a given width fraction of the row. */
@Composable
private fun SkeletonLine(widthFraction: Float = 1f, height: Int = 14) {
    Row(Modifier.fillMaxWidth()) {
        ShimmerBox(
            modifier = Modifier.fillMaxWidth(widthFraction),
            height = height.dp,
            shape = LineShape,
        )
    }
}

/**
 * SkeletonListRow — an avatar + two text lines, the silhouette of a roster / list item
 * (teacher list, student list, message thread, notification, scholarship row, etc.).
 */
@Composable
fun SkeletonListRow(modifier: Modifier = Modifier, withAvatar: Boolean = true) {
    VCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (withAvatar) ShimmerBox(width = 44.dp, height = 44.dp, shape = CircleShape)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonLine(widthFraction = 0.55f, height = 15)
                SkeletonLine(widthFraction = 0.8f, height = 12)
            }
        }
    }
}

/** SkeletonList — a column of [SkeletonListRow]s; the default loading silhouette for any list tab. */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
    withAvatar: Boolean = true,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) { SkeletonListRow(withAvatar = withAvatar) }
    }
}

/**
 * SkeletonDashboard — a greeting block + a progress hero + a 2-up stat-card grid + a list,
 * the silhouette of the admin / teacher / parent home dashboards.
 */
@Composable
fun SkeletonDashboard(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // greeting
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonLine(widthFraction = 0.4f, height = 13)
            SkeletonLine(widthFraction = 0.65f, height = 22)
        }
        // hero card
        VCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonLine(widthFraction = 0.5f, height = 16)
                ShimmerBox(height = 10.dp, shape = Pill)
                SkeletonLine(widthFraction = 0.7f, height = 12)
            }
        }
        // 2-up stat grid
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                VCard(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShimmerBox(width = 40.dp, height = 40.dp, shape = TileShape)
                        SkeletonLine(widthFraction = 0.7f, height = 18)
                        SkeletonLine(widthFraction = 0.5f, height = 11)
                    }
                }
            }
        }
        // recent list
        repeat(3) { SkeletonListRow() }
    }
}

/**
 * SkeletonProfile — a centred avatar + name + role badge + a stack of detail rows,
 * the silhouette of the parent / teacher profile screens.
 */
@Composable
fun SkeletonProfile(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ShimmerBox(width = 88.dp, height = 88.dp, shape = CircleShape)
            ShimmerBox(width = 160.dp, height = 20.dp, shape = LineShape)
            ShimmerBox(width = 90.dp, height = 22.dp, shape = Pill)
        }
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) {
                VCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonLine(widthFraction = 0.45f, height = 14)
                        SkeletonLine(widthFraction = 0.7f, height = 11)
                    }
                }
            }
        }
    }
}

/**
 * SkeletonCalendar — a month-header line + a chip strip + a list of event rows,
 * the silhouette of the academic calendar screen.
 */
@Composable
fun SkeletonCalendar(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonLine(widthFraction = 0.45f, height = 20)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { ShimmerBox(width = 64.dp, height = 30.dp, shape = ChipShape) }
        }
        repeat(5) { SkeletonListRow(withAvatar = false) }
    }
}

/**
 * SkeletonFee — a summary hero (outstanding / collected) + a couple of breakdown cards + rows,
 * the silhouette of the parent fee screen.
 */
@Composable
fun SkeletonFee(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonLine(widthFraction = 0.4f, height = 13)
                SkeletonLine(widthFraction = 0.6f, height = 26)
                ShimmerBox(height = 10.dp, shape = Pill)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                VCard(Modifier.weight(1f)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonLine(widthFraction = 0.7f, height = 16)
                        SkeletonLine(widthFraction = 0.5f, height = 11)
                    }
                }
            }
        }
        repeat(3) { SkeletonListRow(withAvatar = false) }
    }
}

/**
 * SkeletonAnnouncements — a filter-chip strip + a list of announcement cards,
 * the silhouette of the announcements / communication screen.
 */
@Composable
fun SkeletonAnnouncements(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(4) { ShimmerBox(width = 72.dp, height = 30.dp, shape = Pill) }
        }
        repeat(4) {
            VCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonLine(widthFraction = 0.6f, height = 16)
                    SkeletonLine(widthFraction = 1f, height = 12)
                    SkeletonLine(widthFraction = 0.85f, height = 12)
                }
            }
        }
    }
}
