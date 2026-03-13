package com.millane.thesis.application

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.data.reactance.PendingReactanceStore
import com.millane.thesis.application.study.SessionManager
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.components.ReactanceScaleDialog
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Shown after the user has been in a target app for 15 minutes (goal advancement intervention).
 * Displays the full list of their current daily goals; user can close the app or continue using it.
 * After their choice, the reactance scale is shown. On "Continue", they are sent back to the target app
 * and another 15-minute timer is scheduled.
 */
class GoalAdvancementActivity : ComponentActivity() {

    internal val sessionManager: SessionManager
        get() = (applicationContext as ThesisApp).sessionManager

    @Volatile
    private var userChoiceMade = false

    private val showReactanceFromHomeState = mutableStateOf(false)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!userChoiceMade) {
            userChoiceMade = true
            Log.d("GoalAdvancement", "User left without choosing; will show reactance on return")
            sessionManager.markClosedViaIntervention()
            showReactanceFromHomeState.value = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        enableEdgeToEdge()

        onBackPressedDispatcher.addCallback(this) { }

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val pendingReactanceStore = PendingReactanceStore(applicationContext)
        sessionManager.markGoalAdvancementShown()

        val goalsRepo = DailyGoalsRepository(applicationContext)
        val goals = runBlocking {
            goalsRepo.goals.first()
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
        }

        setContent {
            InterventionContextAppTheme {
                GoalAdvancementScreen(
                    targetPackage = targetPackage,
                    goals = goals,
                    showReactanceFromHome = showReactanceFromHomeState.value,
                    onChoiceMade = { if (!userChoiceMade) userChoiceMade = true },
                    onReactanceSubmittedGoHome = { responses ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            sessionId?.let { pendingReactanceStore.store(it, responses) }
                            withContext(Dispatchers.Main) { navigateHomeAndClose() }
                        }
                    },
                    onReactanceSubmittedContinue = { responses ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            sessionManager.saveReactanceResponsesForSessionSync(sessionId, responses)
                            sessionManager.markGoalAdvancementDismissed()
                            sessionManager.scheduleNextGoalAdvancementTrigger()
                            withContext(Dispatchers.Main) { launchTargetAppAndFinish(targetPackage) }
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "com.millane.thesis.application.extra.GOAL_ADVANCEMENT_TARGET_PACKAGE"
        const val EXTRA_SESSION_ID = "com.millane.thesis.application.extra.GOAL_ADVANCEMENT_SESSION_ID"
    }
}

@Composable
private fun GoalAdvancementScreen(
    targetPackage: String,
    goals: List<String>,
    showReactanceFromHome: Boolean,
    onChoiceMade: () -> Unit,
    onReactanceSubmittedGoHome: (List<Int>) -> Unit,
    onReactanceSubmittedContinue: (List<Int>) -> Unit
) {
    var showReactanceDialog by rememberSaveable { mutableStateOf(false) }
    var pendingGoHome by rememberSaveable { mutableStateOf(false) }
    var reactanceSelections by rememberSaveable { mutableStateOf(listOf<Int?>(null, null, null, null, null)) }

    if (showReactanceFromHome) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {}
        ReactanceScaleDialog(
            selectedPerItem = reactanceSelections,
            onSelectionChange = { index, value ->
                reactanceSelections = reactanceSelections.toMutableList().apply { set(index, value) }
            },
            onSubmit = { onReactanceSubmittedGoHome(it) }
        )
        return
    }

    if (showReactanceDialog) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {}
        ReactanceScaleDialog(
            selectedPerItem = reactanceSelections,
            onSelectionChange = { index, value ->
                reactanceSelections = reactanceSelections.toMutableList().apply { set(index, value) }
            },
            onSubmit = { responses ->
                if (pendingGoHome) {
                    onReactanceSubmittedGoHome(responses)
                } else {
                    onReactanceSubmittedContinue(responses)
                }
            }
        )
        return
    }

    BackHandler {
        onChoiceMade()
        pendingGoHome = false
        showReactanceDialog = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val bullets =
            if (goals.isEmpty()) emptyList()
            else listOf("Your goals for today:") + goals

        ConfirmationAndInterventionDialog(
            title = "Work on a goal?",
            message = "You've been using this app for 15 minutes. Would you like to work on something from your goals list instead?",
            bullets = bullets,
            confirmLabel = "Close app",
            dismissLabel = "Continue using app",
            onConfirm = {
                onChoiceMade()
                pendingGoHome = true
                showReactanceDialog = true
            },
            onDismiss = {
                onChoiceMade()
                pendingGoHome = false
                showReactanceDialog = true
            },
            dismissOnClickOutside = false
        )
    }
}

private fun GoalAdvancementActivity.navigateHomeAndClose() {
    sessionManager.markClosedViaIntervention()
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    startActivity(intent)
    finishAndRemoveTask()
}

private fun GoalAdvancementActivity.launchTargetAppAndFinish(targetPackage: String) {
    val packageToLaunch = targetPackage.takeIf { it.isNotEmpty() && it != packageName }
    if (packageToLaunch != null) {
        sessionManager.notifyReturningUserToTargetApp(packageToLaunch)
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
                Log.e("GoalAdvancement", "Failed to launch target app: $packageToLaunch", e)
            }
        }
    }
    finishAndRemoveTask()
}
