package com.millane.thesis.application.data.dailygoals

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.millane.thesis.application.data.datastore.JsonCodec
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.domain.dailygoals.DailyGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class DailyGoalsRepository(
    private val context: Context
) {
    private object Keys {
        val DAILY_GOALS_JSON = stringPreferencesKey("daily_goals_json")
        val LAST_DAILY_GOALS_PROMPT_DAY_MS = longPreferencesKey("last_daily_goals_prompt_day_ms")
    }

    val goals: Flow<List<DailyGoal>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[Keys.DAILY_GOALS_JSON]
            if (raw.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching { JsonCodec.decode<List<DailyGoal>>(raw) }
                    .getOrElse { emptyList() }
            }
        }

    suspend fun addGoal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val current = getGoalsOnce()
        val updated = current + DailyGoal(id = UUID.randomUUID().toString(), text = trimmed)

        context.appDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOALS_JSON] = JsonCodec.encode(updated)
        }
    }

    suspend fun deleteGoal(id: String) {
        val current = getGoalsOnce()

        // rule: cannot delete if only one goal remains
        if (current.size <= 1) return

        val updated = current.filterNot { it.id == id }

        context.appDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOALS_JSON] = JsonCodec.encode(updated)
        }
    }

    /** Replaces all stored goals (e.g. when user submits new daily goals from the first-unlock dialog). Pass empty to clear. */
    suspend fun replaceAllGoals(goals: List<DailyGoal>) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOALS_JSON] = JsonCodec.encode(goals)
        }
    }

    /** Last "day" (4 AM boundary in ms) when we showed the daily goals prompt. Used for first-unlock-after-4AM. */
    suspend fun getLastDailyGoalsPromptDayMs(): Long? {
        val prefs = context.appDataStore.data.first()
        return prefs[Keys.LAST_DAILY_GOALS_PROMPT_DAY_MS]
    }

    suspend fun setLastDailyGoalsPromptDayMs(dayBoundaryMs: Long) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.LAST_DAILY_GOALS_PROMPT_DAY_MS] = dayBoundaryMs
        }
    }

    suspend fun clearLastDailyGoalsPromptDayMs() {
        context.appDataStore.edit { prefs ->
            prefs.remove(Keys.LAST_DAILY_GOALS_PROMPT_DAY_MS)
        }
    }

    private suspend fun getGoalsOnce(): List<DailyGoal> {
        val prefs = context.appDataStore.data.first()
        val raw = prefs[Keys.DAILY_GOALS_JSON]
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { JsonCodec.decode<List<DailyGoal>>(raw) }.getOrElse { emptyList() }
    }
}
