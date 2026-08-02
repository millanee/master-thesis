package com.millane.thesis.application.data.location

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.millane.thesis.application.data.datastore.JsonCodec
import com.millane.thesis.application.data.datastore.appDataStore
import com.millane.thesis.application.domain.location.LocationEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationsRepository(
    private val context: Context
) {
    private val KEY_LOCATIONS_JSON = stringPreferencesKey("locations_json")
    private val KEY_LOCATIONS_SUBMITTED = booleanPreferencesKey("locations_submitted")


     // Persisted locations (only written on SUBMIT).
    val locations: Flow<List<LocationEntry>> =
        context.appDataStore.data.map { prefs ->
            val raw = prefs[KEY_LOCATIONS_JSON] ?: "[]"
            runCatching { JsonCodec.decode<List<LocationEntry>>(raw) }
                .getOrElse { emptyList() }
        }

     // Whether user already pressed SUBMIT (persisted).
    val isSubmitted: Flow<Boolean> =
        context.appDataStore.data.map { prefs ->
            prefs[KEY_LOCATIONS_SUBMITTED] ?: false
        }

     // Save the full list in one shot (called on SUBMIT)
    suspend fun saveAll(entries: List<LocationEntry>) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_LOCATIONS_JSON] = JsonCodec.encode(entries)
        }
    }

    suspend fun setSubmitted(submitted: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[KEY_LOCATIONS_SUBMITTED] = submitted
        }
    }

    suspend fun clearAll() {
        context.appDataStore.edit { prefs ->
            prefs[KEY_LOCATIONS_JSON] = "[]"
            prefs[KEY_LOCATIONS_SUBMITTED] = false
        }
    }
}