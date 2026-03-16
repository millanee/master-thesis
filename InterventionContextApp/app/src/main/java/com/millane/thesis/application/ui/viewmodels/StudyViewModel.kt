package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.study.StudyRepository
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
    val status: StudyStatus = StudyStatus.NOT_STARTED
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StudyRepository(app.applicationContext)

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val snapshot: StateFlow<StudySnapshot> =
        combine(repo.participantId, repo.nickname, repo.group, repo.startDateMs, clock) { pid, nickname, group, start, now ->
            if (pid == null || group == null || start == null) {
                StudySnapshot(participantId = pid, nickname = nickname, group = group, startDateMs = start)
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
                        status = status
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
                    status = status
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
}
