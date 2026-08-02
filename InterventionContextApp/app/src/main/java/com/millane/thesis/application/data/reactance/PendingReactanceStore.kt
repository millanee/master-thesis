package com.millane.thesis.application.data.reactance

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.millane.thesis.application.data.datastore.JsonCodec
import com.millane.thesis.application.data.datastore.appDataStore
import kotlinx.coroutines.flow.first

/**
 * Stores reactance responses locally when the user submits from an intervention
 * (e.g. "Go home"). The data is sent to Firebase when the user confirms in the
 * context validation dialog, ensuring it is never lost due to activity destruction
 * or async timing.
 */
class PendingReactanceStore(private val context: Context) {

    private object Keys {
        val SESSION_ID = stringPreferencesKey("pending_reactance_session_id")
        val RESPONSES_JSON = stringPreferencesKey("pending_reactance_responses_json")
    }

    suspend fun store(sessionId: String, responses: List<Int>) {
        context.appDataStore.edit { prefs ->
            prefs[Keys.SESSION_ID] = sessionId
            prefs[Keys.RESPONSES_JSON] = JsonCodec.encode(responses)
        }
    }

    suspend fun takeForSession(sessionId: String): List<Int>? {
        val prefs = context.appDataStore.data.first()
        val storedSessionId = prefs[Keys.SESSION_ID] ?: return null
        if (storedSessionId != sessionId) return null

        val raw = prefs[Keys.RESPONSES_JSON] ?: return null
        val responses = runCatching { JsonCodec.decode<List<Int>>(raw) }.getOrNull() ?: return null

        // Clear after reading
        context.appDataStore.edit { prefs ->
            prefs.remove(Keys.SESSION_ID)
            prefs.remove(Keys.RESPONSES_JSON)
        }
        return responses
    }
}
