package com.littlebridge.enrollplus.util

import java.util.Calendar

actual fun todayIso(): String {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return isoOf(y, m, d)
}

actual fun nowMinutesOfDay(): Int {
    val cal = Calendar.getInstance()
    return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
}
