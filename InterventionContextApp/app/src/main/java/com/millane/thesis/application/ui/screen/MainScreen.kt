package com.millane.thesis.application.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.ui.screen.sections.BedtimeCard
import com.millane.thesis.application.ui.screen.sections.DailyGoalsCard
import com.millane.thesis.application.ui.screen.sections.LocationsSection
import com.millane.thesis.application.ui.screen.sections.SelectAppsCard
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import com.millane.thesis.application.ui.theme.PageBackground
import com.millane.thesis.application.ui.viewmodels.AppSelectionViewModel
import com.millane.thesis.application.ui.viewmodels.BedtimeViewModel
import com.millane.thesis.application.ui.viewmodels.LocationsViewModel
import com.millane.thesis.application.ui.viewmodels.DailyGoalsViewModel
import com.millane.thesis.application.ui.viewmodels.StudyStatus
import com.millane.thesis.application.ui.viewmodels.StudyViewModel

@Composable
fun MainScreen() {
    val pageBackground = PageBackground
    val scrollState = rememberScrollState()

    val locationsVm: LocationsViewModel = viewModel()
    val bedtimeVm: BedtimeViewModel = viewModel()
    val appsVm: AppSelectionViewModel = viewModel()

    val dailyGoalsVm: DailyGoalsViewModel = viewModel()
    val goals by dailyGoalsVm.goals.collectAsState()

    val studyVm: StudyViewModel = viewModel()
    val study by studyVm.snapshot.collectAsState()
    val bedtimeSubmitted by bedtimeVm.isSubmitted.collectAsState()
    val locationsSubmitted by locationsVm.isSubmitted.collectAsState()
    val appsSubmitted by appsVm.isSubmitted.collectAsState()
    val studyDayNumber = run {
        val start = study.startDateMs
        if (start == null || study.status == StudyStatus.NOT_STARTED) {
            null
        } else {
            (((System.currentTimeMillis() - start).coerceAtLeast(0L)) / (24L * 60 * 60 * 1000)).toInt() + 1
        }
    }

    LaunchedEffect(Unit) {
        studyVm.initIfMissing()
    }
    // When onboarding becomes complete (apps, locations, bedtime), set study start date and assign group.
    LaunchedEffect(locationsSubmitted, appsSubmitted, bedtimeSubmitted) {
        if (locationsSubmitted && appsSubmitted && bedtimeSubmitted) {
            studyVm.initIfMissing()
        }
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

        // Text("Study participant: ${study.participantId ?: "-"}")
        // Text("Group: ${study.group ?: "-"}")
        Text("Study day: ${studyDayNumber ?: "-"}")
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
        Spacer(Modifier.height(20.dp))
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
