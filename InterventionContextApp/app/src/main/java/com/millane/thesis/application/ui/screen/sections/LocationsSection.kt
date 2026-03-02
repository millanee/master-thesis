package com.millane.thesis.application.ui.screen.sections

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import com.millane.thesis.application.location.geofence.GeofenceManager
import com.millane.thesis.application.ui.location.LocationsViewModel
import com.millane.thesis.application.ui.location.geocodeAddress
import kotlinx.coroutines.launch

@Composable
fun LocationsSection(
    vm: LocationsViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allLocations by vm.locations.collectAsState()
    val workLocations = allLocations.filter { it.contextType == LocationContextType.WORK }
    val homeLocations = allLocations.filter { it.contextType == LocationContextType.HOME }

    var workInput by remember { mutableStateOf("") }
    var homeInput by remember { mutableStateOf("") }

    var helperText by remember { mutableStateOf<String?>(null) }

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
            "Location permission is needed to detect WORK/HOME automatically (geofencing). You can still add addresses."
        } else null
    }

    val geofenceManager = remember { GeofenceManager(context) }

    LaunchedEffect(hasFineLocation, allLocations) {
        if (hasFineLocation) {
            geofenceManager.registerAll(allLocations)
        }
    }

    fun addLocation(type: LocationContextType, raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return

        helperText = "Searching address…"

        scope.launch {
            val latLng = geocodeAddress(context, trimmed)
            if (latLng == null) {
                helperText = "Could not find this address. Try adding city/zip code."
                return@launch
            }

            vm.add(
                LocationEntry(
                    contextType = type,
                    displayAddress = trimmed,
                    latitude = latLng.lat,
                    longitude = latLng.lng,
                    radiusMeters = if (type == LocationContextType.WORK) 150f else 120f,
                    placeId = null
                )
            )

            helperText = null
            if (type == LocationContextType.WORK) workInput = "" else homeInput = ""
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
        onDelete = { id -> vm.delete(id) },
        onSubmit = {
            if (!canSubmit) {
                helperText = "Please add at least one WORK and one HOME address."
                return@LocationsCard
            }
            // Optional: ask for permission here if you want geofencing to work
            if (!hasFineLocation) {
                requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                helperText = "Grant location permission so the app can detect when you're at WORK/HOME."
            } else {
                helperText = "Submitted. (Next step: show confirmation dialog + persist to DB)"
            }
        },
        submitEnabled = canSubmit,
        helperText = helperText
    )
}