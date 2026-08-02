package com.millane.thesis.application.study

object StudyManager {

    const val TOTAL_INTERVENTION_PHASES: Int = StudyScheduleConfig.INTERVENTION_PHASE_COUNT

    fun elapsedPhaseCount(startDateMs: Long, nowMs: Long = System.currentTimeMillis()): Int {
        val diffMs = nowMs - startDateMs
        if (diffMs <= 0L) return 0
        return (diffMs / StudyScheduleConfig.phaseDurationMs).toInt()
    }

    fun weekIndex(startDateMs: Long, nowMs: Long = System.currentTimeMillis()): Int =
        elapsedPhaseCount(startDateMs, nowMs).coerceIn(0, TOTAL_INTERVENTION_PHASES - 1)

    fun isStudyComplete(startDateMs: Long, nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs >= startDateMs + StudyScheduleConfig.totalStudyDurationMs

    fun interventionFor(group: StudyGroup, weekIndex: Int): InterventionType {
        val sequence = LatinSquare[group] ?: error("Missing sequence for group=$group")
        // clamp to last week if study extends beyond 3 weeks
        val idx = weekIndex.coerceIn(0, sequence.lastIndex)
        return sequence[idx]
    }
}
