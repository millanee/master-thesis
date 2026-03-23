package com.millane.thesis.application.ui.screen.sections

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import com.millane.thesis.application.location.geofence.GeofenceManager
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.location.geocodeAddress
import com.millane.thesis.application.ui.location.reverseGeocode
import com.millane.thesis.application.ui.viewmodels.LocationsViewModel
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun LocationsSection(
    vm: LocationsViewModel = viewModel()
) {
    val minHomeWorkDistanceMeters = 270.0
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val submitted by vm.isSubmitted.collectAsState()
    val draftLocations by vm.draftLocations.collectAsState()

    val workLocations = draftLocations.filter { it.contextType == LocationContextType.WORK }
    val homeLocations = draftLocations.filter { it.contextType == LocationContextType.HOME }

    var workInput by remember { mutableStateOf("") }
    var homeInput by remember { mutableStateOf("") }
    var helperText by remember { mutableStateOf<String?>(null) }
    var showSubmitConfirm by remember { mutableStateOf(false) }

    var hasFineLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val requestFineLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasFineLocation = granted
        helperText = if (!granted) {
            "Location permission is needed to detect WORK/HOME automatically (geofencing)."
        } else {
            null
        }
    }

    val geofenceManager = remember { GeofenceManager(context) }

    LaunchedEffect(submitted, hasFineLocation, draftLocations) {
        if (submitted && hasFineLocation) {
            geofenceManager.registerAll(draftLocations)
        }
    }

    LaunchedEffect(submitted) {
        if (submitted) showSubmitConfirm = false
    }

    fun addLocation(type: LocationContextType, raw: String) {
        if (submitted) return

        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return

        helperText = "Searching location…"

        scope.launch {
            val latLng = geocodeAddress(context, trimmed)
            if (latLng == null) {
                helperText = "Could not find this location. Try a more specific input."
                return@launch
            }

            val resolved = reverseGeocode(context, latLng.latLng)
            val displayAddress = latLng.formattedAddress ?: resolved?.formatted ?: trimmed

            val normalizedNewAddress = displayAddress.trim().lowercase()
            val conflictingLocation = draftLocations.firstOrNull { existing ->
                existing.contextType != type &&
                    (
                        (existing.contextType == LocationContextType.HOME && type == LocationContextType.WORK) ||
                            (existing.contextType == LocationContextType.WORK && type == LocationContextType.HOME)
                        ) &&
                    existing.displayAddress.trim().lowercase() == normalizedNewAddress
            }

            if (conflictingLocation != null) {
                helperText = "This address is already set for ${conflictingLocation.contextType.name.lowercase()}. Choose a different address."
                return@launch
            }

            val tooCloseLocation = draftLocations.firstOrNull { existing ->
                existing.contextType != type &&
                    (
                        (existing.contextType == LocationContextType.HOME && type == LocationContextType.WORK) ||
                            (existing.contextType == LocationContextType.WORK && type == LocationContextType.HOME)
                        ) &&
                    distanceMeters(
                        lat1 = existing.latitude,
                        lon1 = existing.longitude,
                        lat2 = latLng.latLng.lat,
                        lon2 = latLng.latLng.lng
                    ) < minHomeWorkDistanceMeters
            }

            if (tooCloseLocation != null) {
                helperText = "Home and work locations must be at least 270 meters apart."
                return@launch
            }

            vm.addDraft(
                LocationEntry(
                    contextType = type,
                    displayAddress = displayAddress,
                    latitude = latLng.latLng.lat,
                    longitude = latLng.latLng.lng,
                    radiusMeters = if (type == LocationContextType.WORK) 150f else 120f,
                    placeId = latLng.placeId
                )
            )

            helperText = null

            if (type == LocationContextType.WORK) {
                workInput = ""
            } else {
                homeInput = ""
            }
        }
    }

    val canSubmit = workLocations.isNotEmpty() && homeLocations.isNotEmpty()

    LocationsCard(
        workLocations = workLocations,
        homeLocations = homeLocations,
        workInput = workInput,
        homeInput = homeInput,
        onWorkInputChange = { workInput = it },
        onHomeInputChange = { homeInput = it },
        onAddWork = { addLocation(LocationContextType.WORK, workInput) },
        onAddHome = { addLocation(LocationContextType.HOME, homeInput) },
        onDelete = { id -> vm.deleteDraft(id) },
        onSubmit = {
            if (submitted) return@LocationsCard

            if (!canSubmit) {
                helperText = "Please add at least one WORK and one HOME location."
                return@LocationsCard
            }

            if (!hasFineLocation) {
                requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                helperText = "Grant location permission so the app can detect when you're at WORK/HOME."
                return@LocationsCard
            }

            helperText = null
            showSubmitConfirm = true
        },
        submitEnabled = canSubmit,
        submitted = submitted,
        helperText = helperText
    )

    val confirmationBullets = buildList {
        if (workLocations.isNotEmpty()) {
            add("WORK:")
            workLocations.forEach { add(it.displayAddress) }
        }

        if (homeLocations.isNotEmpty()) {
            add("HOME:")
            homeLocations.forEach { add(it.displayAddress) }
        }
    }

    if (showSubmitConfirm) {
        ConfirmationAndInterventionDialog(
            title = "Confirm locations",
            message = "Do you want to submit these locations? After confirming, you can’t edit them anymore.",
            bullets = confirmationBullets,
            confirmLabel = "CONFIRM",
            dismissLabel = "CANCEL",
            onConfirm = {
                showSubmitConfirm = false
                vm.submit()
                helperText = "Submitted."
            },
            onDismiss = {
                showSubmitConfirm = false
            }
        )
    }
}

private fun distanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1Rad = Math.toRadians(lat1)
    val lat2Rad = Math.toRadians(lat2)
    val deltaLatRad = Math.toRadians(lat2 - lat1)
    val deltaLonRad = Math.toRadians(lon2 - lon1)

    val a = sin(deltaLatRad / 2).pow(2) +
        cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
    val c = 2 * asin(sqrt(a))
    return earthRadiusMeters * c
}
