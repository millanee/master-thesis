package com.millane.thesis.application.data.study

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import com.millane.thesis.application.study.InterventionAttempt
import com.millane.thesis.application.study.InterventionType
import com.millane.thesis.application.study.StudyScheduleConfig
import com.millane.thesis.application.study.StudyGroup
import com.millane.thesis.application.study.StudyManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.UUID

class StudyRepository(private val context: Context) {

    private object Keys {
        val PARTICIPANT_ID = stringPreferencesKey("study_participant_id")
        val NICKNAME = stringPreferencesKey("study_nickname")
        val GROUP = stringPreferencesKey("study_group")
        val START_DATE_MS = longPreferencesKey("study_start_date_ms")
        val QUESTIONNAIRE_SUBMITTED = booleanPreferencesKey("study_questionnaire_submitted")
        val COMPLETION_NOTIFICATION_SHOWN = booleanPreferencesKey("study_completion_notification_shown")
    }

    private val assignment = FirestoreStudyAssignment(FirebaseFirestore.getInstance())
    private val firestore = FirebaseFirestore.getInstance()
    private val appSelectionRepository = AppSelectionRepository(context)
    private val locationsRepository = LocationsRepository(context)
    private val bedtimeRepository = BedtimeRepository(context)

    val participantId: Flow<String?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.PARTICIPANT_ID] }

    val nickname: Flow<String?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.NICKNAME] }

    val group: Flow<StudyGroup?> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[Keys.GROUP] ?: return@map null
            runCatching { StudyGroup.valueOf(raw) }.getOrNull()
        }

    val startDateMs: Flow<Long?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.START_DATE_MS] }

    val questionnaireSubmitted: Flow<Boolean> =
        context.appDataStore.data.map { prefs -> prefs[Keys.QUESTIONNAIRE_SUBMITTED] ?: false }

    val completionNotificationShown: Flow<Boolean> =
        context.appDataStore.data.map { prefs -> prefs[Keys.COMPLETION_NOTIFICATION_SHOWN] ?: false }

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

    suspend fun saveNickname(nickname: String) {
        val trimmed = nickname.trim()
        require(trimmed.isNotEmpty()) { "Nickname must not be blank." }

        val participantId = getOrCreateParticipantId()

        context.appDataStore.edit { prefs ->
            prefs[Keys.NICKNAME] = trimmed
        }

        firestore.collection("participants")
            .document(participantId)
            .set(
                mapOf(
                    "participantId" to participantId,
                    "nickname" to trimmed,
                    "createdAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
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

    suspend fun markCompletionNotificationShownIfNeeded(): Boolean {
        val shouldShow = !completionNotificationShown.first()
        if (!shouldShow) return false

        context.appDataStore.edit { prefs ->
            prefs[Keys.COMPLETION_NOTIFICATION_SHOWN] = true
        }
        return true
    }

    suspend fun submitSusQuestionnaire(
        answers: List<Int>,
        questions: List<String>
    ): SusQuestionnaireSubmission {
        require(answers.isNotEmpty()) { "Answers must not be empty." }
        require(answers.size == questions.size) { "Questions and answers must have the same size." }
        require(answers.size == STANDARD_SUS_ITEM_COUNT) {
            "SUS questionnaire must contain exactly $STANDARD_SUS_ITEM_COUNT items."
        }
        require(answers.all { it in 1..5 }) { "All answers must be between 1 and 5." }

        val participantId = getOrCreateParticipantId()
        val susScore = calculateStandardSusScore(answers)
        val submittedAtMs = System.currentTimeMillis()
        val responses = questions.mapIndexed { index, question ->
            mapOf(
                "questionId" to "sus_${index + 1}",
                "questionText" to question,
                "answer" to answers[index]
            )
        }

        firestore.collection("susQuestionnaires")
            .document(participantId)
            .set(
                mapOf(
                    "participantId" to participantId,
                    "answers" to answers,
                    "responses" to responses,
                    "susScore" to susScore,
                    "submittedAtMs" to submittedAtMs
                ),
                SetOptions.merge()
            )

        firestore.collection("participants")
            .document(participantId)
            .set(
                mapOf(
                    "questionnaireSubmitted" to true,
                    "susScore" to susScore,
                    "questionnaireSubmittedAtMs" to submittedAtMs
                ),
                SetOptions.merge()
            )

        context.appDataStore.edit { prefs ->
            prefs[Keys.QUESTIONNAIRE_SUBMITTED] = true
        }

        return SusQuestionnaireSubmission(
            participantId = participantId,
            answers = answers,
            susScore = susScore,
            submittedAtMs = submittedAtMs
        )
    }

    suspend fun getRaffleEligibility(participantId: String): RaffleEligibilityResult {
        val sessionSnapshot = firestore.collection("sessions")
            .whereEqualTo("participantId", participantId)
            .get()
            .await()

        val appearedInterventionCount = sessionSnapshot.documents.sumOf { document ->
            parseInterventionAttempts(document.get("interventionAttempts"))
                .count { attempt -> attempt.shownAtMs > 0L }
        }
        val answeredReactanceCount = sessionSnapshot.documents.sumOf { document ->
            parseInterventionAttempts(document.get("interventionAttempts"))
                .count { attempt -> attempt.reactanceAnsweredAtMs != null }
        }
        val missingReactanceCount = (appearedInterventionCount - answeredReactanceCount).coerceAtLeast(0)

        val goalAdvancementSessionsWithoutGoals = sessionSnapshot.documents.count { document ->
            document.getString("activeInterventionType") == InterventionType.GOAL_ADVANCEMENT.name &&
                (document.getLong("goalsCountAtSessionStart") ?: 0L) < 1L
        }

        val unansweredContextConfirmationCount = sessionSnapshot.documents.count { document ->
            document.getLong("closedAtMs") != null &&
                document.get("contextValidationStatus") == null
        }

        return RaffleEligibilityResult(
            isEligible = missingReactanceCount <= MAX_MISSING_REACTANCE_RESPONSES &&
                goalAdvancementSessionsWithoutGoals <= MAX_GOAL_ADVANCEMENT_SESSIONS_WITHOUT_GOALS &&
                unansweredContextConfirmationCount <= MAX_UNANSWERED_CONTEXT_CONFIRMATIONS,
            appearedInterventionCount = appearedInterventionCount,
            answeredReactanceCount = answeredReactanceCount,
            missingReactanceCount = missingReactanceCount,
            goalAdvancementSessionsWithoutGoals = goalAdvancementSessionsWithoutGoals,
            unansweredContextConfirmationCount = unansweredContextConfirmationCount
        )
    }

    private fun calculateStandardSusScore(susAnswers: List<Int>): Double {
        require(susAnswers.size == STANDARD_SUS_ITEM_COUNT) {
            "Standard SUS scoring requires exactly $STANDARD_SUS_ITEM_COUNT answers."
        }

        // Standard SUS scoring:
        // - odd-numbered items are positive, so contribution = answer - 1
        // - even-numbered items are negative, so contribution = 5 - answer
        // The 10 contributions sum to a value in [0, 40], which is then multiplied by 2.5
        // to produce the final SUS score in [0, 100].
        val susContributionSum = susAnswers.mapIndexed { index, answer ->
            if (index % 2 == 0) {
                answer - 1
            } else {
                5 - answer
            }
        }.sum()

        return susContributionSum * 2.5
    }

    private fun parseInterventionAttempts(raw: Any?): List<InterventionAttempt> {
        val list = raw as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null

            val shownAtMs = (map["shownAtMs"] as? Number)?.toLong() ?: return@mapNotNull null
            val dismissedAtMs = (map["dismissedAtMs"] as? Number)?.toLong()
            val appClosedViaIntervention = map["appClosedViaIntervention"] as? Boolean ?: false
            val appClosedViaInterventionAtMs =
                (map["appClosedViaInterventionAtMs"] as? Number)?.toLong()
            val reactanceAnsweredAtMs =
                (map["reactanceAnsweredAtMs"] as? Number)?.toLong()
            val reactanceResponses =
                (map["reactanceResponses"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
                    ?: emptyList()
            val reactanceMeanScore =
                (map["reactanceMeanScore"] as? Number)?.toDouble()

            InterventionAttempt(
                shownAtMs = shownAtMs,
                dismissedAtMs = dismissedAtMs,
                appClosedViaIntervention = appClosedViaIntervention,
                appClosedViaInterventionAtMs = appClosedViaInterventionAtMs,
                reactanceAnsweredAtMs = reactanceAnsweredAtMs,
                reactanceResponses = reactanceResponses,
                reactanceMeanScore = reactanceMeanScore
            )
        }
    }

    private companion object {
        const val STANDARD_SUS_ITEM_COUNT = 10
        const val MAX_MISSING_REACTANCE_RESPONSES = 6
        const val MAX_GOAL_ADVANCEMENT_SESSIONS_WITHOUT_GOALS = 6
        const val MAX_UNANSWERED_CONTEXT_CONFIRMATIONS = 8
    }
}

data class CurrentStudySnapshot(
    val participantId: String,
    val studyGroup: StudyGroup,
    val startDateMs: Long,
    val studyWeek: Int,
    val activeInterventionType: InterventionType
)

data class SusQuestionnaireSubmission(
    val participantId: String,
    val answers: List<Int>,
    val susScore: Double,
    val submittedAtMs: Long
)

data class RaffleEligibilityResult(
    val isEligible: Boolean,
    val appearedInterventionCount: Int,
    val answeredReactanceCount: Int,
    val missingReactanceCount: Int,
    val goalAdvancementSessionsWithoutGoals: Int,
    val unansweredContextConfirmationCount: Int
)
