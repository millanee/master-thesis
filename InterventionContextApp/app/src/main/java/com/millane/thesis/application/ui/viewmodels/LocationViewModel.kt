package com.millane.thesis.application.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.datastore.DevDataStoreReset
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.domain.location.LocationEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationsViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val repo = LocationsRepository(appContext)

    // persisted
    private val persistedLocations: StateFlow<List<LocationEntry>> =
        repo.locations.stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), emptyList())

    val isSubmitted: StateFlow<Boolean> =
        repo.isSubmitted.stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5_000), false)

    // draft (not persisted until submit)
    private val _draftLocations = MutableStateFlow<List<LocationEntry>>(emptyList())
    val draftLocations: StateFlow<List<LocationEntry>> = _draftLocations.asStateFlow()

    init {
        // When submitted becomes true, draft should reflect persisted (locked state).
        viewModelScope.launch {
            combine(persistedLocations, isSubmitted) { locations, submitted ->
                Pair(locations, submitted)
            }.collect { (locations, submitted) ->
                if (submitted) {
                    _draftLocations.value = locations
                } else {
                    // while not submitted, keep current draft as-is
                    if (_draftLocations.value.isEmpty()) {
                        _draftLocations.value = emptyList()
                    }
                }
            }
        }
    }

    fun addDraft(entry: LocationEntry) {
        if (isSubmitted.value) return
        _draftLocations.value = _draftLocations.value + entry
    }

    fun deleteDraft(id: String) {
        if (isSubmitted.value) return
        _draftLocations.value = _draftLocations.value.filterNot { it.id == id }
    }

    fun submit() {
        if (isSubmitted.value) return
        val toSave = _draftLocations.value
        viewModelScope.launch {
            repo.saveAll(toSave)
            repo.setSubmitted(true)
        }
    }

    /**
     * DEV ONLY:
     * Clears the ENTIRE app datastore (locations + any future stored data),
     * and resets draft state.
     */
    fun resetForTesting() {
        viewModelScope.launch {
            DevDataStoreReset.clearAll(appContext)
            _draftLocations.value = emptyList()
        }
    }
}