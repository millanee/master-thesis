package com.millane.thesis.application

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.millane.thesis.application.util.getCurrentStudyDayBoundary4AmMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Shown on first target-app open after 4 AM on Goal Advancement weeks.
 * On open, all previous goals are cleared. User must enter one or more daily goals.
 * After confirm, the user is taken back to the originally opened target app.
 */
class DailyGoalsPromptActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TARGET_PACKAGE = "com.millane.thesis.application.extra.DAILY_GOALS_TARGET_PACKAGE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
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
                            }
                            launchTargetAppAndFinish(targetPackage)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DailyGoalsPromptContent(
    onConfirm: (List<String>) -> Unit
) {
    var goalEntries by remember { mutableStateOf(listOf("")) }
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = { /* non-cancelable: user must enter a goal */ }) {
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
                Button(
                    onClick = {
                        val trimmed = goalEntries.map { it.trim() }.filter { it.isNotEmpty() }
                        if (trimmed.isNotEmpty()) onConfirm(trimmed)
                    },
                    enabled = canConfirm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("Confirm")
                }
            }
        }
    }
}

private fun DailyGoalsPromptActivity.launchTargetAppAndFinish(targetPackage: String) {
    val packageToLaunch = targetPackage.takeIf { it.isNotEmpty() && it != packageName }
    if (packageToLaunch != null) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageToLaunch)
        if (launchIntent != null) {
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("DailyGoalsPrompt", "Failed to launch target app: $packageToLaunch", e)
            }
        } else {
            Log.w("DailyGoalsPrompt", "No launch intent for package: $packageToLaunch")
        }
    } else {
        Log.w("DailyGoalsPrompt", "Target package is empty or our own package; not launching")
    }
    finishAndRemoveTask()
}
