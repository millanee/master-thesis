package com.millane.thesis.application.data.location

import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class LocationsRepository {

    private val _locations = MutableStateFlow<List<LocationEntry>>(emptyList())
    val locations: StateFlow<List<LocationEntry>> = _locations

    fun getByType(type: LocationContextType): List<LocationEntry> =
        _locations.value.filter { it.contextType == type }

    fun add(entry: LocationEntry) {
        _locations.update { it + entry }
    }

    fun delete(id: String) {
        _locations.update { list -> list.filterNot { it.id == id } }
    }

    fun clearByType(type: LocationContextType) {
        _locations.update { list -> list.filterNot { it.contextType == type } }
    }
}