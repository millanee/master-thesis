package com.millane.thesis.application.ui.screen.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.theme.*

@Composable
fun DailyGoalsCard(
    modifier: Modifier = Modifier
) {
    var goalInput by remember { mutableStateOf("") }
    var goals by remember { mutableStateOf(listOf("Create Figma Design", "Read Book")) } // demo defaults
    val focusManager = LocalFocusManager.current

    RoundedCard(
        background = CardBackground,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 340.dp)
    ) {
        Text("Daily Goals", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Text("Set or remove your goals for today", fontSize = 16.sp, color = SecondaryText)
        Spacer(Modifier.height(24.dp))

        // Inner list panel
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = InnerCardBackground,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Your goals for today:",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = PrimaryText
                )

                Spacer(Modifier.height(12.dp))

                if (goals.isEmpty()) {
                    Text("No goals added yet.", fontSize = 14.sp, color = SecondaryText)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        goals.forEach { goal ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("•", fontSize = 18.sp, color = PrimaryText)
                                Spacer(Modifier.width(10.dp))

                                Text(
                                    text = goal,
                                    fontSize = 14.sp,
                                    color = PrimaryText,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { goals = goals - goal },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete goal",
                                        tint = PrimaryText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = goalInput,
                onValueChange = { goalInput = it },
                placeholder = { Text("Add a goal…", color = SecondaryText) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = goalInput.trim()
                        if (trimmed.isNotEmpty()) {
                            goals = goals + trimmed
                            goalInput = ""
                        }
                        focusManager.clearFocus()
                    }
                )
            )

            Spacer(Modifier.width(10.dp))

            IconButton(
                onClick = {
                    val trimmed = goalInput.trim()
                    if (trimmed.isNotEmpty()) {
                        goals = goals + trimmed
                        goalInput = ""
                        focusManager.clearFocus()
                    }
                },
                enabled = goalInput.trim().isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add goal",
                    tint = if (goalInput.trim().isNotEmpty()) PrimaryText else SecondaryText
                )
            }
        }
    }
}