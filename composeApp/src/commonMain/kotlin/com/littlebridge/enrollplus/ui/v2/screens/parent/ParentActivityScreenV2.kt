package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.parent.presentation.ParentAnnouncement
import com.littlebridge.enrollplus.feature.parent.presentation.ParentAnnouncementState
import com.littlebridge.enrollplus.feature.parent.presentation.ParentAnnouncementViewModel
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VTag
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * ParentActivityScreenV2 — a pixel-faithful copy of `Parent.tsx → Activity`.
 *
 * Category filter chips + the school announcement feed. **Wired to the real
 * [ParentAnnouncementViewModel]** (`shared/`) → `ParentRepository.getAnnouncements` →
 * `GET /api/v1/parent/announcements`. MockV2 is no longer referenced; the three UI states
 * (loading / error / empty) are handled by [VStateHost].
 */
@Composable
fun ParentActivityScreenV2(
    modifier: Modifier = Modifier,
    viewModel: ParentAnnouncementViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    ParentActivityContent(state = state, modifier = modifier)
}

/** Stateless body — also used by @Preview with seeded state (no MockV2 in the live path). */
@Composable
private fun ParentActivityContent(
    state: ParentAnnouncementState,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    var filter by remember { mutableStateOf("All") }

    // Build the filter set from the real announcement categories + the "All" pseudo-filter.
    val categories = state.announcements.map { it.category }.distinct()
    val filters = listOf("All") + categories
    val filtered = if (filter == "All") {
        state.announcements
    } else {
        state.announcements.filter { it.category.equals(filter, ignoreCase = true) }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 140.dp),
    ) {
        Text("Activity", style = VTheme.type.h1.colored(c.ink), modifier = Modifier.padding(bottom = 12.dp))

        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.announcements.isEmpty(),
            emptyTitle = "All caught up",
            emptyBody = "New announcements from your school will show up here.",
            emptyIcon = VIcons.Bell,
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEach { f ->
                    VTag(text = f, active = filter == f, onClick = { filter = f }, accentActive = true)
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEach { a -> AnnouncementCard(a) }
            }
        }
    }
}

@Composable
private fun AnnouncementCard(a: ParentAnnouncement) {
    val c = VTheme.colors
    // Each category carries a semantic colour AND a matching icon tint (was a flat grey icon on a
    // tinted chip, which read cheap). The tint pairs with the chip so the glyph belongs to its hue.
    val (tint, icon) = when (a.category.lowercase()) {
        "holidays", "holiday" -> c.accentDeep to VIcons.Calendar      // brand violet — a treat day
        "ptm" -> c.warningInk to VIcons.Users                          // amber — needs your time
        "events", "event" -> Color(0xFF6C8DF5) to VIcons.Star          // sky — something happening
        "reminder" -> c.dangerInk to VIcons.Clock                      // red — don't miss it
        else -> c.tealDeep to VIcons.Bell                              // teal — general notice
    }
    VCard(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(a.title, style = VTheme.type.bodyStrong.colored(c.ink))
                Text(a.description, style = VTheme.type.caption.colored(c.ink2))
                Text(a.date, style = VTheme.type.label.colored(c.ink3), modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
