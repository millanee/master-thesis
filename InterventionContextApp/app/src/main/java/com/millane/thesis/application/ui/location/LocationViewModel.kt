package com.millane.thesis.application.ui.location

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.domain.location.LocationEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocationsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = LocationsRepository(app.applicationContext)

    val locations: StateFlow<List<LocationEntry>> =
        repo.locations.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun add(entry: LocationEntry) {
        viewModelScope.launch {
            repo.add(entry)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repo.delete(id)
        }
    }
}