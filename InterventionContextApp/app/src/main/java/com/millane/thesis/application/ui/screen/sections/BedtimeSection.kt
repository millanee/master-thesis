package com.millane.thesis.application.ui.screen.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimeCard(
    modifier: Modifier = Modifier
) {
    val timePickerState = rememberTimePickerState(
        initialHour = 22,
        initialMinute = 30,
        is24Hour = true
    )

    var showPicker by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    val bedtimeText = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)

    RoundedCard(
        background = CardBackground,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Bedtime",
            fontSize = 34.sp,
            color = PrimaryText
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Set your bedtime for workdays.",
            fontSize = 14.sp,
            color = SecondaryText
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = InnerCardBackground,
            onClick = { if (!submitted) showPicker = true }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = bedtimeText,
                    fontSize = 22.sp,
                    color = PrimaryText
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { showConfirm = true },
            enabled = !submitted,
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
                text = if (submitted) "SUBMITTED" else "SUBMIT",
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
        }
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardBackground
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Select time",
                        fontSize = 18.sp,
                        color = PrimaryText
                    )

                    TimePicker(state = timePickerState)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPicker = false }) {
                            Text("Cancel")
                        }
                        TextButton(onClick = { showPicker = false }) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }

    if (showConfirm) {
        ConfirmationAndInterventionDialog(
            title = "Confirm Bedtime",
            message = "Set your bedtime to:",
            bullets = listOf(bedtimeText),
            confirmLabel = "Confirm",
            dismissLabel = "Cancel",
            onConfirm = {
                submitted = true
                showConfirm = false
            },
            onDismiss = { showConfirm = false }
        )
    }
}