/*
 * File: DateUtil.kt
 * Module: util
 *
 * Tiny cross-platform date helpers used by the VDatePicker calendar component
 * and any screen that needs "today" without pulling in kotlinx-datetime.
 *
 * All dates in this app are ISO "YYYY-MM-DD" strings on the wire, so these
 * helpers stay string-first to match the existing calendar grids
 * (AcademicCalendarScreenV2 / ParentAttendanceCalendar).
 */
package com.littlebridge.enrollplus.util

/** Today's local date as an ISO "YYYY-MM-DD" string. Platform-provided. */
expect fun todayIso(): String

/**
 * Current local wall-clock time as minutes-since-midnight (0..1439). Platform-provided.
 *
 * Used by the parent dashboard's "today's schedule" card to highlight the period the
 * child is in *right now* and dim the periods already finished — a genuinely live read
 * that re-evaluates as the school day progresses (no fabricated "current class").
 */
expect fun nowMinutesOfDay(): Int

/**
 * Today's ISO weekday: 1=Monday … 7=Sunday — matches `teacher_periods.weekday` and
 * `java.time.DayOfWeek.value`, so the weekly-timetable read lines up day-for-day.
 */
fun todayWeekday(): Int {
    val (y, m, d) = parseIsoDate(todayIso()) ?: return 1
    // dayOfWeek() returns 0=Sun..6=Sat; remap to 1=Mon..7=Sun.
    return when (val dow = dayOfWeek(y, m, d)) {
        0 -> 7          // Sunday
        else -> dow     // Mon..Sat → 1..6
    }
}

/** Parses "HH:mm" (24h) into minutes-since-midnight (0..1439); null on bad input. */
fun parseHourMinute(hm: String): Int? {
    val parts = hm.trim().split(":")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val mi = parts[1].take(2).toIntOrNull() ?: return null
    if (h !in 0..23 || mi !in 0..59) return null
    return h * 60 + mi
}

/** Formats minutes-since-midnight into a friendly 12h clock, e.g. 525 → "8:45 AM". */
fun formatClock12h(minutesOfDay: Int): String {
    val m = ((minutesOfDay % 1440) + 1440) % 1440
    val h24 = m / 60
    val mi = m % 60
    val period = if (h24 < 12) "AM" else "PM"
    val h12 = when (val h = h24 % 12) { 0 -> 12; else -> h }
    return "$h12:${mi.toString().padStart(2, '0')} $period"
}

/** Parses "YYYY-MM-DD" into Triple(year, month, day); null on bad input. */
fun parseIsoDate(iso: String): Triple<Int, Int, Int>? {
    if (iso.length < 10) return null
    val y = iso.substring(0, 4).toIntOrNull() ?: return null
    val m = iso.substring(5, 7).toIntOrNull() ?: return null
    val d = iso.substring(8, 10).toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    return Triple(y, m, d)
}

/** Formats y/m/d into an ISO "YYYY-MM-DD" string (zero-padded). */
fun isoOf(year: Int, month: Int, day: Int): String {
    val mm = month.toString().padStart(2, '0')
    val dd = day.toString().padStart(2, '0')
    return "$year-$mm-$dd"
}

fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

/**
 * Day of week for a given date using Sakamoto's algorithm.
 * Returns 0=Sunday .. 6=Saturday.
 */
fun dayOfWeek(year: Int, month: Int, day: Int): Int {
    val t = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val y = if (month < 3) year - 1 else year
    return (y + y / 4 - y / 100 + y / 400 + t[month - 1] + day) % 7
}

val MONTH_LONG: List<String> = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

val WEEKDAY_SHORT: List<String> = listOf("S", "M", "T", "W", "T", "F", "S")
