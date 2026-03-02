package com.millane.thesis.application.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit

// only DEV helper to reset data store
object DevDataStoreReset {
    suspend fun clearAll(context: Context) {
        context.appDataStore.edit { prefs ->
            prefs.clear()
        }
    }
}