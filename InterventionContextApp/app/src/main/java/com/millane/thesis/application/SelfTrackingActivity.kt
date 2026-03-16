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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.millane.thesis.application.data.usage.SelfTrackingUsageRepository
import com.millane.thesis.application.data.usage.SelfTrackingUsageSnapshot
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.study.SessionManager
import com.millane.thesis.application.ui.components.ReactanceScaleDialog
import com.millane.thesis.application.ui.theme.InterventionContextAppTheme
import com.millane.thesis.application.ui.theme.PrimaryText
import com.millane.thesis.application.ui.theme.SecondaryText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SelfTrackingActivity : ComponentActivity() {

    internal val sessionManager: SessionManager
        get() = (applicationContext as ThesisApp).sessionManager

    private val usageRepo by lazy { SelfTrackingUsageRepository(applicationContext) }

    @Volatile
    private var userChoiceMade = false
    private val showReactanceFromHomeState = mutableStateOf(false)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!userChoiceMade) {
            userChoiceMade = true
            Log.d("SelfTrackingActivity", "User left without choosing; will show reactance on return")
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
        val openedAtMs = intent.getLongExtra(EXTRA_SESSION_OPENED_AT_MS, System.currentTimeMillis())
        val contextType = intent.getStringExtra(EXTRA_SESSION_CONTEXT_TYPE)
            ?.let { runCatching { LocationContextType.valueOf(it) }.getOrNull() }

        sessionManager.markInterventionUiVisible()
        sessionManager.markSelfTrackingShown()

        setContent {
            InterventionContextAppTheme {
                SelfTrackingScreen(
                    targetPackage = targetPackage,
                    sessionOpenedAtMs = openedAtMs,
                    sessionContextType = contextType,
                    usageRepo = usageRepo,
                    showReactanceFromHome = showReactanceFromHomeState.value,
                    onChoiceMade = { if (!userChoiceMade) userChoiceMade = true },
                    onReactanceSubmittedCloseApp = { responses ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            sessionManager.saveReactanceResponsesForSessionSync(sessionId, responses)
                            sessionManager.markClosedViaIntervention()
                            withContext(Dispatchers.Main) {
                                sessionManager.markInterventionUiHidden()
                                sessionManager.launchContextValidationForSession(sessionId)
                                finishAndRemoveTask()
                            }
                        }
                    },
                    onReactanceSubmittedContinue = { responses ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            sessionManager.saveReactanceResponsesForSessionSync(sessionId, responses)
                            sessionManager.markSelfTrackingDismissed()
                            sessionManager.scheduleNextSelfTrackingTrigger()
                            withContext(Dispatchers.Main) {
                                sessionManager.markInterventionUiHidden()
                                launchTargetAppAndFinish(targetPackage)
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE =
            "com.millane.thesis.application.extra.SELF_TRACKING_TARGET_PACKAGE"
        const val EXTRA_SESSION_ID =
            "com.millane.thesis.application.extra.SELF_TRACKING_SESSION_ID"
        const val EXTRA_SESSION_OPENED_AT_MS =
            "com.millane.thesis.application.extra.SELF_TRACKING_OPENED_AT_MS"
        const val EXTRA_SESSION_CONTEXT_TYPE =
            "com.millane.thesis.application.extra.SELF_TRACKING_CONTEXT_TYPE"
    }
}

@Composable
private fun SelfTrackingScreen(
    targetPackage: String,
    sessionOpenedAtMs: Long,
    sessionContextType: LocationContextType?,
    usageRepo: SelfTrackingUsageRepository,
    showReactanceFromHome: Boolean,
    onChoiceMade: () -> Unit,
    onReactanceSubmittedCloseApp: (List<Int>) -> Unit,
    onReactanceSubmittedContinue: (List<Int>) -> Unit
) {
    var showReactanceDialog by rememberSaveable { mutableStateOf(false) }
    var pendingGoHome by rememberSaveable { mutableStateOf(false) }
    var reactanceSelections by rememberSaveable {
        mutableStateOf(listOf<Int?>(null, null, null, null, null))
    }
    var uiState by remember { mutableStateOf<UsageUiState?>(null) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val snapshot = usageRepo.getSnapshotIncludingOngoing(
            nowMs = now,
            ongoingStartMs = sessionOpenedAtMs,
            ongoingContextType = sessionContextType
        )
        uiState = UsageUiState.from(snapshot, now, sessionOpenedAtMs, sessionContextType)
    }

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
                reactanceSelections =
                    reactanceSelections.toMutableList().apply { set(index, value) }
            },
            onSubmit = { onReactanceSubmittedCloseApp(it) }
        )
        return
    }

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
                reactanceSelections =
                    reactanceSelections.toMutableList().apply { set(index, value) }
            },
            onSubmit = { responses ->
                if (pendingGoHome) {
                    onReactanceSubmittedCloseApp(responses)
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
        uiState?.let { state ->
            UsageStatisticsDialog(
                state = state,
                onCloseApp = {
                    onChoiceMade()
                    pendingGoHome = true
                    showReactanceDialog = true
                },
                onDismiss = {
                    onChoiceMade()
                    pendingGoHome = false
                    showReactanceDialog = true
                }
            )
        }
    }
}

private data class UsageUiState(
    val sessionDurationMinutes: Int,
    val contextLabel: String,
    val lastContextTime: String?,
    val contextDurationMinutes: Int,
    val hourFractions: List<Float>,
    val currentHourIndex: Int
) {
    companion object {
        fun from(
            snapshot: SelfTrackingUsageSnapshot,
            nowMs: Long,
            sessionOpenedAtMs: Long,
            contextType: LocationContextType?
        ): UsageUiState {
            val sessionDurationMin =
                ((nowMs - sessionOpenedAtMs) / 60000L).coerceAtLeast(0L).toInt()
            val contextMin = (snapshot.totalContextMsToday / 60000L).toInt()

            val dateFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val lastContext =
                snapshot.lastContextUsedAtMs?.let { dateFmt.format(Date(it)) }

            val hourFractions = snapshot.perHourMs.map { ms ->
                val minutes = ms / 60000f
                (minutes / 60f).coerceIn(0f, 1f)
            }

            val hourIndex = (((nowMs - snapshot.boundaryMs) / (60 * 60 * 1000L)).toInt())
                .coerceIn(0, 23)

            return UsageUiState(
                sessionDurationMinutes = sessionDurationMin,
                contextLabel = contextLabel(contextType),
                lastContextTime = lastContext,
                contextDurationMinutes = contextMin,
                hourFractions = hourFractions,
                currentHourIndex = hourIndex
            )
        }

        private fun contextLabel(contextType: LocationContextType?): String = when (contextType) {
            LocationContextType.HOME -> "home"
            LocationContextType.WORK -> "work"
            LocationContextType.BEDTIME -> "bedtime"
            null -> "this context"
        }
    }
}

@Composable
private fun UsageStatisticsDialog(
    state: UsageUiState,
    onCloseApp: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF6EDED),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(3.dp, Color(0xFFE6A7A7), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Usage Statistics",
                    fontSize = 22.sp,
                    color = PrimaryText
                )
                Text(
                    text = "Check your usage behavior!",
                    fontSize = 14.sp,
                    color = SecondaryText
                )

                Text(
                    text = "Session duration: ${state.sessionDurationMinutes} min",
                    fontSize = 16.sp,
                    color = PrimaryText
                )
                Text(
                    text = "SM last used in ${state.contextLabel}: ${state.lastContextTime ?: "-"}",
                    fontSize = 16.sp,
                    color = PrimaryText
                )
                Text(
                    text = "Session duration during ${state.contextLabel}: ${state.contextDurationMinutes} min",
                    fontSize = 16.sp,
                    color = PrimaryText
                )
                Spacer(Modifier.height(8.dp))

                HourlyDiagram(
                    fractions = state.hourFractions,
                    currentHourIndex = state.currentHourIndex
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DialogButton(
                        label = "Close App",
                        background = Color(0xFFBFEA8B),
                        onClick = onCloseApp,
                        modifier = Modifier.weight(1f)
                    )
                    DialogButton(
                        label = "Dismiss",
                        background = Color(0xFFE8B8B8),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyDiagram(
    fractions: List<Float>,
    currentHourIndex: Int
) {
    val hourLabels = listOf("4", "5", "6", "7", "8", "9", "10", "11", "12", "1", "2", "3")

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HourRow(
            titleSuffix = "p.m.",
            labels = hourLabels,
            fractions = fractions.subList(0, 12),
            globalOffset = 0,
            currentHourIndex = currentHourIndex
        )
        HourRow(
            titleSuffix = "a.m.",
            labels = hourLabels,
            fractions = fractions.subList(12, 24),
            globalOffset = 12,
            currentHourIndex = currentHourIndex
        )
    }
}

@Composable
private fun HourRow(
    titleSuffix: String,
    labels: List<String>,
    fractions: List<Float>,
    globalOffset: Int,
    currentHourIndex: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text(text = "60\nmin", fontSize = 10.sp, color = SecondaryText)
                Text(text = "0\nmin", fontSize = 10.sp, color = SecondaryText)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Divider(
                    color = SecondaryText.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        fractions.forEachIndexed { idx, fraction ->
                            val heightFraction = fraction.coerceIn(0f, 1f)
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .fillMaxHeight(heightFraction)
                                    .background(
                                        if (globalOffset + idx == currentHourIndex) {
                                            Color(0xFF2DB6CC)
                                        } else {
                                            Color(0xFFBBBBBB)
                                        }
                                    )
                            )
                        }
                    }
                }
                Divider(
                    color = SecondaryText.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(28.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(text = label, fontSize = 10.sp, color = SecondaryText)
                }
            }
            Text(
                text = titleSuffix,
                fontSize = 10.sp,
                color = SecondaryText,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun DialogButton(
    label: String,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier.height(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                fontSize = 20.sp,
                color = PrimaryText
            )
        }
    }
}

private fun SelfTrackingActivity.launchTargetAppAndFinish(targetPackage: String) {
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
                Log.e("SelfTrackingActivity", "Failed to launch target app: $packageToLaunch", e)
            }
        }
    }
    finishAndRemoveTask()
}
