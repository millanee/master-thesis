package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.study.StudyRepository
import com.millane.thesis.application.study.InterventionType
import com.millane.thesis.application.study.StudyGroup
import com.millane.thesis.application.study.StudyManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudySnapshot(
    val participantId: String? = null,
    val group: StudyGroup? = null,
    val startDateMs: Long? = null,
    val weekIndex: Int? = null,
    val activeIntervention: InterventionType? = null
)

class StudyViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StudyRepository(app.applicationContext)

    val snapshot: StateFlow<StudySnapshot> =
        combine(repo.participantId, repo.group, repo.startDateMs) { pid, group, start ->
            if (pid == null || group == null || start == null) {
                StudySnapshot(participantId = pid, group = group, startDateMs = start)
            } else {
                val w = StudyManager.weekIndex(start)
                val active = StudyManager.interventionFor(group, w)
                StudySnapshot(
                    participantId = pid,
                    group = group,
                    startDateMs = start,
                    weekIndex = w,
                    activeIntervention = active
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudySnapshot())

    fun initIfMissing(defaultGroup: StudyGroup = StudyGroup.A) {
        viewModelScope.launch {
            repo.getOrCreateParticipantId()
            // set defaults only if missing
            if (snapshot.value.group == null) repo.setGroup(defaultGroup)
            repo.setStartDateNowIfMissing()
        }
    }

    fun setGroup(group: StudyGroup) {
        viewModelScope.launch { repo.setGroup(group) }
    }

    fun setStartDateNow() {
        viewModelScope.launch { repo.setStartDateMs(System.currentTimeMillis()) }
    }
}