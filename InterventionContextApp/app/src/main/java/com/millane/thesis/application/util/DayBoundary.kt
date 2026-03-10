package com.millane.thesis.application.util

import java.util.Calendar

/**
 * Returns the start of the current "study day" in ms.
 *
 * A study day starts at **4:00 AM local time**.
 * If it's before 4 AM, the current study day is considered to have started at 4 AM yesterday.
 */
fun getCurrentStudyDayBoundary4AmMs(): Long {
    val cal = Calendar.getInstance()
    val nowMs = cal.timeInMillis
    cal.set(Calendar.HOUR_OF_DAY, 4)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val today4Am = cal.timeInMillis
    return if (nowMs < today4Am) {
        cal.add(Calendar.DAY_OF_MONTH, -1)
        cal.timeInMillis
    } else {
        today4Am
    }
}
