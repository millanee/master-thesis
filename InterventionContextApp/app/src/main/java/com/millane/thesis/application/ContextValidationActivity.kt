package com.millane.thesis.application

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import com.millane.thesis.application.data.reactance.PendingReactanceStore
import com.millane.thesis.application.data.study.FirestoreSessionRepository
import com.millane.thesis.application.study.ContextValidationStatus
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class ContextValidationActivity : ComponentActivity() {

    private val sessionRepo by lazy { FirestoreSessionRepository(FirebaseFirestore.getInstance()) }
    private val pendingReactanceStore by lazy { PendingReactanceStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
        if (sessionId.isNullOrEmpty()) {
            finish()
            return
        }

        setContent {
            InterventionContextAppTheme {
                ContextValidationScreen(
                    sessionId = sessionId,
                    onAnswer = { confirmed ->
                        saveAnswerAndClose(sessionId, confirmed)
                    }
                )
            }
        }
    }

    private fun saveAnswerAndClose(sessionId: String, confirmed: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val status = if (confirmed) {
                ContextValidationStatus.CONFIRMED
            } else {
                ContextValidationStatus.REJECTED
            }
            runCatching {
                sessionRepo.setContextValidationStatus(sessionId, status)
            }
            // Save any pending reactance (stored when user chose "Go home" in intervention)
            val pendingResponses = pendingReactanceStore.takeForSession(sessionId)
            if (pendingResponses != null) {
                runCatching {
                    sessionRepo.saveLatestReactanceResponses(
                        sessionId = sessionId,
                        responses = pendingResponses,
                        answeredAtMs = System.currentTimeMillis()
                    )
                }
            }
            launch(Dispatchers.Main) {
                navigateHomeAndClose()
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID =
            "com.millane.thesis.application.extra.CONTEXT_VALIDATION_SESSION_ID"
    }
}

private fun ContextValidationActivity.navigateHomeAndClose() {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    startActivity(intent)
    finishAndRemoveTask()
}

@Composable
private fun ContextValidationScreen(
    sessionId: String,
    onAnswer: (Boolean) -> Unit
) {
    var dialogVisible by remember { mutableStateOf(true) }
    var contextLabel by remember { mutableStateOf<String?>(null) }
    var loadFinished by remember { mutableStateOf(false) }

    // Load the detected context for this session from Firestore.
    androidx.compose.runtime.LaunchedEffect(sessionId) {
        val db = FirebaseFirestore.getInstance()
        val snap = runCatching {
            db.collection("sessions").document(sessionId).get().await()
        }.getOrNull()

        val rawContext = snap?.getString("detectedContextAtStart")
        contextLabel = rawContext?.let { toHumanReadableContext(it) }
        loadFinished = true
    }

    if (!dialogVisible) {
        // Keep a simple dark background behind the dialog.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {}
        return
    }

    val questionText: String? = if (!loadFinished) {
        null
    } else if (contextLabel != null) {
        "Was your context throughout this session the following: $contextLabel?"
    } else {
        "Was the detected context your actual context throughout this session?"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        AlertDialog(
            onDismissRequest = { /* Block outside dismiss; user must choose yes/no */ },
            title = {
                Text(
                    text = "Confirm detected context",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                if (questionText == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        text = questionText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    dialogVisible = false
                    onAnswer(true)
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                Button(onClick = {
                    dialogVisible = false
                    onAnswer(false)
                }) {
                    Text("No")
                }
            }
        )
    }
}

private fun toHumanReadableContext(raw: String): String {
    return when (raw.uppercase(Locale.getDefault())) {
        "HOME" -> "Home"
        "WORK" -> "Work"
        "BEDTIME" -> "Bedtime"
        else -> raw.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }
}
