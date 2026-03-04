package com.millane.thesis.application.study

import kotlin.math.max

object StudyManager {

    fun weekIndex(startDateMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val diff = nowMs - startDateMs
        val weeks = (diff / (1000L * 60 * 60 * 24 * 7)).toInt()
        return max(0, weeks)
    }

    fun interventionFor(group: StudyGroup, weekIndex: Int): InterventionType {
        val sequence = LatinSquare[group] ?: error("Missing sequence for group=$group")
        // clamp to last week if study extends beyond 3 weeks
        val idx = weekIndex.coerceIn(0, sequence.lastIndex)
        return sequence[idx]
    }
}