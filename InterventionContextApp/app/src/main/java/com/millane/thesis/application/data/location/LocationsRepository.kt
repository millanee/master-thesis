package com.millane.thesis.application.data.location

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.millane.thesis.application.data.datastore.JsonCodec
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationsRepository(
    private val context: Context
) {
    private val KEY_LOCATIONS_JSON = stringPreferencesKey("locations_json")

    val locations: Flow<List<LocationEntry>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[KEY_LOCATIONS_JSON] ?: "[]"
            runCatching { JsonCodec.decode<List<LocationEntry>>(raw) }
                .getOrElse { emptyList() }
        }

    suspend fun add(entry: LocationEntry) {
        context.appDataStore.edit { prefs ->
            val current = readLocations(prefs[KEY_LOCATIONS_JSON])
            prefs[KEY_LOCATIONS_JSON] = JsonCodec.encode(current + entry)
        }
    }

    suspend fun delete(id: String) {
        context.appDataStore.edit { prefs ->
            val current = readLocations(prefs[KEY_LOCATIONS_JSON])
            prefs[KEY_LOCATIONS_JSON] = JsonCodec.encode(current.filterNot { it.id == id })
        }
    }

    suspend fun clearByType(type: LocationContextType) {
        context.appDataStore.edit { prefs ->
            val current = readLocations(prefs[KEY_LOCATIONS_JSON])
            prefs[KEY_LOCATIONS_JSON] = JsonCodec.encode(current.filterNot { it.contextType == type })
        }
    }

    private fun readLocations(raw: String?): List<LocationEntry> {
        val safe = raw ?: "[]"
        return runCatching { JsonCodec.decode<List<LocationEntry>>(safe) }
            .getOrElse { emptyList() }
    }
}