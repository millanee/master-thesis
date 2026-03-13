package com.millane.thesis.application

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.millane.thesis.application.data.reactance.PendingReactanceStore
import com.millane.thesis.application.study.SessionManager
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.components.ReactanceScaleDialog
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FrictionActivity : ComponentActivity() {

    internal val sessionManager: SessionManager
        get() = (applicationContext as ThesisApp).sessionManager

    /** Set when the user explicitly chooses "Close app" or "Proceed"; used to avoid double-handling on leave. */
    @Volatile
    private var userChoiceMade = false

    /** When true, user left via Home while intervention was active; show only reactance dialog when they return. */
    private val showReactanceFromHomeState = mutableStateOf(false)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User pressed home or switched app; treat as intervention accepted. Show reactance when they return.
        if (!userChoiceMade) {
            userChoiceMade = true
            Log.d("FrictionActivity", "User left screen without choosing; will show reactance on return")
            sessionManager.markClosedViaIntervention()
            showReactanceFromHomeState.value = true
            // Do NOT navigate or finish; activity stays in background so we can show reactance on resume.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Remove enter/exit animation so the friction screen appears instantly (no transition).
        overridePendingTransition(0, 0)

        // Ensure this Activity covers the entire screen surface.
        enableEdgeToEdge()

        // Block the system back button at the Activity level; the composable
        // decides what to do (or not do) with back presses.
        onBackPressedDispatcher.addCallback(this) {
            // Intentionally left empty; handled by Compose BackHandler.
        }

        val targetPackage = intent.getStringExtra(EXTRA_TARGET_PACKAGE).orEmpty()
        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        val pendingReactanceStore = PendingReactanceStore(applicationContext)

        // Record that the design friction intervention was shown (creates intervention attempt with shownAtMs).
        sessionManager.markDesignFrictionShown()

        setContent {
            InterventionContextAppTheme {
                FrictionCountdownScreen(
                    targetPackage = targetPackage,
                    showReactanceFromHome = showReactanceFromHomeState.value,
                    onChoiceMade = {
                        if (!userChoiceMade) userChoiceMade = true
                    },
                    onReactanceSubmittedGoHome = { responses ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            sessionId?.let { pendingReactanceStore.store(it, responses) }
                            withContext(Dispatchers.Main) { navigateHomeAndClose() }
                        }
                    },
                    onReactanceSubmittedLaunchTarget = { responses, pkg ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            sessionManager.saveReactanceResponsesForSessionSync(sessionId, responses)
                            withContext(Dispatchers.Main) { launchTargetAppAndFinish(pkg) }
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "com.millane.thesis.application.extra.TARGET_PACKAGE"
        const val EXTRA_SESSION_ID = "com.millane.thesis.application.extra.FRICTION_SESSION_ID"
    }
}

@Composable
private fun FrictionCountdownScreen(
    targetPackage: String,
    showReactanceFromHome: Boolean,
    onChoiceMade: () -> Unit,
    onReactanceSubmittedGoHome: (List<Int>) -> Unit,
    onReactanceSubmittedLaunchTarget: (List<Int>, String) -> Unit,
    totalSeconds: Int = 6,
) {
    var secondsLeft by rememberSaveable { mutableStateOf(totalSeconds) }
    var showDecisionDialog by rememberSaveable { mutableStateOf(false) }
    var showReactanceDialog by rememberSaveable { mutableStateOf(false) }
    var pendingGoHome by rememberSaveable { mutableStateOf(false) }
    var pendingLaunchPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var reactanceSelections by rememberSaveable { mutableStateOf(listOf<Int?>(null, null, null, null, null)) }

    // User left via Home: show only reactance dialog (no countdown, no decision).
    if (showReactanceFromHome) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
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

    // After user chose Close app or Proceed: show reactance dialog, then navigate on submit.
    if (showReactanceDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
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
                    onReactanceSubmittedLaunchTarget(responses, pendingLaunchPackage ?: targetPackage)
                }
            }
        )
        return
    }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        } else if (!showDecisionDialog) {
            showDecisionDialog = true
        }
    }

    BackHandler {
        if (showDecisionDialog) {
            onChoiceMade()
            pendingGoHome = false
            pendingLaunchPackage = targetPackage
            showReactanceDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val safeSeconds = secondsLeft.coerceAtLeast(0)
        val dotCount = when (safeSeconds) {
            6, 3 -> 1
            5, 2 -> 2
            4, 1 -> 3
            else -> 0
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = safeSeconds.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                modifier = Modifier,
                textAlign = TextAlign.Start,
                text = buildAnnotatedString {
                    append("Take a deep breath")
                    repeat(3) { idx ->
                        val visible = idx < dotCount
                        withStyle(
                            SpanStyle(color = Color.White.copy(alpha = if (visible) 1f else 0f))
                        ) {
                            append(".")
                        }
                    }
                },
                fontSize = 30.sp,
                color = Color.White
            )
        }
    }

    if (showDecisionDialog) {
        ConfirmationAndInterventionDialog(
            title = "Take a short pause?",
            message = "You opened a targeted app. Do you want to close it or proceed?",
            confirmLabel = "Close app",
            dismissLabel = "Proceed",
            onConfirm = {
                onChoiceMade()
                pendingGoHome = true
                pendingLaunchPackage = null
                showReactanceDialog = true
            },
            onDismiss = {
                onChoiceMade()
                pendingGoHome = false
                pendingLaunchPackage = targetPackage
                showReactanceDialog = true
            },
            dismissOnClickOutside = false
        )
    }
}

private fun FrictionActivity.navigateHomeAndClose() {
    sessionManager.markClosedViaIntervention()
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    startActivity(intent)
    // Remove our app from the task so the user sees the home screen, not our app.
    finishAndRemoveTask()
}

private fun FrictionActivity.launchTargetAppAndFinish(targetPackage: String) {
    sessionManager.markDesignFrictionDismissed()
    // Never launch ourselves; target must be the app the user originally intended to open (e.g. Instagram).
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
                Log.e("FrictionActivity", "Failed to launch target app: $packageToLaunch", e)
            }
        } else {
            Log.w("FrictionActivity", "No launch intent for package: $packageToLaunch (check <queries> on Android 11+)")
        }
    }
    // Remove our app from the task so the user sees the target app (e.g. Instagram), not our app.
    finishAndRemoveTask()
}
