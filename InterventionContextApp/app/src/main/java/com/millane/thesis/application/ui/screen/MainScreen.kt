package com.millane.thesis.application.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.millane.thesis.application.R
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.screen.sections.DailyGoalsCard

import com.millane.thesis.application.ui.theme.*

private enum class TargetApp { Instagram, TikTok }

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (contains(item)) this - item else this + item

@Composable
fun MainScreen() {
    val pageBackground = PageBackground
    val cardBackground = CardBackground

    val scrollState = rememberScrollState()
    var selectedApps by remember { mutableStateOf(setOf(TargetApp.Instagram, TargetApp.TikTok)) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    var goalInput by remember { mutableStateOf("") }
    var goals by remember { mutableStateOf(listOf("Create Figma Design", "Read Book")) } // demo defaults
    val focusManager = LocalFocusManager.current

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

        Spacer(Modifier.height(18.dp))

        // Select apps
        RoundedCard(
            background = cardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 340.dp)
        ) {
            Text(
                text = "Select Apps",
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Select the app(s) you want to intervene.\nYou can only select and submit them once.\nIf you do not submit your choice on the day before the study starts, both apps are targeted.",
                fontSize = 14.sp,
                color = SecondaryText
            )
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                AppChoice(
                    label = "Instagram",
                    iconRes = R.drawable.instagram_icon,
                    selected = selectedApps.contains(TargetApp.Instagram),
                    enabled = !submitted,
                    onToggle = { selectedApps = selectedApps.toggle(TargetApp.Instagram) }
                )

                AppChoice(
                    label = "TikTok",
                    iconRes = R.drawable.tiktok_icon,
                    selected = selectedApps.contains(TargetApp.TikTok),
                    enabled = !submitted,
                    onToggle = { selectedApps = selectedApps.toggle(TargetApp.TikTok) }
                )
            }
            Spacer(Modifier.height(25.dp))

            val canSubmit = selectedApps.isNotEmpty()

            Button(
                onClick = { showConfirmDialog = true },
                enabled = selectedApps.isNotEmpty() && !submitted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SubmitButtonBackground,
                    disabledContainerColor = SubmitButtonBackground.copy(alpha = 0.5f),
                    contentColor = PrimaryText,
                    disabledContentColor = PrimaryText.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    if (submitted) "SUBMITTED" else "SUBMIT",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                )
            }
            if (!canSubmit) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Please select at least one app.",
                    color = SecondaryText,
                    fontSize = 12.sp
                )
            }
            if (showConfirmDialog) {
                ConfirmationAndInterventionDialog(
                    title = "Confirm App Selection",
                    message = "Are you sure you want to\nlimit the following:",
                    bullets = buildList {
                        if (selectedApps.contains(TargetApp.Instagram)) add("Instagram")
                        if (selectedApps.contains(TargetApp.TikTok)) add("TikTok")
                    },
                    confirmLabel = "Submit",
                    dismissLabel = "Cancel",
                    onConfirm = {
                        submitted = true
                        showConfirmDialog = false
                    },
                    onDismiss = { showConfirmDialog = false }
                )
            }
        }
    }
}

@Composable
private fun AppChoice(
    label: String,
    iconRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable(enabled = enabled) { onToggle() }
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(84.dp)
        )

        val ringColor = PrimaryText
        val fillColor = if (selected) SelectionBlue else Color.Transparent

        Spacer(
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, ringColor, CircleShape)
                .clip(CircleShape)
                .background(fillColor)
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
