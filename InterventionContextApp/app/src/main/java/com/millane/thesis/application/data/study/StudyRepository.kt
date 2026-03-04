package com.millane.thesis.application.data.study

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.study.StudyGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class StudyRepository(private val context: Context) {

    private object Keys {
        val PARTICIPANT_ID = stringPreferencesKey("study_participant_id")
        val GROUP = stringPreferencesKey("study_group") // "A", "B", "C"
        val START_DATE_MS = longPreferencesKey("study_start_date_ms")
    }

    // Flows (for ViewModel observe)
    val participantId: Flow<String?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.PARTICIPANT_ID] }

    val group: Flow<StudyGroup?> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[Keys.GROUP] ?: return@map null
            runCatching { StudyGroup.valueOf(raw) }.getOrNull()
        }

    val startDateMs: Flow<Long?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.START_DATE_MS] }

    /**
     * Returns existing id, otherwise generates and stores a new UUID.
     */
    suspend fun getOrCreateParticipantId(): String {
        val prefs = context.appDataStore.data.first()
        val existingId = prefs[Keys.PARTICIPANT_ID]
        if (!existingId.isNullOrBlank()) return existingId

        val newId = UUID.randomUUID().toString()
        context.appDataStore.edit { it[Keys.PARTICIPANT_ID] = newId }
        return newId
    }

    suspend fun setGroup(group: StudyGroup) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.GROUP] = group.name
        }
    }

    /**
     * Set start date to "now" only if it doesn't exist yet.
     */
    suspend fun setStartDateNowIfMissing() {
        val prefs = context.appDataStore.data.first()
        if (prefs[Keys.START_DATE_MS] != null) return

        val now = System.currentTimeMillis()
        context.appDataStore.edit { it[Keys.START_DATE_MS] = now }
    }

    suspend fun setStartDateMs(value: Long) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.START_DATE_MS] = value
        }
    }
}