package com.millane.thesis.application

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.android.libraries.places.api.Places
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.location.geofence.GeofenceManager
import com.millane.thesis.application.study.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ThesisApp : Application() {
    val sessionManager: SessionManager by lazy { SessionManager(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        if (BuildConfig.PLACES_API_KEY.isNotBlank() && !Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.PLACES_API_KEY)
        }
        restoreSubmittedGeofences()
    }

    private fun restoreSubmittedGeofences() {
        val hasFineLocation =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation) {
            Log.d("GEOFENCE", "Skipping startup geofence restore because fine location is not granted")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val locationsRepo = LocationsRepository(applicationContext)
            val submitted = locationsRepo.isSubmitted.first()
            val entries = locationsRepo.locations.first()
            if (!submitted || entries.isEmpty()) {
                Log.d("GEOFENCE", "Skipping startup geofence restore because no submitted locations exist")
                return@launch
            }

            GeofenceManager(applicationContext).registerAll(entries) { error ->
                Log.e("GEOFENCE", "Startup geofence restore failed", error)
            }
            Log.d("GEOFENCE", "Requested startup geofence restore for ${entries.size} locations")
        }
    }
}
