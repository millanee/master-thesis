package com.millane.thesis.application.study

import java.util.Calendar
import kotlin.math.max

object StudyManager {

    private const val DAY_MS: Long = 24L * 60 * 60 * 1000

    // For testing: 1 day per intervention, total 3 days.
    // For the real 21-day study, set DAYS_PER_INTERVENTION = 7.
    const val DAYS_PER_INTERVENTION: Int = 1
    const val TOTAL_INTERVENTION_DAYS: Int = DAYS_PER_INTERVENTION * 3

    /**
     * Returns the index of the current study day (0-based), aligned to 4 AM local time.
     *
     * Day 0 covers the interval [startDateMs, startDateMs + 24h),
     * Day 1 covers [startDateMs + 24h, startDateMs + 48h), etc.
     */
    fun studyDayIndex(startDateMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val startBoundary = alignToStudyDayBoundary4Am(startDateMs)
        val currentBoundary = alignToStudyDayBoundary4Am(nowMs)
        val diffMs = currentBoundary - startBoundary
        if (diffMs <= 0L) return 0
        val days = (diffMs / DAY_MS).toInt()
        return max(0, days)
    }

    /**
     * Returns the week index (0-based) derived from the 4 AM–aligned study day index.
     *
     * Days 0–6  -> week 0
     * Days 7–13 -> week 1
     * Days 14–20 -> week 2
     */
    fun weekIndex(startDateMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val day = studyDayIndex(startDateMs, nowMs)
        return day / DAYS_PER_INTERVENTION
    }

    fun interventionFor(group: StudyGroup, weekIndex: Int): InterventionType {
        val sequence = LatinSquare[group] ?: error("Missing sequence for group=$group")
        // clamp to last week if study extends beyond 3 weeks
        val idx = weekIndex.coerceIn(0, sequence.lastIndex)
        return sequence[idx]
    }

    /**
     * Aligns a timestamp to the start of its corresponding study day at 4 AM local time.
     *
     * If [timeMs] is before 4 AM, the boundary is 4 AM of the previous calendar day.
     * If [timeMs] is at or after 4 AM, the boundary is 4 AM of the same calendar day.
     */
    private fun alignToStudyDayBoundary4Am(timeMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timeMs
        cal.set(Calendar.HOUR_OF_DAY, 4)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val boundaryToday = cal.timeInMillis
        return if (timeMs < boundaryToday) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
            cal.timeInMillis
        } else {
            boundaryToday
        }
    }
}
