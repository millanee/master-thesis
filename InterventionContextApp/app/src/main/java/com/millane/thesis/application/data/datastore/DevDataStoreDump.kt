package com.millane.thesis.application.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.first

object DevDataStoreDump {

    /**
     * Reads current datastore snapshot and returns a human-readable dump.
     * Works for all Preferences value types.
     */
    suspend fun dump(context: Context): String {
        val prefs = context.appDataStore.data.first()

        if (prefs.asMap().isEmpty()) return "(datastore is empty)"

        // Sort keys for stable output
        val lines = prefs.asMap()
            .toList()
            .sortedBy { (key, _) -> key.name }
            .map { (key, value) -> "${key.name} = ${formatValue(value)}" }

        return lines.joinToString(separator = "\n")
    }

    private fun formatValue(v: Any?): String {
        return when (v) {
            null -> "null"
            is Set<*> -> v.joinToString(prefix = "[", postfix = "]")
            else -> v.toString()
        }
    }
}