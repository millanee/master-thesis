package com.millane.thesis.application.ui.screen.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.theme.*

@Composable
fun LocationsCard(
    modifier: Modifier = Modifier
) {
    var workLocations by remember { mutableStateOf(listOf("Streetname 1, 00000 city")) }
    var homeLocations by remember { mutableStateOf(listOf("Streetname 2, 00000 city")) }

    var workInput by remember { mutableStateOf("") }
    var homeInput by remember { mutableStateOf("") }

    var submitted by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    val canSubmit = workLocations.isNotEmpty() && homeLocations.isNotEmpty()

    RoundedCard(
        background = CardBackground,
        modifier = modifier.fillMaxWidth()
    ) {
        Text("Locations", fontSize = 34.sp, color = PrimaryText)
        Spacer(Modifier.height(8.dp))

        SectionLabel("WORK")
        Spacer(Modifier.height(8.dp))

        LocationPanel(
            items = workLocations,
            inputValue = workInput,
            onInputChange = { workInput = it },
            onAdd = {
                val trimmed = workInput.trim()
                if (trimmed.isNotEmpty()) {
                    workLocations = workLocations + trimmed
                    workInput = ""
                }
            },
            onDelete = { toDelete ->
                workLocations = workLocations.filterNot { it == toDelete }
            },
            enabled = !submitted
        )

        Spacer(Modifier.height(20.dp))

        SectionLabel("HOME")
        Spacer(Modifier.height(8.dp))

        LocationPanel(
            items = homeLocations,
            inputValue = homeInput,
            onInputChange = { homeInput = it },
            onAdd = {
                val trimmed = homeInput.trim()
                if (trimmed.isNotEmpty()) {
                    homeLocations = homeLocations + trimmed
                    homeInput = ""
                }
            },
            onDelete = { toDelete ->
                homeLocations = homeLocations.filterNot { it == toDelete }
            },
            enabled = !submitted
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { showConfirmDialog = true },
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
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (submitted) "SUBMITTED" else "SUBMIT",
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
        }

        if (!canSubmit && !submitted) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Please add at least one WORK and one HOME address.",
                color = SecondaryText,
                fontSize = 12.sp
            )
        }

        if (showConfirmDialog) {
            ConfirmationAndInterventionDialog(
                title = "Confirm Locations",
                message = "Are you sure you want to\nsubmit these locations?",
                bullets = buildList {
                    add("WORK:")
                    workLocations.forEach { add(it) }
                    add("HOME:")
                    homeLocations.forEach { add(it) }
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


@Composable
private fun SectionLabel(text: String) {
    Text(text = text, fontSize = 14.sp, color = PrimaryText)
}

@Composable
private fun LocationPanel(
    items: List<String>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = InnerCardBackground,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Your goals for today:",
                fontSize = 16.sp,
                color = PrimaryText
            )

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    LocationRow(
                        text = item,
                        enabled = enabled,
                        onDelete = { onDelete(item) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onInputChange,
                    enabled = enabled,
                    placeholder = { Text("Add address…", color = SecondaryText) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default
                )

                Spacer(Modifier.width(10.dp))

                IconButton(
                    onClick = onAdd,
                    enabled = enabled && inputValue.trim().isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = if (enabled && inputValue.trim().isNotEmpty()) PrimaryText else SecondaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationRow(
    text: String,
    enabled: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("•", fontSize = 18.sp, color = PrimaryText)
        Spacer(Modifier.width(8.dp))

        Text(
            text = text,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = PrimaryText
        )

        IconButton(
            onClick = onDelete,
            enabled = enabled,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = if (enabled) PrimaryText else SecondaryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}