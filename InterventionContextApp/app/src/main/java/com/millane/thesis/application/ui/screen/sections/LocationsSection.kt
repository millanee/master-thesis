package com.millane.thesis.application.ui.screen.sections

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.data.datastore.DevDataStoreReset
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import com.millane.thesis.application.location.geofence.GeofenceManager
import com.millane.thesis.application.ui.viewmodels.LocationsViewModel
import com.millane.thesis.application.ui.location.geocodeAddress
import com.millane.thesis.application.ui.location.looksLikePostalAddress
import com.millane.thesis.application.ui.location.reverseGeocode
import kotlinx.coroutines.launch
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog

@Composable
fun LocationsSection(
    vm: LocationsViewModel = viewModel()
) {
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

    // Permission (geofencing)
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
        } else null
    }

    val geofenceManager = remember { GeofenceManager(context) }

    // Register geofences ONLY after submit
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

        helperText = "Searching address…"

        scope.launch {
            val latLng = geocodeAddress(context, trimmed)
            if (latLng == null) {
                helperText = "Could not find this address. Try adding city/zip code."
                return@launch
            }

            val resolved = reverseGeocode(context, latLng)
            if (resolved == null) {
                helperText = "Could not verify the address. Please try a more precise input."
                return@launch
            }

            if (!looksLikePostalAddress(resolved)) {
                helperText =
                    "Please enter a real address (Street + City or Postal Code). Detected: ${resolved.formatted}"
                return@launch
            }

            vm.addDraft(
                LocationEntry(
                    contextType = type,
                    displayAddress = resolved.formatted,
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

    // Debuggable check
    val isDebuggable =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    if (isDebuggable) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        // clears ALL DataStore keys (now + future features)
                        DevDataStoreReset.clearAll(context)

                        // reset in-memory draft state
                        vm.resetForTesting()

                        // remove any registered geofences
                        geofenceManager.clearAll()

                        helperText = "DEV: cleared all stored data."
                    }
                }
            ) {
                Text("DEV: Reset all data")
            }
        }
        Spacer(Modifier.height(12.dp))
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
                helperText = "Please add at least one WORK and one HOME address."
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