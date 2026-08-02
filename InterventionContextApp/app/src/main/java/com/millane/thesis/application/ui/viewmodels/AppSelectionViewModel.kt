package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.apps.TargetApps
import com.millane.thesis.application.data.apps.AppSelectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppSelectionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppSelectionRepository(app.applicationContext)

    val isSubmitted: StateFlow<Boolean> =
        repo.isSubmitted.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val persistedSelected: StateFlow<Set<String>> =
        repo.selectedApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    // Draft (UI) until submitted
    private val _draftSelected = MutableStateFlow<Set<String>>(emptySet())
    val draftSelected: StateFlow<Set<String>> = _draftSelected.asStateFlow()

    init {
        viewModelScope.launch {
            combine(persistedSelected, isSubmitted) { persisted, submitted ->
                persisted to submitted
            }.collect { (persisted, submitted) ->
                if (submitted) {
                    _draftSelected.value = persisted
                } else {
                    // initial draft = persisted (if any). If none stored, default = both target apps.
                    if (_draftSelected.value.isEmpty()) {
                        _draftSelected.value =
                            if (persisted.isNotEmpty()) persisted else TargetApps.all
                    }
                }
            }
        }
    }

    fun toggle(appPackage: String) {
        if (isSubmitted.value) return

        _draftSelected.value =
            if (_draftSelected.value.contains(appPackage)) {
                _draftSelected.value - appPackage
            } else {
                _draftSelected.value + appPackage
            }
    }

    fun submit() {
        if (isSubmitted.value) return

        val toSave = _draftSelected.value
        if (toSave.isEmpty()) return

        viewModelScope.launch {
            repo.saveSelectedApps(toSave)
            repo.setSubmitted(true)
        }
    }

    fun resetForTesting() {
        viewModelScope.launch {
            repo.setSubmitted(false)
            repo.saveSelectedApps(emptySet())
            _draftSelected.value = emptySet()
        }
    }
}