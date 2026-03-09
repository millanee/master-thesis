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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.millane.thesis.application.study.SessionManager
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import kotlinx.coroutines.delay

class FrictionActivity : ComponentActivity() {

    internal val sessionManager: SessionManager
        get() = (applicationContext as ThesisApp).sessionManager

    /** Set when the user explicitly chooses "Close app" or "Proceed"; used to avoid double-handling on leave. */
    @Volatile
    private var userChoiceMade = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // User pressed home or switched app; treat as intervention accepted (no targeted app opened).
        if (!userChoiceMade) {
            userChoiceMade = true
            Log.d("FrictionActivity", "User left screen without choosing; treating as close app")
            navigateHomeAndClose()
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

        // Record that the design friction intervention was shown (creates intervention attempt with shownAtMs).
        sessionManager.markDesignFrictionShown()

        setContent {
            InterventionContextAppTheme {
                FrictionCountdownScreen(
                    onCloseApp = {
                        if (!userChoiceMade) {
                            userChoiceMade = true
                            navigateHomeAndClose()
                        }
                    },
                    onProceed = {
                        if (!userChoiceMade) {
                            userChoiceMade = true
                            launchTargetAppAndFinish(targetPackage)
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "com.millane.thesis.application.extra.TARGET_PACKAGE"
    }
}

@Composable
private fun FrictionCountdownScreen(
    totalSeconds: Int = 6,
    onCloseApp: () -> Unit,
    onProceed: () -> Unit,
) {
    var secondsLeft by rememberSaveable { mutableStateOf(totalSeconds) }
    var showDecisionDialog by rememberSaveable { mutableStateOf(false) }

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
            onProceed()
        }
        // During the countdown (before the dialog), back is ignored.
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = secondsLeft.coerceAtLeast(0).toString(),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White
        )
    }

    if (showDecisionDialog) {
        ConfirmationAndInterventionDialog(
            title = "Take a short pause?",
            message = "You opened a targeted app. Do you want to close it or proceed?",
            confirmLabel = "Close app",
            dismissLabel = "Proceed",
            onConfirm = { onCloseApp() },
            onDismiss = { onProceed() }
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
    if (targetPackage.isNotEmpty()) {
        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e("FrictionActivity", "Failed to launch target app: $targetPackage", e)
            }
        } else {
            Log.w("FrictionActivity", "No launch intent for package: $targetPackage")
        }
    }
    // Remove our app from the task so the user sees the target app (e.g. Instagram), not our app.
    finishAndRemoveTask()
}
