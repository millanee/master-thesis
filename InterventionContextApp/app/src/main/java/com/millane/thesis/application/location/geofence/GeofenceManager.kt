package com.millane.thesis.application.location.geofence

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.millane.thesis.application.domain.location.LocationEntry

class GeofenceManager(
    private val context: Context
) {
    private val client: GeofencingClient = LocationServices.getGeofencingClient(context)

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    private fun toGeofence(entry: LocationEntry): Geofence {
        return Geofence.Builder()
            .setRequestId(entry.id)
            .setCircularRegion(entry.latitude, entry.longitude, entry.radiusMeters)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
    }

    fun registerAll(entries: List<LocationEntry>, onError: (Exception) -> Unit = {}) {
        if (entries.isEmpty()) {
            clearAll(onError)
            return
        }

        try {
            client.removeGeofences(pendingIntent())
                .addOnCompleteListener {
                    val geofences = entries.map { toGeofence(it) }

                    val request = GeofencingRequest.Builder()
                        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                        .addGeofences(geofences)
                        .build()

                    try {
                        client.addGeofences(request, pendingIntent())
                            .addOnFailureListener { e -> onError(e) }
                    } catch (se: SecurityException) {
                        onError(se)
                    }
                }
                .addOnFailureListener { e -> onError(e) }
        } catch (se: SecurityException) {
            onError(se)
        }
    }

    fun clearAll(onError: (Exception) -> Unit = {}) {
        client.removeGeofences(pendingIntent())
            .addOnFailureListener { e -> onError(e) }
    }
}