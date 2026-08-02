package com.millane.thesis.application.ui.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.millane.thesis.application.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double)

data class GeocodedPlace(
    val latLng: LatLng,
    val formattedAddress: String? = null,
    val placeId: String? = null
)

data class ResolvedAddress(
    val formatted: String,
    val street: String?,
    val houseNumber: String?,
    val postalCode: String?,
    val city: String?,
    val country: String?
)

/**
 * Forward geocoding: free-text -> location.
 *
 * Prefer Google Places for address lookup quality. Fall back to the Android geocoder if the
 * Places SDK is unavailable or returns nothing.
 */
suspend fun geocodeAddress(context: Context, query: String): GeocodedPlace? {
    geocodeWithPlaces(context, query)?.let { return it }
    return geocodeWithAndroidGeocoder(context, query)
}

private suspend fun geocodeWithPlaces(context: Context, query: String): GeocodedPlace? {
    if (BuildConfig.PLACES_API_KEY.isBlank()) return null
    if (!Places.isInitialized()) return null

    return runCatching {
        val placesClient = Places.createClient(context)
        val sessionToken = AutocompleteSessionToken.newInstance()
        var bestMatch: ScoredGeocodedPlace? = null

        for (variant in buildForwardGeocodeQueries(query)) {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(variant)
                .setCountries(listOf("DK"))
                .setSessionToken(sessionToken)
                .build()
            val response = placesClient.findAutocompletePredictions(request).awaitOrNull() ?: continue

            for (prediction in response.autocompletePredictions.take(5)) {
                val place = fetchPlaceDetails(placesClient, prediction.placeId) ?: continue
                val location = place.location ?: continue
                val formattedAddress = place.formattedAddress
                val score = scoreResolvedAddress(query, formattedAddress.orEmpty())
                if (score <= 0) continue

                val candidate = ScoredGeocodedPlace(
                    geocodedPlace = GeocodedPlace(
                        latLng = LatLng(location.latitude, location.longitude),
                        formattedAddress = formattedAddress,
                        placeId = place.id
                    ),
                    score = score
                )

                if (bestMatch == null || candidate.score > bestMatch.score) {
                    bestMatch = candidate
                }
            }
        }

        bestMatch?.geocodedPlace
    }.getOrNull()
}

private suspend fun fetchPlaceDetails(
    placesClient: PlacesClient,
    placeId: String
): Place? {
    val request = FetchPlaceRequest.newInstance(
        placeId,
        listOf(
            Place.Field.ID,
            Place.Field.LOCATION,
            Place.Field.FORMATTED_ADDRESS
        )
    )
    return placesClient.fetchPlace(request).awaitOrNull()?.place
}

private suspend fun geocodeWithAndroidGeocoder(
    context: Context,
    query: String
): GeocodedPlace? {
    val geocoder = Geocoder(context, Locale.getDefault())

    return try {
        var bestCandidate: ScoredGeocodedPlace? = null

        for (variant in buildForwardGeocodeQueries(query)) {
            val addresses = forwardGeocode(geocoder, variant, 5)
            for (address in addresses) {
                val latLng = LatLng(address.latitude, address.longitude)
                val resolved = reverseGeocodeInternal(geocoder, latLng)
                val formattedAddress = resolved?.let { buildDisplayAddress(it, latLng) }
                val score = scoreResolvedAddress(query, formattedAddress ?: address.getAddressLine(0).orEmpty())
                if (score <= 0) continue

                val candidate = ScoredGeocodedPlace(
                    geocodedPlace = GeocodedPlace(
                        latLng = latLng,
                        formattedAddress = formattedAddress,
                        placeId = null
                    ),
                    score = score
                )
                if (bestCandidate == null || candidate.score > bestCandidate.score) {
                    bestCandidate = candidate
                }
            }
        }

        bestCandidate?.geocodedPlace
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}

private suspend fun forwardGeocode(
    geocoder: Geocoder,
    query: String,
    maxResults: Int
): List<Address> {
    return if (Build.VERSION.SDK_INT >= 33) {
        suspendCancellableCoroutine { cont ->
            geocoder.getFromLocationName(query, maxResults) { list ->
                cont.resume(list ?: emptyList())
            }
        }
    } else {
        withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            geocoder.getFromLocationName(query, maxResults) ?: emptyList()
        }
    }
}

/**
 * Reverse geocoding: lat/lng -> structured address info
 */
suspend fun reverseGeocode(context: Context, latLng: LatLng): ResolvedAddress? {
    val geocoder = Geocoder(context, Locale.getDefault())

    return try {
        val a = reverseGeocodeInternal(geocoder, latLng) ?: return null

        val formatted = buildDisplayAddress(a, latLng)

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

private suspend fun reverseGeocodeInternal(
    geocoder: Geocoder,
    latLng: LatLng
): Address? {
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

    return results.firstOrNull()
}

private suspend fun <T> Task<T>.awaitOrNull(): T? =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.resume(null)
        }
    }

private fun buildDisplayAddress(a: Address, latLng: LatLng): String {
    val addressLine = a.getAddressLine(0)
    if (!addressLine.isNullOrBlank()) return addressLine

    val fallbackParts = listOfNotNull(
        a.featureName,
        a.subLocality,
        a.locality ?: a.subAdminArea,
        a.countryName
    )
        .distinct()
        .filter { it.isNotBlank() }

    if (fallbackParts.isNotEmpty()) {
        return fallbackParts.joinToString(", ")
    }

    return "${latLng.lat}, ${latLng.lng}"
}

private data class ScoredGeocodedPlace(
    val geocodedPlace: GeocodedPlace,
    val score: Int
)

private fun buildForwardGeocodeQueries(query: String): List<String> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return emptyList()

    val variants = linkedSetOf(trimmed)
    val hasCountry = trimmed.contains("denmark", ignoreCase = true) ||
        trimmed.contains("danmark", ignoreCase = true)
    if (!hasCountry) {
        variants += "$trimmed, Denmark"
    }

    val postalCode = extractPostalCode(trimmed)
    val cityFromPostalCode = postalCode?.let(::cityForDanishPostalCode)
    if (cityFromPostalCode != null && !trimmed.contains(cityFromPostalCode, ignoreCase = true)) {
        variants += "$trimmed, $cityFromPostalCode"
        variants += "$trimmed, $cityFromPostalCode, Denmark"
    }

    return variants.toList()
}

private fun scoreResolvedAddress(query: String, candidateText: String): Int {
    val queryTokens = extractMatchTokens(query)
    if (queryTokens.isEmpty()) return 0

    val candidateTokens = extractMatchTokens(candidateText)
    val matchedTokens = queryTokens.count { it in candidateTokens }
    val queryStreetTokens = queryTokens.filter { it.any(Char::isLetter) && it.length >= 4 }
    val streetMatchCount = queryStreetTokens.count { it in candidateTokens }
    val houseNumber = queryTokens.firstOrNull { it.length in 1..4 && it.all(Char::isDigit) }
    val postalCode = queryTokens.firstOrNull { it.length == 4 && it.all(Char::isDigit) }

    if (queryStreetTokens.isNotEmpty() && streetMatchCount == 0) return 0
    if (postalCode != null && postalCode !in candidateTokens) return 0
    if (houseNumber != null && houseNumber !in candidateTokens) return 0

    var score = matchedTokens + (streetMatchCount * 3)
    if (postalCode != null) score += 3
    if (houseNumber != null) score += 2
    return score
}

private fun extractPostalCode(query: String): String? =
    "\\b\\d{4}\\b".toRegex().find(query)?.value

private fun cityForDanishPostalCode(postalCode: String): String? = when (postalCode) {
    "1000", "1050", "1051", "1052", "1053", "1054", "1055", "1056", "1057", "1058",
    "1059", "1060", "1061", "1062", "1063", "1064", "1065", "1066", "1067", "1068",
    "1100", "1110", "1123", "1127", "1150", "1160", "1170", "1208", "1250", "1300",
    "1350", "1400", "1450", "1468", "1500", "1550", "1560", "1577", "1590", "1599" -> "Kobenhavn K"
    "1610", "1620", "1630", "1640", "1650", "1660", "1670", "1680", "1700", "1711",
    "1712", "1713", "1714", "1715", "1716", "1717", "1718", "1719", "1720", "1721",
    "1722", "1723", "1724", "1725", "1726", "1727", "1750", "1760", "1770", "1780",
    "1799", "1800", "1810", "1820", "1850", "1860", "1870", "1900", "1920", "1950",
    "1960", "1999" -> "Frederiksberg C"
    "2000" -> "Frederiksberg"
    "2100" -> "Kobenhavn O"
    "2200" -> "Kobenhavn N"
    "2300" -> "Kobenhavn S"
    "2400" -> "Kobenhavn NV"
    "2450" -> "Kobenhavn SV"
    "2500" -> "Valby"
    "2600" -> "Glostrup"
    "2610" -> "Rodovre"
    "2620" -> "Albertslund"
    "2630" -> "Taastrup"
    "2650" -> "Hvidovre"
    "2700" -> "Bronshoj"
    "2720" -> "Vanlose"
    "2730" -> "Herlev"
    "2740" -> "Skovlunde"
    "2800" -> "Kongens Lyngby"
    "2820" -> "Gentofte"
    "2860" -> "Soborg"
    "2900" -> "Hellerup"
    else -> null
}

private fun extractMatchTokens(value: String): Set<String> =
    normalizeForMatch(value)
        .split(" ")
        .filter { it.isNotBlank() }
        .toSet()

private fun normalizeForMatch(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
}
