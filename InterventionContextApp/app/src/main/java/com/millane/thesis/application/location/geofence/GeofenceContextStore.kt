package com.millane.thesis.application.location.geofence

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Minimal: keeps "current active geofence IDs".
 * Later: persist in DataStore/Room, and map IDs -> WORK/HOME.
 */
object GeofenceContextStore {
    private val _activeGeofenceIds = MutableStateFlow<Set<String>>(emptySet())
    val activeGeofenceIds: StateFlow<Set<String>> = _activeGeofenceIds

    fun onEnter(context: Context, ids: List<String>) {
        _activeGeofenceIds.value = _activeGeofenceIds.value + ids
    }

    fun onExit(context: Context, ids: List<String>) {
        _activeGeofenceIds.value = _activeGeofenceIds.value - ids.toSet()
    }
}