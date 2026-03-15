package com.millane.thesis.application.data.study

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.millane.thesis.application.data.apps.AppSelectionRepository
import com.millane.thesis.application.data.bedtime.BedtimeRepository
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.study.InterventionType
import com.millane.thesis.application.study.StudyScheduleConfig
import com.millane.thesis.application.study.StudyGroup
import com.millane.thesis.application.study.StudyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Calendar
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
    private val locationsRepository = LocationsRepository(context)
    private val bedtimeRepository = BedtimeRepository(context)

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
     * True when app selection is submitted, locations are submitted with at least one
     * home and one work location, and bedtime is submitted.
     */
    suspend fun isOnboardingComplete(): Boolean {
        if (!appSelectionRepository.isSubmitted.first()) return false
        if (!locationsRepository.isSubmitted.first()) return false
        if (!bedtimeRepository.isSubmitted.first()) return false
        val locations = locationsRepository.locations.first()
        val hasHome = locations.any { it.contextType == LocationContextType.HOME }
        val hasWork = locations.any { it.contextType == LocationContextType.WORK }
        return hasHome && hasWork
    }

    /**
     * Ensures participant ID exists. When onboarding is complete for the first time
     * (apps submitted, locations with at least one HOME and WORK, and bedtime submitted),
     * this also sets the study start date and assigns the study group.
     *
     * The study start date depends on the active schedule mode:
     * standard mode starts at the next 4 AM boundary, while fast test mode starts immediately.
     */
    suspend fun initStudyIfMissing(): Triple<String, StudyGroup?, Long?> {
        val pid = getOrCreateParticipantId()

        val onboardingComplete = isOnboardingComplete()

        val start = startDateMs.first()
        val startToUse = if (onboardingComplete) {
            if (start == null) {
                val now = System.currentTimeMillis()
                val firstStudyDayStart = computeStudyStartMs(now)
                setStartDateMs(firstStudyDayStart)
                firstStudyDayStart
            } else {
                start
            }
        } else {
            // Onboarding not complete yet: don't set a start date.
            null
        }

        val currentGroup = group.first()
        val finalGroup = if (onboardingComplete) {
            if (currentGroup == null && startToUse != null) {
                val g = assignment.assignGroup(pid, startToUse)
                setGroup(g)
                g
            } else {
                currentGroup
            }
        } else {
            currentGroup
        }

        if (onboardingComplete) {
            syncParticipantTargetAppsToFirestore(pid)
        }

        return Triple(pid, finalGroup, startToUse)
    }

    suspend fun getCurrentStudySnapshot(): CurrentStudySnapshot? {
        val pid = participantId.first() ?: return null
        val grp = group.first() ?: return null
        val start = startDateMs.first() ?: return null

        val now = System.currentTimeMillis()
        // Before the first study day has begun: study not yet active.
        if (now < start) return null

        if (StudyManager.isStudyComplete(start, now)) return null

        val week = StudyManager.weekIndex(start, now)
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

    suspend fun setStartDateMs(value: Long) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.START_DATE_MS] = value
        }
    }

    /**
     * Sets the study start date using the active schedule mode.
     */
    suspend fun setStartDateToNextStudyDay(nowMs: Long = System.currentTimeMillis()) {
        val firstStudyDayStart = computeStudyStartMs(nowMs)
        setStartDateMs(firstStudyDayStart)
    }

    private fun computeStudyStartMs(nowMs: Long): Long {
        if (StudyScheduleConfig.startsImmediately) return nowMs
        return computeNextStudyDayBoundary4AmMs(nowMs)
    }

    private fun computeNextStudyDayBoundary4AmMs(nowMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(Calendar.HOUR_OF_DAY, 4)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today4Am = cal.timeInMillis
        return if (nowMs < today4Am) {
            today4Am
        } else {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.timeInMillis
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
