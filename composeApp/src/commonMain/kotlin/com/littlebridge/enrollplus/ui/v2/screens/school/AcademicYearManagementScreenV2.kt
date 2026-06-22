package com.littlebridge.enrollplus.ui.v2.screens.school

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.littlebridge.enrollplus.feature.admin.domain.model.AcademicYearDto
import com.littlebridge.enrollplus.feature.admin.presentation.AcademicYearViewModel
import com.littlebridge.enrollplus.ui.v2.components.VBackHeader
import com.littlebridge.enrollplus.ui.v2.components.VBadge
import com.littlebridge.enrollplus.ui.v2.components.VBadgeTone
import com.littlebridge.enrollplus.ui.v2.components.VButton
import com.littlebridge.enrollplus.ui.v2.components.VButtonSize
import com.littlebridge.enrollplus.ui.v2.components.VButtonTone
import com.littlebridge.enrollplus.ui.v2.components.VButtonVariant
import com.littlebridge.enrollplus.ui.v2.components.VCard
import com.littlebridge.enrollplus.ui.v2.components.VDatePicker
import com.littlebridge.enrollplus.ui.v2.components.VIcons
import com.littlebridge.enrollplus.ui.v2.components.VInput
import com.littlebridge.enrollplus.ui.v2.components.VLabel
import com.littlebridge.enrollplus.ui.v2.screens.VStateHost
import com.littlebridge.enrollplus.ui.v2.screens.collectAsStateV2
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import org.koin.compose.viewmodel.koinViewModel

/**
 * AcademicYearManagementScreenV2 — the REAL replacement for the Settings
 * "Academic Year (Coming Soon)" stub. Lets a school admin:
 *   - Create a new academic year (optionally activating it)
 *   - Activate / Archive existing years (exactly one active at a time)
 *   - View historical (archived) years
 */
@Composable
fun AcademicYearManagementScreenV2(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AcademicYearViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateV2()
    val c = VTheme.colors

    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var end by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding().navigationBarsPadding()) {
        VBackHeader(
            title = "Academic Year",
            onBack = onBack,
            action = {
                VButton(
                    text = if (showCreate) "Close" else "New",
                    onClick = { showCreate = !showCreate },
                    size = VButtonSize.Sm,
                    tone = VButtonTone.Teal,
                )
            },
        )

        VStateHost(
            loading = state.isLoading,
            error = state.errorMessage,
            isEmpty = state.isEmpty && !showCreate,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            emptyTitle = "No academic years yet",
            emptyBody = "Create your first academic year to anchor the calendar.",
            emptyIcon = VIcons.Calendar,
            onRetry = { viewModel.load() },
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showCreate) {
                    VLabel("Create academic year")
                    VCard {
                        VInput(value = name, onValueChange = { name = it }, label = "Name", placeholder = "e.g. 2026-27")
                        Spacer(Modifier.height(10.dp))
                        VDatePicker(value = start, onValueChange = { start = it }, label = "Start date")
                        Spacer(Modifier.height(10.dp))
                        VDatePicker(value = end, onValueChange = { end = it }, label = "End date")
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VButton(
                                text = "Save Draft",
                                onClick = {
                                    viewModel.createYear(name, start, end, activate = false) {
                                        showCreate = false; name = ""; start = ""; end = ""
                                    }
                                },
                                variant = VButtonVariant.Secondary,
                                tone = VButtonTone.Navy,
                                loading = state.isMutating,
                                modifier = Modifier.weight(1f),
                            )
                            VButton(
                                text = "Create & Activate",
                                onClick = {
                                    viewModel.createYear(name, start, end, activate = true) {
                                        showCreate = false; name = ""; start = ""; end = ""
                                    }
                                },
                                tone = VButtonTone.Teal,
                                loading = state.isMutating,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                state.activeYear?.let { active ->
                    VLabel("Active year")
                    YearCard(active, isActive = true, onActivate = {}, onArchive = { viewModel.archive(active.id) })
                }

                val historical = state.historicalYears
                if (historical.isNotEmpty()) {
                    VLabel("Historical & drafts")
                    historical.forEach { y ->
                        YearCard(
                            y,
                            isActive = false,
                            onActivate = { viewModel.activate(y.id) },
                            onArchive = { viewModel.archive(y.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearCard(
    year: AcademicYearDto,
    isActive: Boolean,
    onActivate: () -> Unit,
    onArchive: () -> Unit,
) {
    val c = VTheme.colors
    VCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(year.name, style = VTheme.type.h3.colored(c.ink))
                Text("${year.startDate} → ${year.endDate}", style = VTheme.type.caption.colored(c.ink2))
            }
            VBadge(
                text = year.status,
                tone = when (year.status.uppercase()) {
                    "ACTIVE" -> VBadgeTone.Success
                    "ARCHIVED" -> VBadgeTone.Neutral
                    else -> VBadgeTone.Warning
                },
            )
        }
        if (year.academicDays != null || year.holidayDays != null) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                year.academicDays?.let { Text("$it school days", style = VTheme.type.caption.colored(c.ink3)) }
                year.holidayDays?.let { Text("$it holidays", style = VTheme.type.caption.colored(c.ink3)) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isActive) {
                VButton(
                    text = "Activate",
                    onClick = onActivate,
                    size = VButtonSize.Sm,
                    tone = VButtonTone.Teal,
                )
            }
            VButton(
                text = "Archive",
                onClick = onArchive,
                size = VButtonSize.Sm,
                variant = VButtonVariant.Secondary,
                tone = VButtonTone.Navy,
            )
        }
    }
}
