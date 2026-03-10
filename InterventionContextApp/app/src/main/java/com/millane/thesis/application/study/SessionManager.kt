package com.millane.thesis.application.study

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.FirebaseFirestore
import com.millane.thesis.application.DailyGoalsPromptActivity
import com.millane.thesis.application.FrictionActivity
import com.millane.thesis.application.GoalAdvancementActivity
import com.millane.thesis.application.data.apps.AppSelectionRepository
import com.millane.thesis.application.data.bedtime.BedtimeRepository
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.data.study.FirestoreSessionRepository
import com.millane.thesis.application.data.study.StudyRepository
import com.millane.thesis.application.location.geofence.GeofenceContextStore
import com.millane.thesis.application.util.getCurrentStudyDayBoundary4AmMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SessionManager(
    private val context: Context
) {

    private val studyRepo = StudyRepository(context)
    private val bedtimeRepo = BedtimeRepository(context)
    private val goalsRepo = DailyGoalsRepository(context)
    private val locationsRepo = LocationsRepository(context)
    private val appsRepo = AppSelectionRepository(context)
    private val sessionRepo = FirestoreSessionRepository(FirebaseFirestore.getInstance())

    private val sessionMutex = Mutex()

    private var activeSessionId: String? = null
    private var activeApp: String? = null

    @Volatile
    private var designFrictionShownForCurrentSession: Boolean = false

    /** True from when we start FrictionActivity until it calls markDesignFrictionShown(); prevents double launch. */
    @Volatile
    private var designFrictionLaunchInProgress: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val goalAdvancementDelayMs = 1 * 60 * 1000L // 1 min for testing (was 15)
    private var goalAdvancementRunnable: Runnable? = null

    /** Prevents double-launch when accessibility events fire multiple times quickly. */
    @Volatile
    private var dailyGoalsPromptLaunchInProgress: Boolean = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun onForegroundAppChanged(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sessionMutex.withLock {
                Log.d("SESSION", "Foreground app changed: $packageName")

                // Don't end session when our app comes to foreground (e.g. FrictionActivity).
                if (packageName == context.packageName) {
                    Log.d("SESSION", "Foreground is our app, keeping session alive")
                    return@withLock
                }

                val appsSubmitted = appsRepo.isSubmitted.first()
                val locationsSubmitted = locationsRepo.isSubmitted.first()
                val bedtimeSubmitted = bedtimeRepo.isSubmitted.first()

                Log.d(
                    "SESSION",
                    "submitted states -> apps=$appsSubmitted locations=$locationsSubmitted bedtime=$bedtimeSubmitted"
                )

                if (!appsSubmitted || !locationsSubmitted || !bedtimeSubmitted) {
                    endSessionIfRunningLocked()
                    return@withLock
                }

                val selectedApps = appsRepo.selectedApps.first()
                Log.d("SESSION", "selected apps = $selectedApps")

                if (packageName !in selectedApps) {
                    endSessionIfRunningLocked()
                    return@withLock
                }

                if (activeSessionId != null && activeApp == packageName) {
                    Log.d("SESSION", "session already active for app = $packageName")
                    return@withLock
                }

                val bedtimeStart = bedtimeRepo.bedtime.first()
                val goals = goalsRepo.goals.first()
                val submittedLocations = locationsRepo.locations.first()
                val activeGeofences = GeofenceContextStore.activeGeofenceIds.value
                val snapshot = studyRepo.getCurrentStudySnapshot() ?: run {
                    endSessionIfRunningLocked()
                    return@withLock
                }

                Log.d("SESSION", "bedtimeStart = $bedtimeStart")
                Log.d("SESSION", "active geofences = $activeGeofences")
                Log.d("SESSION", "submitted locations = $submittedLocations")

                val detectedContext = ContextDetector.detect(
                    bedtimeStart = bedtimeStart,
                    activeGeofenceIds = activeGeofences,
                    submittedLocations = submittedLocations
                )

                Log.d("SESSION", "detected context = $detectedContext")

                if (detectedContext == DetectedContext.NONE) {
                    endSessionIfRunningLocked()
                    return@withLock
                }

                // Goal Advancement: first target-app open after 4 AM should prompt for daily goals.
                if (snapshot.activeInterventionType == InterventionType.GOAL_ADVANCEMENT) {
                    val launchedPrompt = maybeLaunchDailyGoalsPromptForNewDay(packageName)
                    // If we showed the prompt, don't start a session yet; we'll start it
                    // when the user returns to the target app after setting goals.
                    if (launchedPrompt) {
                        return@withLock
                    }
                }

                startSessionLocked(
                    packageName = packageName,
                    ctx = detectedContext,
                    goalsCount = goals.size,
                    activeInterventionType = snapshot.activeInterventionType
                )
            }
        }
    }

    fun currentSessionId(): String? = activeSessionId

    fun shouldShowDesignFriction(): Boolean {
        return activeSessionId != null && !designFrictionShownForCurrentSession
    }

    fun markDesignFrictionShown() {
        val sessionId = activeSessionId ?: return
        if (designFrictionShownForCurrentSession) {
            designFrictionLaunchInProgress = false
            return
        }

        designFrictionShownForCurrentSession = true
        designFrictionLaunchInProgress = false

        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.addInterventionShown(
                sessionId = sessionId,
                shownAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "design friction shown for session=$sessionId")
        }
    }

    fun markDesignFrictionDismissed() {
        val sessionId = activeSessionId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.markLatestInterventionDismissed(
                sessionId = sessionId,
                dismissedAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "design friction dismissed for session=$sessionId")
        }
    }

    fun markClosedViaIntervention() {
        val sessionId = activeSessionId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.markLatestInterventionClosedApp(
                sessionId = sessionId,
                closedAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "app closed via intervention for session=$sessionId")
        }
    }

    fun saveReactanceResponses(responses: List<Int>) {
        val sessionId = activeSessionId ?: return

        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.saveLatestReactanceResponses(
                sessionId = sessionId,
                responses = responses,
                answeredAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "saved reactance for session=$sessionId responses=$responses")
        }
    }

    private suspend fun startSessionLocked(
        packageName: String,
        ctx: DetectedContext,
        goalsCount: Int,
        activeInterventionType: InterventionType
    ) {
        if (activeSessionId != null && activeApp == packageName) {
            Log.d("SESSION", "start skipped because session is already active for $packageName")
            return
        }

        val record = SessionRecord(
            participantId = studyRepo.getCurrentStudySnapshot()?.participantId ?: return,
            targetAppPackage = packageName,
            openedAtMs = System.currentTimeMillis(),
            studyGroup = studyRepo.getCurrentStudySnapshot()?.studyGroup ?: return,
            studyWeek = studyRepo.getCurrentStudySnapshot()?.studyWeek ?: return,
            activeInterventionType = activeInterventionType,
            detectedContextAtStart = ContextDetector.toLocationContextType(ctx),
            goalsCountAtSessionStart = goalsCount
        )

        activeSessionId = record.sessionId
        activeApp = packageName
        designFrictionShownForCurrentSession = false

        if (activeInterventionType == InterventionType.DESIGN_FRICTION) {
            // Launch on main thread immediately so the friction screen appears before Instagram is visible.
            withContext(Dispatchers.Main.immediate) {
                maybeLaunchDesignFriction()
            }
        }
        if (activeInterventionType == InterventionType.GOAL_ADVANCEMENT) {
            withContext(Dispatchers.Main.immediate) {
                scheduleGoalAdvancementTrigger()
            }
        }

        try {
            sessionRepo.createSession(record)
            Log.d("SESSION", "started session ${record.sessionId} for $packageName")
        } catch (e: Exception) {
            activeSessionId = null
            activeApp = null
            designFrictionShownForCurrentSession = false
            designFrictionLaunchInProgress = false
            Log.e("SESSION", "failed to start session for $packageName", e)
        }
    }

    private suspend fun endSessionIfRunningLocked() {
        val id = activeSessionId ?: return

        try {
            sessionRepo.closeSession(
                sessionId = id,
                closedAtMs = System.currentTimeMillis(),
                detectedContextAtEnd = null
            )
            Log.d("SESSION", "ended session $id")
        } catch (e: Exception) {
            Log.e("SESSION", "failed to end session $id", e)
        } finally {
            activeSessionId = null
            activeApp = null
            designFrictionShownForCurrentSession = false
            designFrictionLaunchInProgress = false
            cancelGoalAdvancementTrigger()
            dailyGoalsPromptLaunchInProgress = false
        }
    }

    private suspend fun maybeLaunchDailyGoalsPromptForNewDay(targetPackage: String): Boolean {
        if (dailyGoalsPromptLaunchInProgress) return false

        val boundaryMs = getCurrentStudyDayBoundary4AmMs()
        val lastPrompted = goalsRepo.getLastDailyGoalsPromptDayMs()
        if (lastPrompted == boundaryMs) return false

        // Mark as prompted for this study-day so we don't relaunch repeatedly.
        withContext(Dispatchers.IO) {
            goalsRepo.setLastDailyGoalsPromptDayMs(boundaryMs)
        }
        dailyGoalsPromptLaunchInProgress = true
        withContext(Dispatchers.Main.immediate) {
            val intent = Intent(context, DailyGoalsPromptActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
                putExtra(DailyGoalsPromptActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            }
            context.startActivity(intent)
        }
        return true
    }

    fun markGoalAdvancementShown() {
        val sessionId = activeSessionId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.addInterventionShown(
                sessionId = sessionId,
                shownAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "goal advancement shown for session=$sessionId")
        }
    }

    fun markGoalAdvancementDismissed() {
        val sessionId = activeSessionId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.markLatestInterventionDismissed(
                sessionId = sessionId,
                dismissedAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "goal advancement dismissed for session=$sessionId")
        }
    }

    /** Called after user chooses "Continue" so we show the intervention again after another 15 min. */
    fun scheduleNextGoalAdvancementTrigger() {
        mainHandler.post { scheduleGoalAdvancementTrigger() }
    }

    /** Schedules the 15-min timer; when it fires, launches GoalAdvancementActivity. */
    fun scheduleGoalAdvancementTrigger() {
        cancelGoalAdvancementTrigger()
        val targetPackage = activeApp ?: return
        if (activeSessionId == null) return
        goalAdvancementRunnable = Runnable {
            if (activeSessionId != null && activeApp == targetPackage) {
                maybeLaunchGoalAdvancement()
            }
        }
        mainHandler.postDelayed(goalAdvancementRunnable!!, goalAdvancementDelayMs)
        Log.d("SESSION", "scheduled goal advancement in 15 min for $targetPackage")
    }

    fun cancelGoalAdvancementTrigger() {
        goalAdvancementRunnable?.let { mainHandler.removeCallbacks(it) }
        goalAdvancementRunnable = null
    }

    private fun maybeLaunchGoalAdvancement() {
        val targetPackage = activeApp ?: return
        if (activeSessionId == null) return
        val intent = Intent(context, GoalAdvancementActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(GoalAdvancementActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }
        context.startActivity(intent)
        Log.d("SESSION", "launched GoalAdvancementActivity for $targetPackage")
    }

    private fun maybeLaunchDesignFriction() {
        if (!shouldShowDesignFriction() || designFrictionLaunchInProgress) return
        val targetPackage = activeApp ?: return

        // Prevent a second FrictionActivity if accessibility events fire again before onCreate runs.
        designFrictionLaunchInProgress = true

        val intent = Intent(context, FrictionActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(FrictionActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }

        context.startActivity(intent)
    }
}