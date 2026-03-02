package com.millane.thesis.application.data.apps

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.millane.thesis.application.data.datastore.appDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppSelectionRepository(private val context: Context) {

    private object Keys {
        val SELECTED_APPS = stringSetPreferencesKey("selected_apps") // e.g. {"Instagram","TikTok"}
        val APPS_SUBMITTED = booleanPreferencesKey("apps_submitted")
    }

    val selectedApps: Flow<Set<String>> =
        context.appDataStore.data.map { prefs -> prefs[Keys.SELECTED_APPS] ?: emptySet() }

    val isSubmitted: Flow<Boolean> =
        context.appDataStore.data.map { prefs -> prefs[Keys.APPS_SUBMITTED] ?: false }

    suspend fun saveSelectedApps(apps: Set<String>) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.SELECTED_APPS] = apps
        }
    }

    suspend fun setSubmitted(submitted: Boolean) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.APPS_SUBMITTED] = submitted
        }
    }
}