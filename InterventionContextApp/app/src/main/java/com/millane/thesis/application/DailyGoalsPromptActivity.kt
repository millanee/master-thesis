package com.millane.thesis.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.domain.dailygoals.DailyGoal
import com.millane.thesis.application.ui.theme.*
import com.millane.thesis.application.util.getCurrentDayBoundaryMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Shown at first phone unlock of the day (after 4 AM). User enters one or more daily goals.
 * On open, all previous goals are cleared. On confirm, goals are saved and the day is marked as prompted.
 */
class DailyGoalsPromptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val goalsRepo = DailyGoalsRepository(applicationContext)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                goalsRepo.replaceAllGoals(emptyList())
            }
        }
        setContent {
            InterventionContextAppTheme {
                DailyGoalsPromptContent(
                    onConfirm = { goals ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                val list = goals.map { DailyGoal(id = UUID.randomUUID().toString(), text = it) }
                                goalsRepo.replaceAllGoals(list)
                                goalsRepo.setLastDailyGoalsPromptDayMs(getCurrentDayBoundaryMs())
                            }
                            finish()
                        }
                    },
                    onDismiss = { finish() }
                )
            }
        }
    }
}

@Composable
private fun DailyGoalsPromptContent(
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var goalEntries by remember { mutableStateOf(listOf("")) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    "Set your daily goals",
                    fontSize = 22.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    color = PrimaryText
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add one or more goals for today. You can add more from the app later.",
                    fontSize = 14.sp,
                    color = SecondaryText
                )
                Spacer(Modifier.height(16.dp))

                goalEntries.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { goalEntries = goalEntries.toMutableList().apply { set(index, it) } },
                        placeholder = { Text("Goal ${index + 1}", color = SecondaryText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }
                        )
                    )
                }

                TextButton(
                    onClick = {
                        goalEntries = goalEntries + ""
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = PrimaryText, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add another goal", color = PrimaryText)
                }

                Spacer(Modifier.height(16.dp))

                val canConfirm = goalEntries.any { it.trim().isNotEmpty() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Skip")
                    }
                    Button(
                        onClick = {
                            val trimmed = goalEntries.map { it.trim() }.filter { it.isNotEmpty() }
                            if (trimmed.isNotEmpty()) onConfirm(trimmed)
                        },
                        enabled = canConfirm,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
