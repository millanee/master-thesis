package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.apps.AppSelectionRepository
import kotlinx.coroutines.flow.*
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
                    // initial draft = persisted (if any). If none stored, default = both apps.
                    if (_draftSelected.value.isEmpty()) {
                        _draftSelected.value =
                            if (persisted.isNotEmpty()) persisted else setOf("Instagram", "TikTok")
                    }
                }
            }
        }
    }

    fun toggle(appName: String) {
        if (isSubmitted.value) return
        _draftSelected.value =
            if (_draftSelected.value.contains(appName)) _draftSelected.value - appName
            else _draftSelected.value + appName
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
}