package com.millane.thesis.application.ui.screen.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.millane.thesis.application.domain.location.LocationEntry
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.theme.*

@Composable
fun LocationsCard(
    workLocations: List<LocationEntry>,
    homeLocations: List<LocationEntry>,
    workInput: String,
    homeInput: String,
    onWorkInputChange: (String) -> Unit,
    onHomeInputChange: (String) -> Unit,
    onAddWork: () -> Unit,
    onAddHome: () -> Unit,
    onDelete: (String) -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    helperText: String? = null,
    modifier: Modifier = Modifier
) {
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
            onInputChange = onWorkInputChange,
            onAdd = onAddWork,
            onDelete = onDelete,
            placeholder = "Add work address…"
        )

        Spacer(Modifier.height(20.dp))

        SectionLabel("HOME")
        Spacer(Modifier.height(8.dp))

        LocationPanel(
            items = homeLocations,
            inputValue = homeInput,
            onInputChange = onHomeInputChange,
            onAdd = onAddHome,
            onDelete = onDelete,
            placeholder = "Add home address…"
        )

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onSubmit,
            enabled = submitEnabled,
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
            Text("SUBMIT", fontSize = 18.sp, letterSpacing = 1.sp)
        }

        if (helperText != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = helperText, color = SecondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, fontSize = 14.sp, color = PrimaryText)
}

@Composable
private fun LocationPanel(
    items: List<LocationEntry>,
    inputValue: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    placeholder: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = InnerCardBackground,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("•", fontSize = 18.sp, color = PrimaryText)
                        Spacer(Modifier.width(8.dp))

                        Text(
                            text = item.displayAddress,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = PrimaryText
                        )

                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = PrimaryText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
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
                    placeholder = { Text(placeholder, color = SecondaryText) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )

                Spacer(Modifier.width(10.dp))

                IconButton(
                    onClick = onAdd,
                    enabled = inputValue.trim().isNotEmpty()
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = if (inputValue.trim().isNotEmpty()) PrimaryText else SecondaryText
                    )
                }
            }
        }
    }
}