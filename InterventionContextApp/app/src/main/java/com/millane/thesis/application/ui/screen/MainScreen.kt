package com.millane.thesis.application.ui.screen

import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.millane.thesis.application.ui.screen.sections.BedtimeCard
import com.millane.thesis.application.ui.screen.sections.DailyGoalsCard
import com.millane.thesis.application.ui.screen.sections.LocationsCard
import com.millane.thesis.application.ui.screen.sections.SelectAppsCard
import com.millane.thesis.application.ui.theme.*

@Composable
fun MainScreen() {
    val pageBackground = PageBackground
    val scrollState = rememberScrollState()

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
        // Daily Goals - Goal Advancement
        DailyGoalsCard()
        LocationsCard()
        BedtimeCard()
        // Select apps
        SelectAppsCard()
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
