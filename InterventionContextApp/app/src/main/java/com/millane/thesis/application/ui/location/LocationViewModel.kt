package com.millane.thesis.application.ui.location

import androidx.lifecycle.ViewModel
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import kotlinx.coroutines.flow.StateFlow

class LocationsViewModel(
    private val repo: LocationsRepository = LocationsRepository()
) : ViewModel() {

    val locations: StateFlow<List<LocationEntry>> = repo.locations

    fun workLocations(): List<LocationEntry> = repo.getByType(LocationContextType.WORK)
    fun homeLocations(): List<LocationEntry> = repo.getByType(LocationContextType.HOME)

    fun add(entry: LocationEntry) = repo.add(entry)
    fun delete(id: String) = repo.delete(id)
}