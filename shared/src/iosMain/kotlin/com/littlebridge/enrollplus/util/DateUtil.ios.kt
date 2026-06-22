package com.littlebridge.enrollplus.util

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

actual fun todayIso(): String {
    val cal = NSCalendar.currentCalendar
    val comps = cal.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay,
        fromDate = NSDate()
    )
    return isoOf(comps.year.toInt(), comps.month.toInt(), comps.day.toInt())
}

actual fun nowMinutesOfDay(): Int {
    val cal = NSCalendar.currentCalendar
    val comps = cal.components(
        NSCalendarUnitHour or NSCalendarUnitMinute,
        fromDate = NSDate()
    )
    return comps.hour.toInt() * 60 + comps.minute.toInt()
}
