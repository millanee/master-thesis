package com.millane.thesis.application.study

enum class StudyScheduleMode {
    STANDARD_21_DAY,
    TEST_3_DAY,
    FAST_TEST_15_MIN
}

object StudyScheduleConfig {

    // Switch this back to STANDARD_21_DAY to restore the real study schedule.
    val ACTIVE_MODE: StudyScheduleMode = StudyScheduleMode.FAST_TEST_15_MIN

    private const val MINUTE_MS = 60_000L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    const val INTERVENTION_PHASE_COUNT: Int = 3

    val phaseDurationMs: Long
        get() = when (ACTIVE_MODE) {
            StudyScheduleMode.STANDARD_21_DAY -> 7L * DAY_MS
            // Test schedule that preserves the normal 4 AM study-day boundary,
            // but compresses the full study to three full days.
            StudyScheduleMode.TEST_3_DAY -> DAY_MS
            StudyScheduleMode.FAST_TEST_15_MIN -> 5L * MINUTE_MS
        }

    val totalStudyDurationMs: Long
        get() = phaseDurationMs * INTERVENTION_PHASE_COUNT

    val startsImmediately: Boolean
        get() = ACTIVE_MODE == StudyScheduleMode.FAST_TEST_15_MIN
}
