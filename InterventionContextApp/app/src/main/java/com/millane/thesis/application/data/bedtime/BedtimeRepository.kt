package com.millane.thesis.application.data.bedtime

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.millane.thesis.application.data.datastore.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BedtimeRepository(private val context: Context) {

    private object Keys {
        val BEDTIME_TIME = stringPreferencesKey("bedtime_time") // "HH:mm"
        val BEDTIME_SUBMITTED = booleanPreferencesKey("bedtime_submitted")
    }

    val bedtime: Flow<String?> =
        context.appDataStore.data.map { prefs -> prefs[Keys.BEDTIME_TIME] }

    val isSubmitted: Flow<Boolean> =
        context.appDataStore.data.map { prefs -> prefs[Keys.BEDTIME_SUBMITTED] ?: false }

    suspend fun saveBedtime(time: String) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.BEDTIME_TIME] = time
        }
    }

    suspend fun setSubmitted(submitted: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.BEDTIME_SUBMITTED] = submitted
        }
    }
}