package com.millane.thesis.application.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.data.datastore.DevDataStoreDump
import com.millane.thesis.application.data.datastore.DevDataStoreReset
import com.millane.thesis.application.ui.screen.sections.BedtimeCard
import com.millane.thesis.application.ui.screen.sections.DailyGoalsCard
import com.millane.thesis.application.ui.screen.sections.LocationsSection
import com.millane.thesis.application.ui.screen.sections.SelectAppsCard
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import com.millane.thesis.application.ui.theme.PageBackground
import com.millane.thesis.application.ui.viewmodels.AppSelectionViewModel
import com.millane.thesis.application.ui.viewmodels.BedtimeViewModel
import com.millane.thesis.application.ui.viewmodels.LocationsViewModel
import kotlinx.coroutines.launch
import com.millane.thesis.application.ui.viewmodels.DailyGoalsViewModel
import com.millane.thesis.application.ui.viewmodels.StudyViewModel

@Composable
fun MainScreen() {
    val pageBackground = PageBackground
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showDumpDialog by remember { mutableStateOf(false) }
    var dumpText by remember { mutableStateOf("") }

    val locationsVm: LocationsViewModel = viewModel()
    val bedtimeVm: BedtimeViewModel = viewModel()
    val appsVm: AppSelectionViewModel = viewModel()

    val dailyGoalsVm: DailyGoalsViewModel = viewModel()
    val goals by dailyGoalsVm.goals.collectAsState()

    val studyVm: StudyViewModel = viewModel()
    val study by studyVm.snapshot.collectAsState()
    val bedtimeSubmitted by bedtimeVm.isSubmitted.collectAsState()

    LaunchedEffect(Unit) {
        studyVm.initIfMissing()
    }
    // When user completes last onboarding step (bedtime), set study start date and assign group
    LaunchedEffect(bedtimeSubmitted) {
        if (bedtimeSubmitted) studyVm.initIfMissing()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        dumpText = DevDataStoreDump.dump(context)
                        showDumpDialog = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("DEV: SHOW DATA")
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        locationsVm.resetForTesting()
                        bedtimeVm.resetForTesting()
                        appsVm.resetForTesting()

                        dumpText = "(datastore cleared)"
                        showDumpDialog = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("DEV: RESET") }
        }

        Text("Study participant: ${study.participantId ?: "-"}")
        Text("Group: ${study.group ?: "-"}")
        Text("Week: ${study.weekIndex ?: "-"}")
        Text("Active: ${study.activeIntervention ?: "-"}")

        // Daily Goals - Goal Advancement
        DailyGoalsCard(
            goals = goals,
            onAddGoal = { dailyGoalsVm.addGoal(it) },
            onDeleteGoal = { dailyGoalsVm.deleteGoal(it) }
        )
        LocationsSection()
        BedtimeCard()
        SelectAppsCard()
    }

    if (showDumpDialog) {
        AlertDialog(
            onDismissRequest = { showDumpDialog = false },
            confirmButton = {
                TextButton(onClick = { showDumpDialog = false }) { Text("OK") }
            },
            title = { Text("DataStore Dump (DEV)") },
            text = {
                // Scrollable dump
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(dumpText)
                }
            }
        )
    }
}

@Preview(name = "Small phone", widthDp = 320, heightDp = 800, showBackground = true)
@Composable
private fun PreviewSmallPhone() {
    InterventionContextAppTheme { MainScreen() }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun MainScreenPreview() {
    MaterialTheme {
        MainScreen()
    }
}