package com.millane.thesis.application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.millane.thesis.application.ui.screen.MainScreen
import com.millane.thesis.application.ui.components.ConfirmationAndInterventionDialog
import com.millane.thesis.application.ui.screen.components.RoundedCard
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import com.millane.thesis.application.ui.theme.CardBackground
import com.millane.thesis.application.ui.theme.InnerCardBackground
import com.millane.thesis.application.ui.theme.PageBackground
import com.millane.thesis.application.ui.theme.PrimaryText
import com.millane.thesis.application.ui.theme.SecondaryText
import com.millane.thesis.application.ui.theme.SubmitButtonBackground
import com.millane.thesis.application.ui.viewmodels.RaffleEligibilityState
import com.millane.thesis.application.ui.viewmodels.StudyStatus
import com.millane.thesis.application.ui.viewmodels.StudyViewModel
import com.millane.thesis.application.ui.viewmodels.SusQuestion

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
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        studyVm.initIfMissing()
    }

    LaunchedEffect(study.status) {
        if (study.status == StudyStatus.COMPLETED) {
            studyVm.maybeShowStudyCompletionNotification()
        }
    }

    LaunchedEffect(study.status) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            study.status == StudyStatus.COMPLETED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
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

        study.status == StudyStatus.COMPLETED -> {
            if (!study.feedbackSubmitted) {
                StudyFeedbackScreen(studyVm = studyVm)
            } else {
                QuestionnaireSubmittedScreen(
                    participantId = study.participantId,
                    studyVm = studyVm
                )
            }
        }

        else -> MainScreen()
    }
}

@Composable
private fun StudyQuestionnaireScreen(
    questions: List<SusQuestion>,
    onSubmit: suspend (List<Int>) -> Unit
) {
    var currentQuestionIndex by rememberSaveable { mutableStateOf(0) }
    var answers by rememberSaveable { mutableStateOf(List(questions.size) { 0 }) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }

    val currentAnswer = answers[currentQuestionIndex]
    val currentQuestion = questions[currentQuestionIndex]
    val isLastQuestion = currentQuestionIndex == questions.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        RoundedCard(
            background = CardBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Study questionnaire",
                style = MaterialTheme.typography.headlineSmall,
                color = PrimaryText
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Question ${currentQuestionIndex + 1} of ${questions.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = SecondaryText
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = currentQuestion.prompt,
                style = MaterialTheme.typography.titleMedium,
                color = PrimaryText
            )
            Spacer(Modifier.height(20.dp))

            LikertScaleSelector(
                selectedValue = currentAnswer,
                onValueSelected = { selected ->
                    answers = answers.toMutableList().also { it[currentQuestionIndex] = selected }
                }
            )

            Spacer(Modifier.height(24.dp))

            if (isLastQuestion) {
                androidx.compose.material3.Button(
                    onClick = {
                        if (currentAnswer == 0 || isSubmitting) return@Button
                        isSubmitting = true
                    },
                    enabled = currentAnswer != 0 && !isSubmitting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = SubmitButtonBackground,
                        contentColor = PrimaryText
                    )
                ) {
                    Text(if (isSubmitting) "Submitting..." else "Submit")
                }
            } else {
                TextButton(
                    onClick = {
                        if (currentAnswer != 0) currentQuestionIndex += 1
                    },
                    enabled = currentAnswer != 0,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Next")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next question"
                    )
                }
            }
        }
    }

    LaunchedEffect(isSubmitting) {
        if (isSubmitting) {
            onSubmit(answers)
        }
    }
}

@Composable
private fun LikertScaleSelector(
    selectedValue: Int,
    onValueSelected: (Int) -> Unit
) {
    val labels = listOf(
        1 to "Strongly disagree",
        2 to "Disagree",
        3 to "Neither agree nor disagree",
        4 to "Agree",
        5 to "Strongly agree"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEach { (value, label) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (selectedValue == value) MaterialTheme.colorScheme.primary else SecondaryText,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { onValueSelected(value) },
                shape = RoundedCornerShape(16.dp),
                color = InnerCardBackground
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedValue == value,
                            onClick = { onValueSelected(value) }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedValue == value,
                        onClick = null
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "$value = $label",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryText
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyFeedbackScreen(
    studyVm: StudyViewModel
) {
    var experiencedTechnicalIssues by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var issueDescription by rememberSaveable { mutableStateOf("") }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    val trimmedIssueDescription = issueDescription.trim()
    val isSubmitEnabled = experiencedTechnicalIssues != null &&
        (!experiencedTechnicalIssues!! || trimmedIssueDescription.isNotEmpty()) &&
        !isSubmitting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        RoundedCard(
            background = CardBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Study feedback",
                style = MaterialTheme.typography.headlineSmall,
                color = PrimaryText
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Did you experience any technical issues with the app during the study?",
                style = MaterialTheme.typography.bodyLarge,
                color = PrimaryText
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = SecondaryText,
                        shape = RoundedCornerShape(16.dp)
                    ),
                shape = RoundedCornerShape(16.dp),
                color = InnerCardBackground
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = experiencedTechnicalIssues == true,
                                onClick = { experiencedTechnicalIssues = true }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = experiencedTechnicalIssues == true,
                            onClick = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Yes", color = PrimaryText)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = experiencedTechnicalIssues == false,
                                onClick = { experiencedTechnicalIssues = false }
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = experiencedTechnicalIssues == false,
                            onClick = null
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("No", color = PrimaryText)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "If yes, please describe the issue(s) you experienced.",
                style = MaterialTheme.typography.bodyLarge,
                color = PrimaryText
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = issueDescription,
                onValueChange = { issueDescription = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text("Issue description", color = PrimaryText) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    disabledTextColor = PrimaryText.copy(alpha = 0.6f),
                    focusedLabelColor = PrimaryText,
                    unfocusedLabelColor = PrimaryText,
                    cursorColor = PrimaryText
                )
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = {
                    if (!isSubmitEnabled) return@Button
                    isSubmitting = true
                },
                enabled = isSubmitEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = SubmitButtonBackground,
                    contentColor = PrimaryText
                )
            ) {
                Text(if (isSubmitting) "Submitting..." else "Submit")
            }
        }
    }

    LaunchedEffect(isSubmitting) {
        if (isSubmitting) {
            studyVm.submitStudyFeedback(
                experiencedTechnicalIssues = experiencedTechnicalIssues == true,
                issueDescription = trimmedIssueDescription
            )
        }
    }
}

@Composable
private fun QuestionnaireSubmittedScreen(
    participantId: String?,
    studyVm: StudyViewModel
) {
    val context = LocalContext.current
    val raffleUrl = "https://www.survey-xact.dk/LinkCollector?key=21WNARCDU29N"
    val raffleEligibilityState by studyVm.raffleEligibilityState.collectAsState()

    LaunchedEffect(participantId) {
        studyVm.loadRaffleEligibility(participantId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBackground)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        RoundedCard(
            background = CardBackground,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Study complete",
                style = MaterialTheme.typography.headlineSmall,
                color = PrimaryText
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "We are checking whether you are eligible for the raffle. If you are eligible, the link will appear below. Your email adress can not be attributed to your study results.",
                style = MaterialTheme.typography.bodyLarge,
                color = PrimaryText
            )
            when (val state = raffleEligibilityState) {
                RaffleEligibilityState.Idle,
                RaffleEligibilityState.Loading -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Checking raffle eligibility...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }

                is RaffleEligibilityState.Loaded -> {
                    if (state.result.isEligible) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "You qualified for the raffle. If you want to participate in the raffle you must submit your email address via the link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryText
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raffleUrl))
                                context.startActivity(intent)
                            }
                        ) {
                            Text(
                                text = raffleUrl,
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "You did not meet the raffle eligibility criteria.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SecondaryText
                        )
                    }
                }

                RaffleEligibilityState.Error -> {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "We could not verify raffle eligibility right now.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SecondaryText
                    )
                }
            }
        }
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
                label = { Text("Nickname", color = PrimaryText) },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText,
                    disabledTextColor = PrimaryText.copy(alpha = 0.6f),
                    focusedLabelColor = PrimaryText,
                    unfocusedLabelColor = PrimaryText,
                    cursorColor = PrimaryText
                )
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
