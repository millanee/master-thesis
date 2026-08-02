package com.millane.thesis.application.location.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val transition = event.geofenceTransition
        val triggeringIds = event.triggeringGeofences?.map { it.requestId }.orEmpty()

        // TODO: forward to your repository / store:
        // - if ENTER: mark these IDs as active
        // - if EXIT: remove
        // Usually call a small singleton or enqueue work.
        when (transition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                GeofenceContextStore.onEnter(context, triggeringIds)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                GeofenceContextStore.onExit(context, triggeringIds)
            }
        }
    }
}