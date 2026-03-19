package com.millane.thesis.application.ui.screen.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.R
import com.millane.thesis.application.apps.TargetApps
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.theme.CardBackground
import com.millane.thesis.application.ui.theme.PrimaryText
import com.millane.thesis.application.ui.theme.SecondaryText
import com.millane.thesis.application.ui.theme.SelectionBlue
import com.millane.thesis.application.ui.theme.SubmitButtonBackground
import com.millane.thesis.application.ui.viewmodels.AppSelectionViewModel

@Composable
fun SelectAppsCard(
    modifier: Modifier = Modifier,
    vm: AppSelectionViewModel = viewModel()
) {
    val submitted by vm.isSubmitted.collectAsState()
    val selectedApps by vm.draftSelected.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }

    RoundedCard(
        background = CardBackground,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp)
    ) {
        Text("Select Apps", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Select the app(s) you want to intervene.\nYou can only select and submit them once.",
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
                selected = selectedApps.contains(TargetApps.INSTAGRAM),
                enabled = !submitted,
                onToggle = { vm.toggle(TargetApps.INSTAGRAM) }
            )

            AppChoice(
                label = "TikTok",
                iconRes = R.drawable.tiktok_icon,
                selected = selectedApps.contains(TargetApps.TIKTOK),
                enabled = !submitted,
                onToggle = { vm.toggle(TargetApps.TIKTOK) }
            )
        }

        Spacer(Modifier.height(25.dp))

        val canSubmit = selectedApps.isNotEmpty()

        Button(
            onClick = { if (!submitted) showConfirmDialog = true },
            enabled = canSubmit && !submitted,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SubmitButtonBackground,
                disabledContainerColor = SubmitButtonBackground.copy(alpha = 0.5f),
                contentColor = PrimaryText,
                disabledContentColor = PrimaryText.copy(alpha = 0.6f)
            ),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (submitted) "SUBMITTED" else "SUBMIT",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
        }

        if (!canSubmit) {
            Spacer(Modifier.height(8.dp))
            Text("Please select at least one app.", color = SecondaryText, fontSize = 12.sp)
        }

        if (showConfirmDialog) {
            val bullets = selectedApps.map { TargetApps.displayName(it) }

            ConfirmationAndInterventionDialog(
                title = "Confirm App Selection",
                message = "Are you sure you want to\nlimit the following:",
                bullets = bullets,
                confirmLabel = "Submit",
                dismissLabel = "Cancel",
                onConfirm = {
                    vm.submit()
                    showConfirmDialog = false
                },
                onDismiss = { showConfirmDialog = false }
            )
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