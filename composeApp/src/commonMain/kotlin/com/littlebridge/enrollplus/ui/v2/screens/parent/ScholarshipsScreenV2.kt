package com.littlebridge.enrollplus.ui.v2.screens.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.parent.presentation.Scholarship
import com.littlebridge.enrollplus.feature.parent.presentation.ScholarshipApplication
import com.littlebridge.enrollplus.feature.parent.presentation.ScholarshipsState
import com.littlebridge.enrollplus.feature.parent.presentation.ScholarshipsViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VProgressBar
import com.littlebridge.enrollplus.ui.v2.screens.VSectionHeader
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * ScholarshipsScreenV2 — parent-side scholarships overlay.
 *
 * Wired to [ScholarshipsViewModel] (`GET /api/v1/parent/scholarships`).
 * Auto-loads on init (the VM has no public refresh hook by design).
 *
 * Layout:
 *   • Profile strength VCard with VProgressBar + streak / level chips
 *   • Available scholarships list — VCards with amount in dataLg,
 *     category VBadge (Full Funding=Success, Merit Based=Arctic,
 *     International=Warning), time-left caption + CRITICAL VBadge for
 *     time-sensitive opportunities.
 *   • Applications list — institution + program + status VBadge
 *     (Shortlisted=Arctic, Under Review=Warning, Received=Success).
 *
 * Three states via [VStateHost] (LAW 3).
 */
@Composable
fun ScholarshipsScreenV2(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ScholarshipsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    Column(modifier.fillMaxSize().statusBarsPadding()
        .imePadding()
        .navigationBarsPadding()) {
        VBackHeader(title = "Scholarships", onBack = onBack)
        ScholarshipsContent(
            state = state,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ScholarshipsContent(
    state: ScholarshipsState,
    modifier: Modifier = Modifier,
) {
    val c = VTheme.colors
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VStateHost(
            loading = state.isLoading,
            error = state.error,
            isEmpty = state.scholarships.isEmpty() &&
                state.applications.isEmpty() &&
                state.profileStrength == 0,
            emptyTitle = "No scholarships yet",
            emptyBody = "Personalised scholarship matches will appear here as your profile grows.",
            emptyIcon = VIcons.Sparkles,
            onRetry = null,
        ) {
            // Profile strength + gamification
            VCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Profile strength", style = VTheme.type.label.colored(c.ink3))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${state.profileStrength}%",
                            style = VTheme.type.dataLg.colored(c.ink),
                        )
                    }
                    VBadge(text = "LVL ${state.currentLevel}", tone = VBadgeTone.Accent)
                }
                Spacer(Modifier.height(10.dp))
                VProgressBar(value = state.profileStrength.toFloat().coerceIn(0f, 100f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        StreakTile(label = "Day streak", value = state.streakDays.toString())
                    }
                    Box(Modifier.weight(1f)) {
                        StreakTile(label = "Current level", value = state.currentLevel.toString())
                    }
                }
            }

            // Available scholarships
            if (state.scholarships.isNotEmpty()) {
                VSectionHeader(title = "AVAILABLE SCHOLARSHIPS")
                state.scholarships.forEach { s -> ScholarshipCard(s) }
            }

            // Applications
            if (state.applications.isNotEmpty()) {
                VSectionHeader(title = "MY APPLICATIONS")
                VCard {
                    state.applications.forEachIndexed { i, a ->
                        if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border1))
                        ApplicationRow(a)
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakTile(label: String, value: String) {
    val c = VTheme.colors
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.cream)
            .padding(12.dp),
    ) {
        Text(label, style = VTheme.type.label.colored(c.ink3))
        Spacer(Modifier.height(4.dp))
        Text(value, style = VTheme.type.dataLg.colored(c.ink))
    }
}

@Composable
private fun ScholarshipCard(s: Scholarship) {
    val c = VTheme.colors
    // Semantic categories: "full funding" is the best outcome → success green; "merit based"
    // is a brand highlight → violet; "international" → amber accent. Distinct + meaningful.
    val categoryTone = when (s.category.lowercase()) {
        "full funding" -> VBadgeTone.Success
        "merit based" -> VBadgeTone.Accent
        "international" -> VBadgeTone.Warning
        else -> VBadgeTone.Neutral
    }
    VCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VBadge(text = s.category, tone = categoryTone)
            if (s.isCritical) {
                VBadge(text = "CRITICAL", tone = VBadgeTone.Danger)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(s.title, style = VTheme.type.h3.colored(c.ink))
        if (s.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(s.description, style = VTheme.type.body.colored(c.ink2))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Award", style = VTheme.type.label.colored(c.ink3))
                Spacer(Modifier.height(2.dp))
                Text(s.amount, style = VTheme.type.dataLg.colored(c.ink))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Closes in", style = VTheme.type.label.colored(c.ink3))
                Spacer(Modifier.height(2.dp))
                Text(s.timeLeft, style = VTheme.type.dataSm.colored(c.ink2))
            }
        }
    }
}

@Composable
private fun ApplicationRow(a: ScholarshipApplication) {
    val c = VTheme.colors
    // Semantic application states: "received" + "shortlisted" are positive progress → success
    // green (shortlisted the deeper read), "under review" is in-flight → amber.
    val statusTone = when (a.status.lowercase()) {
        "received" -> VBadgeTone.Success
        "shortlisted" -> VBadgeTone.Success
        "under review" -> VBadgeTone.Warning
        else -> VBadgeTone.Neutral
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.cream),
            contentAlignment = Alignment.Center,
        ) {
            Icon(VIcons.GraduationCap, contentDescription = null, tint = c.ink2, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(a.institution, style = VTheme.type.bodyStrong.colored(c.ink))
            Spacer(Modifier.height(2.dp))
            Text(a.program, style = VTheme.type.caption.colored(c.ink3))
        }
        VBadge(text = a.status, tone = statusTone)
    }
}
