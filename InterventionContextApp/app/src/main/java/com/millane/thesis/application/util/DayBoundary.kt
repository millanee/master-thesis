package com.millane.thesis.application.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Returns the start of the current "day" in ms.
 *
 * For now (testing), "day" is defined as the boundary at **12:15 Europe/Istanbul**.
 * If it's before 12:15 (Istanbul time), the current day is considered to have started
 * at 12:15 yesterday (Istanbul time).
 */
fun getCurrentDayBoundaryMs(): Long {
    val tz = TimeZone.getTimeZone("Europe/Istanbul")
    val cal = Calendar.getInstance(tz)
    val nowMs = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 12)
    cal.set(Calendar.MINUTE, 25)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val todayBoundary = cal.timeInMillis
    return if (nowMs < todayBoundary) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
        cal.timeInMillis
    } else {
        todayBoundary
    }
}
