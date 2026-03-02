package com.millane.thesis.application.ui.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double)

/**
 * Converts a free-text address into coordinates (lat/lng).
 * Returns null if nothing found or geocoder fails.
 */
suspend fun geocodeAddress(context: Context, query: String): LatLng? {
    val geocoder = Geocoder(context, Locale.getDefault())

    return try {
        val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocationName(query, 1) { list ->
                    cont.resume(list ?: emptyList())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1) ?: emptyList()
            }
        }

        val a = results.firstOrNull() ?: return null
        LatLng(a.latitude, a.longitude)
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}