package com.millane.thesis.application.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

private const val DATASTORE_NAME = "app_prefs"

// one datastore for the whole app (locations, daily goals, etc.)
val Context.appDataStore by preferencesDataStore(name = DATASTORE_NAME)