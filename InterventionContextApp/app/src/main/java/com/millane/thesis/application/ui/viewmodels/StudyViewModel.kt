package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.notifications.StudyCompletionNotifier
import com.millane.thesis.application.data.study.StudyRepository
import com.millane.thesis.application.data.study.SusQuestionnaireSubmission
import com.millane.thesis.application.study.InterventionType
import com.millane.thesis.application.study.StudyGroup
import com.millane.thesis.application.study.StudyManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StudyStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED
}

data class StudySnapshot(
    val participantId: String? = null,
    val nickname: String? = null,
    val group: StudyGroup? = null,
    val startDateMs: Long? = null,
    val weekIndex: Int? = null,
    val activeIntervention: InterventionType? = null,
    val status: StudyStatus = StudyStatus.NOT_STARTED,
    val questionnaireSubmitted: Boolean = false
)

data class SusQuestion(
    val id: String,
    val prompt: String
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StudyRepository(app.applicationContext)
    private val notifier = StudyCompletionNotifier(app.applicationContext)

    val susQuestions: List<SusQuestion> = listOf(
        SusQuestion("sus_1", "I think that I would like to use this app frequently."),
        SusQuestion("sus_2", "I found the app unnecessarily complex."),
        SusQuestion("sus_3", "I thought the app was easy to use."),
        SusQuestion("sus_4", "I think that I would need the support of a technical person to be able to use this app."),
        SusQuestion("sus_5", "I found the various functions in this app were well integrated."),
        SusQuestion("sus_6", "I thought there was too much inconsistency in this app."),
        SusQuestion("sus_7", "I would imagine that most people would learn to use this app very quickly."),
        SusQuestion("sus_8", "I found the app very awkward to use."),
        SusQuestion("sus_9", "I felt very confident using the app."),
        SusQuestion("sus_10", "I needed to learn a lot of things before I could get going with this app.")
    )

    private data class ParticipantState(
        val participantId: String?,
        val nickname: String?
    )

    private data class StudyState(
        val group: StudyGroup?,
        val startDateMs: Long?
    )

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val snapshot: StateFlow<StudySnapshot> =
        combine(
            combine(repo.participantId, repo.nickname) { pid, nickname ->
                ParticipantState(participantId = pid, nickname = nickname)
            },
            combine(repo.group, repo.startDateMs) { group, start ->
                StudyState(group = group, startDateMs = start)
            },
            repo.questionnaireSubmitted,
            clock
        ) { participantState, studyState, questionnaireSubmitted, now ->
            val pid = participantState.participantId
            val nickname = participantState.nickname
            val group = studyState.group
            val start = studyState.startDateMs
            if (pid == null || group == null || start == null) {
                StudySnapshot(
                    participantId = pid,
                    nickname = nickname,
                    group = group,
                    startDateMs = start,
                    questionnaireSubmitted = questionnaireSubmitted
                )
            } else {
                val status = when {
                    now < start -> StudyStatus.NOT_STARTED
                    StudyManager.isStudyComplete(start, now) -> StudyStatus.COMPLETED
                    else -> StudyStatus.IN_PROGRESS
                }

                if (status != StudyStatus.IN_PROGRESS) {
                    return@combine StudySnapshot(
                        participantId = pid,
                        nickname = nickname,
                        group = group,
                        startDateMs = start,
                        status = status,
                        questionnaireSubmitted = questionnaireSubmitted
                    )
                }

                val w = StudyManager.weekIndex(start, now)
                val active = StudyManager.interventionFor(group, w)
                StudySnapshot(
                    participantId = pid,
                    nickname = nickname,
                    group = group,
                    startDateMs = start,
                    weekIndex = w,
                    activeIntervention = active,
                    status = status,
                    questionnaireSubmitted = questionnaireSubmitted
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudySnapshot())

    fun initIfMissing() {
        viewModelScope.launch {
            repo.initStudyIfMissing()
        }
    }

    fun setGroup(group: StudyGroup) {
        viewModelScope.launch { repo.setGroup(group) }
    }

    fun setStartDateNow() {
        viewModelScope.launch { repo.setStartDateToNextStudyDay(System.currentTimeMillis()) }
    }

    fun saveNickname(nickname: String) {
        viewModelScope.launch { repo.saveNickname(nickname) }
    }

    suspend fun maybeShowStudyCompletionNotification() {
        val shouldShow = repo.markCompletionNotificationShownIfNeeded()
        if (shouldShow) {
            notifier.show()
        }
    }

    suspend fun submitSusQuestionnaire(answers: List<Int>): SusQuestionnaireSubmission {
        return repo.submitSusQuestionnaire(
            answers = answers,
            questions = susQuestions.map { it.prompt }
        )
    }
}
