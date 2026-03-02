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

data class ResolvedAddress(
    val formatted: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    val country: String?
)

/**
 * Forward geocoding: free-text -> lat/lng
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

/**
 * Reverse geocoding: lat/lng -> structured postal-ish address info.
 */
suspend fun reverseGeocode(context: Context, latLng: LatLng): ResolvedAddress? {
    val geocoder = Geocoder(context, Locale.getDefault())

    return try {
        val results: List<Address> = if (Build.VERSION.SDK_INT >= 33) {
            suspendCancellableCoroutine { cont ->
                geocoder.getFromLocation(latLng.lat, latLng.lng, 1) { list ->
                    cont.resume(list ?: emptyList())
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latLng.lat, latLng.lng, 1) ?: emptyList()
            }
        }

        val a = results.firstOrNull() ?: return null

        val formatted = a.getAddressLine(0)
            ?: listOfNotNull(
                a.thoroughfare,
                a.subThoroughfare,
                a.postalCode,
                a.locality ?: a.subAdminArea,
                a.countryName
            ).joinToString(", ")

        ResolvedAddress(
            formatted = formatted,
            street = a.thoroughfare,
            houseNumber = a.subThoroughfare,
            postalCode = a.postalCode,
            city = a.locality ?: a.subAdminArea,
            country = a.countryName
        )
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Heuristic validator: accept only "postal-ish" results.
 * Rule: accept if at least 2 out of 3 are present:
 *  - city
 *  - postal code
 *  - street
 */
fun looksLikePostalAddress(r: ResolvedAddress): Boolean {
    val hasCity = !r.city.isNullOrBlank()
    val hasPostal = !r.postalCode.isNullOrBlank()
    val hasStreet = !r.street.isNullOrBlank()

    val score = listOf(hasCity, hasPostal, hasStreet).count { it }
    return score >= 2
}