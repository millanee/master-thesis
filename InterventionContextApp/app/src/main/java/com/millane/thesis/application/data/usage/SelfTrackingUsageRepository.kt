package com.millane.thesis.application.data.usage

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.util.getCurrentStudyDayBoundary4AmMs
import kotlinx.coroutines.flow.first

data class SelfTrackingUsageSnapshot(
    val boundaryMs: Long,
    val totalMsToday: Long,
    val totalHomeMsToday: Long,
    val lastHomeUsedAtMs: Long?,
    val perHourMs: List<Long>
)

class SelfTrackingUsageRepository(private val context: Context) {

    private object Keys {
        val LAST_RESET_BOUNDARY = longPreferencesKey("selftrack_last_reset_boundary_ms")
        val TOTAL_MS_TODAY = longPreferencesKey("selftrack_total_ms_today")
        val TOTAL_HOME_MS_TODAY = longPreferencesKey("selftrack_total_home_ms_today")
        val LAST_HOME_USED_AT_MS = longPreferencesKey("selftrack_last_home_used_at_ms")
    }

    private val hourKeys = (0 until 24).map { idx ->
        longPreferencesKey("selftrack_hour_ms_$idx")
    }

    private val dataStore
        get() = context.appDataStore

    /**
     * Ensures that statistics are initialized for the current 4 AM study-day boundary.
     * If stored data belongs to a previous day, all counters are reset.
     */
    private suspend fun ensureFreshForToday(boundaryMs: Long) {
        val prefs = dataStore.data.first()
        val lastBoundary = prefs[Keys.LAST_RESET_BOUNDARY]
        if (lastBoundary == boundaryMs) return

        dataStore.edit { e ->
            e[Keys.LAST_RESET_BOUNDARY] = boundaryMs
            e[Keys.TOTAL_MS_TODAY] = 0L
            e[Keys.TOTAL_HOME_MS_TODAY] = 0L
            e[Keys.LAST_HOME_USED_AT_MS] = 0L
            hourKeys.forEach { key -> e[key] = 0L }
        }
    }

    suspend fun touchToday() {
        val boundary = getCurrentStudyDayBoundary4AmMs()
        ensureFreshForToday(boundary)
    }

    /**
     * Persist a finished self-tracking session into today's stats. The tracked window
     * is clamped to the current study day [4 AM, 4 AM + 24h).
     */
    suspend fun recordFinishedSession(
        startMs: Long,
        endMs: Long,
        isHome: Boolean
    ) {
        val boundary = getCurrentStudyDayBoundary4AmMs()
        ensureFreshForToday(boundary)

        val clampedStart = maxOf(startMs, boundary)
        val clampedEnd = minOf(endMs, boundary + DAY_MS)
        if (clampedEnd <= clampedStart) return

        val (perHourDelta, totalDelta) = computeHourlyDeltas(boundary, clampedStart, clampedEnd)

        dataStore.edit { e ->
            hourKeys.forEachIndexed { idx, key ->
                val old = e[key] ?: 0L
                e[key] = old + perHourDelta[idx]
            }

            val oldTotal = e[Keys.TOTAL_MS_TODAY] ?: 0L
            e[Keys.TOTAL_MS_TODAY] = oldTotal + totalDelta

            if (isHome) {
                val oldHome = e[Keys.TOTAL_HOME_MS_TODAY] ?: 0L
                e[Keys.TOTAL_HOME_MS_TODAY] = oldHome + totalDelta
                e[Keys.LAST_HOME_USED_AT_MS] = clampedEnd
            }
        }
    }

    /**
     * Returns a snapshot of persisted usage and optionally adds the ongoing session
     * (if any) on top for display purposes. The ongoing segment is not persisted.
     */
    suspend fun getSnapshotIncludingOngoing(
        nowMs: Long,
        ongoingStartMs: Long?,
        ongoingIsHome: Boolean
    ): SelfTrackingUsageSnapshot {
        val boundary = getCurrentStudyDayBoundary4AmMs()
        ensureFreshForToday(boundary)

        val prefs = dataStore.data.first()

        val basePerHour = hourKeys.map { key -> prefs[key] ?: 0L }.toMutableList()
        var total = prefs[Keys.TOTAL_MS_TODAY] ?: 0L
        var totalHome = prefs[Keys.TOTAL_HOME_MS_TODAY] ?: 0L
        val lastHomeUsedAt = (prefs[Keys.LAST_HOME_USED_AT_MS] ?: 0L).takeIf { it > 0 }

        if (ongoingStartMs != null) {
            val clampedStart = maxOf(ongoingStartMs, boundary)
            val clampedEnd = minOf(nowMs, boundary + DAY_MS)
            if (clampedEnd > clampedStart) {
                val (perHourDelta, totalDelta) =
                    computeHourlyDeltas(boundary, clampedStart, clampedEnd)

                basePerHour.indices.forEach { idx ->
                    basePerHour[idx] = basePerHour[idx] + perHourDelta[idx]
                }
                total += totalDelta
                if (ongoingIsHome) {
                    totalHome += totalDelta
                }
            }
        }

        return SelfTrackingUsageSnapshot(
            boundaryMs = boundary,
            totalMsToday = total,
            totalHomeMsToday = totalHome,
            lastHomeUsedAtMs = lastHomeUsedAt,
            perHourMs = basePerHour
        )
    }

    private fun computeHourlyDeltas(
        boundary: Long,
        startMs: Long,
        endMs: Long
    ): Pair<List<Long>, Long> {
        val result = MutableList(24) { 0L }
        var cursor = startMs
        while (cursor < endMs) {
            val hourIndex =
                (((cursor - boundary) / HOUR_MS).toInt()).coerceIn(0, result.lastIndex)
            val hourEnd = minOf(boundary + (hourIndex + 1) * HOUR_MS, endMs)
            val delta = hourEnd - cursor
            result[hourIndex] += delta
            cursor = hourEnd
        }
        val total = result.sum()
        return result to total
    }

    companion object {
        private const val HOUR_MS = 60 * 60 * 1000L
        private const val DAY_MS = 24 * HOUR_MS
    }
}

