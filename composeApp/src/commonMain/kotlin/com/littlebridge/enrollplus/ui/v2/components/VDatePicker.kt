package com.littlebridge.enrollplus.ui.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.littlebridge.enrollplus.ui.v2.theme.VTheme
import com.littlebridge.enrollplus.ui.v2.theme.colored
import com.littlebridge.enrollplus.ui.v2.theme.shapeInput
import com.littlebridge.enrollplus.util.MONTH_LONG
import com.littlebridge.enrollplus.util.WEEKDAY_SHORT
import com.littlebridge.enrollplus.util.dayOfWeek
import com.littlebridge.enrollplus.util.daysInMonth
import com.littlebridge.enrollplus.util.isoOf
import com.littlebridge.enrollplus.util.parseIsoDate
import com.littlebridge.enrollplus.util.todayIso

/**
 * VDatePicker — a read-only field that opens a calendar dialog instead of
 * letting the user type a date. This is the app-wide standard for ALL date
 * inputs: typing dates by hand (e.g. "YYYY-MM-DD") is error-prone, so every
 * date is now chosen from a real month grid.
 *
 * [value] / [onValueChange] use ISO "YYYY-MM-DD" strings to match the rest of
 * the app (calendars, leave, PTM, announcements all speak ISO on the wire).
 * The field shows a friendly "13 Jun 2026" label while keeping the ISO value
 * as the source of truth.
 *
 * No new dependency: the calendar math lives in shared/util/DateUtil.kt and is
 * the same Sakamoto-based logic the existing read-only calendars already use.
 */
@Composable
fun VDatePicker(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "Select date",
    enabled: Boolean = true,
    isError: Boolean = false,
) {
    val c = VTheme.colors
    var open by remember { mutableStateOf(false) }

    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = VTheme.type.inputLabel.colored(c.ink2),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(VTheme.dimens.shapeInput)
                .background(c.cream)
                .border(1.dp, if (isError) c.danger else c.hairline, VTheme.dimens.shapeInput)
                .clickable(enabled = enabled) { open = true }
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    VIcons.Calendar,
                    contentDescription = null,
                    tint = if (enabled) c.tealDeep else c.ink3,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = value.takeIf { it.isNotBlank() }?.let(::prettyDate) ?: placeholder,
                    style = if (value.isBlank()) VTheme.type.body.colored(c.ink3)
                    else VTheme.type.body.colored(c.ink),
                )
            }
        }
    }

    if (open) {
        DatePickerDialog(
            initialIso = value.takeIf { it.isNotBlank() } ?: todayIso(),
            onDismiss = { open = false },
            onPick = { iso -> onValueChange(iso); open = false },
        )
    }
}

@Composable
private fun DatePickerDialog(
    initialIso: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val c = VTheme.colors
    val start = parseIsoDate(initialIso) ?: parseIsoDate(todayIso())!!
    var viewYear by remember { mutableStateOf(start.first) }
    var viewMonth by remember { mutableStateOf(start.second) } // 1..12
    var selectedIso by remember { mutableStateOf(isoOf(start.first, start.second, start.third)) }

    Dialog(onDismissRequest = onDismiss) {
        VCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Month header with prev / next nav
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(VTheme.dimens.shapeInput)
                            .clickable {
                                if (viewMonth == 1) { viewMonth = 12; viewYear-- } else viewMonth--
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(VIcons.ChevronLeft, contentDescription = "Previous month", tint = c.ink, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        "${MONTH_LONG[viewMonth - 1]} $viewYear",
                        style = VTheme.type.h3.colored(c.ink),
                    )
                    Box(
                        Modifier.size(34.dp).clip(VTheme.dimens.shapeInput)
                            .clickable {
                                if (viewMonth == 12) { viewMonth = 1; viewYear++ } else viewMonth++
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(VIcons.ChevronRight, contentDescription = "Next month", tint = c.ink, modifier = Modifier.size(22.dp))
                    }
                }

                // Weekday header
                Row(Modifier.fillMaxWidth()) {
                    WEEKDAY_SHORT.forEach { d ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(d, style = VTheme.type.label.colored(c.ink3))
                        }
                    }
                }

                // Day grid
                val firstDow = dayOfWeek(viewYear, viewMonth, 1) // 0=Sun..6=Sat
                val total = daysInMonth(viewYear, viewMonth)
                val cells = firstDow + total
                val rows = (cells + 6) / 7
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until rows) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                val day = cellIndex - firstDow + 1
                                Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                    if (day in 1..total) {
                                        val iso = isoOf(viewYear, viewMonth, day)
                                        val isSelected = iso == selectedIso
                                        val isToday = iso == todayIso()
                                        Box(
                                            Modifier
                                                .size(38.dp)
                                                .clip(VTheme.dimens.shapeInput)
                                                .background(if (isSelected) c.tealDeep else c.card)
                                                .border(
                                                    if (isToday && !isSelected) 1.dp else 0.dp,
                                                    if (isToday && !isSelected) c.tealDeep else c.card,
                                                    VTheme.dimens.shapeInput,
                                                )
                                                .clickable { selectedIso = iso },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                day.toString(),
                                                style = VTheme.type.body.colored(if (isSelected) c.card else c.ink),
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                VButton(
                    text = "Select",
                    onClick = { onPick(selectedIso) },
                    variant = VButtonVariant.Primary,
                    full = true,
                )
                VButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    variant = VButtonVariant.Ghost,
                    full = true,
                )
            }
        }
    }
}

private val MONTH_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "2026-06-13" -> "13 Jun 2026". Falls back to the raw value if unparseable. */
private fun prettyDate(iso: String): String {
    val (y, m, d) = parseIsoDate(iso) ?: return iso
    return "$d ${MONTH_SHORT[m - 1]} $y"
}
