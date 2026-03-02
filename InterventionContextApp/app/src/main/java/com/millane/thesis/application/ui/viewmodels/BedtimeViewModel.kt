package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.bedtime.BedtimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BedtimeViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val repo = BedtimeRepository(appContext)

    val isSubmitted: StateFlow<Boolean> =
        repo.isSubmitted.stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), false)

    private val persistedBedtime: StateFlow<String?> =
        repo.bedtime.stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), null)

    // draft: solange nicht submitted, darf UI es ändern; danach = persisted (locked)
    private val _draftBedtime = MutableStateFlow<String?>(null)
    val draftBedtime: StateFlow<String?> = _draftBedtime.asStateFlow()

    init {
        viewModelScope.launch {
            combine(persistedBedtime, isSubmitted) { persisted, submitted ->
                persisted to submitted
            }.collect { (persisted, submitted) ->
                if (submitted) {
                    _draftBedtime.value = persisted
                } else {
                    // wenn noch nie gesetzt, initialisiere draft aus persisted (falls vorhanden)
                    if (_draftBedtime.value == null) _draftBedtime.value = persisted
                }
            }
        }
    }

    fun setDraft(hour: Int, minute: Int) {
        if (isSubmitted.value) return
        _draftBedtime.value = "%02d:%02d".format(hour, minute)
    }

    fun submit() {
        if (isSubmitted.value) return
        val time = _draftBedtime.value ?: return
        viewModelScope.launch {
            repo.saveBedtime(time)
            repo.setSubmitted(true)
        }
    }

    fun resetForTesting() {
        viewModelScope.launch {
            repo.setSubmitted(false)
            repo.saveBedtime("")
            _draftBedtime.value = null
        }
    }
}