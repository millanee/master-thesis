package com.millane.thesis.application.data.study

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.millane.thesis.application.data.apps.AppSelectionRepository
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.study.InterventionType
import com.millane.thesis.application.study.StudyGroup
import com.millane.thesis.application.study.StudyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

class StudyRepository(private val context: Context) {

    private object Keys {
        val PARTICIPANT_ID = stringPreferencesKey("study_participant_id")
        val GROUP = stringPreferencesKey("study_group")
        val START_DATE_MS = longPreferencesKey("study_start_date_ms")
    }

    private val assignment = FirestoreStudyAssignment(FirebaseFirestore.getInstance())
    private val firestore = FirebaseFirestore.getInstance()
    private val appSelectionRepository = AppSelectionRepository(context)

    val participantId: Flow<String?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.PARTICIPANT_ID] }

    val group: Flow<StudyGroup?> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[Keys.GROUP] ?: return@map null
            runCatching { StudyGroup.valueOf(raw) }.getOrNull()
        }

    val startDateMs: Flow<Long?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.START_DATE_MS] }

    suspend fun initStudyIfMissing(): Triple<String, StudyGroup, Long> {
        val pid = getOrCreateParticipantId()

        setStartDateNowIfMissing()
        val start = startDateMs.first() ?: System.currentTimeMillis()

        val currentGroup = group.first()
        val finalGroup = if (currentGroup == null) {
            val g = assignment.assignGroup(pid, start)
            setGroup(g)
            g
        } else {
            currentGroup
        }

        syncParticipantTargetAppsToFirestore(pid)

        return Triple(pid, finalGroup, start)
    }

    suspend fun getCurrentStudySnapshot(): CurrentStudySnapshot? {
        val pid = participantId.first() ?: return null
        val grp = group.first() ?: return null
        val start = startDateMs.first() ?: return null

        val week = StudyManager.weekIndex(start)
        val intervention = StudyManager.interventionFor(grp, week)

        return CurrentStudySnapshot(
            participantId = pid,
            studyGroup = grp,
            startDateMs = start,
            studyWeek = week,
            activeInterventionType = intervention
        )
    }

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

    suspend fun syncParticipantTargetAppsToFirestore(participantId: String) {
        val selectedApps = appSelectionRepository.selectedApps.first().toList()

        firestore.collection("participants")
            .document(participantId)
            .set(
                mapOf("targetApps" to selectedApps),
                SetOptions.merge()
            )
    }
}

data class CurrentStudySnapshot(
    val participantId: String,
    val studyGroup: StudyGroup,
    val startDateMs: Long,
    val studyWeek: Int,
    val activeInterventionType: InterventionType
)