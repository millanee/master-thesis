package com.millane.thesis.application.data.dailygoals

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    }

    private val defaultGoal = DailyGoal(
        id = "default",
        text = "Create Figma Design"
    )

    val goals: Flow<List<DailyGoal>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[Keys.DAILY_GOALS_JSON]
            if (raw.isNullOrBlank()) {
                listOf(defaultGoal)
            } else {
                runCatching { JsonCodec.decode<List<DailyGoal>>(raw) }
                    .getOrElse { listOf(defaultGoal) }
                    .ifEmpty { listOf(defaultGoal) }
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

        // keep at least 1 (safety)
        val safe = if (updated.isEmpty()) listOf(defaultGoal) else updated

        context.appDataStore.edit { prefs ->
            prefs[Keys.DAILY_GOALS_JSON] = JsonCodec.encode(safe)
        }
    }

    private suspend fun getGoalsOnce(): List<DailyGoal> {
        val prefs = context.appDataStore.data.first()
        val raw = prefs[Keys.DAILY_GOALS_JSON]
        if (raw.isNullOrBlank()) return listOf(defaultGoal)

        return runCatching { JsonCodec.decode<List<DailyGoal>>(raw) }
            .getOrElse { listOf(defaultGoal) }
            .ifEmpty { listOf(defaultGoal) }
    }
}