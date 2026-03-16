package com.millane.thesis.application

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.ui.screen.MainScreen
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import com.millane.thesis.application.ui.theme.PageBackground
import com.millane.thesis.application.ui.viewmodels.StudyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InterventionContextAppTheme {
                AppEntryScreen()
            }
        }
    }
}

@Composable
private fun AppEntryScreen() {
    val studyVm: StudyViewModel = viewModel()
    val study by studyVm.snapshot.collectAsState()

    LaunchedEffect(Unit) {
        studyVm.initIfMissing()
    }

    when {
        study.participantId == null && study.nickname == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PageBackground),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        study.nickname.isNullOrBlank() -> {
            NicknameScreen(
                onSubmitNickname = { studyVm.saveNickname(it) }
            )
        }

        else -> MainScreen()
    }
}

@Composable
private fun NicknameScreen(
    onSubmitNickname: (String) -> Unit
) {
    var nickname by rememberSaveable { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    val trimmedNickname = nickname.trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter your nickname",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Enter the nickname you chose earlier in the survey.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nickname") }
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = { showConfirmDialog = true },
                enabled = trimmedNickname.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Submit")
            }
        }
    }

    if (showConfirmDialog) {
        ConfirmationAndInterventionDialog(
            title = "Confirm nickname",
            message = "Do you want to submit this nickname?",
            bullets = listOf(trimmedNickname),
            confirmLabel = "Confirm",
            dismissLabel = "Cancel",
            onConfirm = {
                showConfirmDialog = false
                onSubmitNickname(trimmedNickname)
            },
            onDismiss = {
                showConfirmDialog = false
            },
            dismissOnClickOutside = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    InterventionContextAppTheme {
        NicknameScreen(onSubmitNickname = {})
    }
}
