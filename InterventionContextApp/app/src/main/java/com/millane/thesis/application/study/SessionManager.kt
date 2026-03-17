package com.millane.thesis.application.study

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import com.millane.thesis.application.ContextValidationActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.millane.thesis.application.DailyGoalsPromptActivity
import com.millane.thesis.application.FrictionActivity
import com.millane.thesis.application.GoalAdvancementActivity
import com.millane.thesis.application.SelfTrackingActivity
import com.millane.thesis.application.data.apps.AppSelectionRepository
import com.millane.thesis.application.data.bedtime.BedtimeRepository
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.data.study.FirestoreSessionRepository
import com.millane.thesis.application.data.study.StudyRepository
import com.millane.thesis.application.data.usage.SelfTrackingUsageRepository
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.location.geofence.GeofenceContextStore
import com.millane.thesis.application.notifications.StudyCompletionNotifier
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
    private val usageRepo = SelfTrackingUsageRepository(context)
    private val studyCompletionNotifier = StudyCompletionNotifier(context)

    private val sessionMutex = Mutex()

    private var activeSessionId: String? = null
    private var activeApp: String? = null
    private var activeSessionOpenedAtMs: Long? = null
    private var activeLocationAtStart: LocationContextType? = null
    private var activeIntervention: InterventionType? = null
    private var contextValidationHandledForActiveSession: Boolean = false

    @Volatile
    private var designFrictionShownForCurrentSession: Boolean = false

    /** True from when we start FrictionActivity until it calls markDesignFrictionShown(); prevents double launch. */
    @Volatile
    private var designFrictionLaunchInProgress: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val goalAdvancementDelayMs = 1 * 60 * 1000L // 1 min for testing (was 15)
    private var goalAdvancementRunnable: Runnable? = null

    private val selfTrackingDelayMs = 1 * 60 * 1000L // 1 min for testing (was 15)
    private var selfTrackingRunnable: Runnable? = null

    /**
     * Some devices/launchers briefly report the launcher as foreground while an app is opening.
     * Without a guard, this can immediately end a just-started session and show the context dialog.
     */
    private val minSessionDurationForContextValidationMs = 5_000L

    /** Prevents double-launch when accessibility events fire multiple times quickly. */
    @Volatile
    private var dailyGoalsPromptLaunchInProgress: Boolean = false

    /** Set by intervention activities before launching the target app (e.g. after "Proceed" / "Continue").
     *  Prevents ending the session and showing the context confirmation when the brief switch to launcher
     *  occurs between our activity finishing and the target app becoming foreground. */
    @Volatile
    private var pendingReturnToTargetAppPackage: String? = null

    @Volatile
    private var pendingReturnToTargetTimestampMs: Long = 0L

    @Volatile
    private var lastForegroundPackage: String? = null

    @Volatile
    private var previousForegroundPackage: String? = null

    // Some devices / launchers take several seconds between our Activity finishing and the
    // target app becoming foreground (especially with task/animation delays). If this window
    // is too small, we may incorrectly treat the transition as the user "leaving" the app and
    // trigger context validation on top of the target app.
    private val pendingReturnToTargetWindowMs = 15_000L

    /** Call this before launching the target app from an intervention (e.g. after "Proceed" or "Continue")
     *  so the context confirmation is not shown for the brief transition. */
    fun notifyReturningUserToTargetApp(targetPackage: String) {
        if (targetPackage.isNotEmpty()) {
            pendingReturnToTargetAppPackage = targetPackage
            pendingReturnToTargetTimestampMs = System.currentTimeMillis()
            Log.d("SESSION", "notifyReturningUserToTargetApp: $targetPackage")
        }
    }

    /** Set right before launching an intervention activity. Some devices briefly report launcher
     *  as foreground during the transition; we skip ending the session in that window. */
    @Volatile
    private var pendingInterventionLaunchAtMs: Long = 0L

    @Volatile
    private var interventionLaunchInProgress: Boolean = false

    @Volatile
    private var interventionUiVisible: Boolean = false

    private val pendingInterventionLaunchWindowMs = 15_000L

    /** Debounce: only end session after user has been away from target app for this long.
     *  Filters out spurious launcher events that fire while user is still in the target app. */
    private val leaveDebounceMs = 2_500L
    private var pendingEndSessionRunnable: Runnable? = null

    private fun cancelPendingEndSession() {
        pendingEndSessionRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingEndSessionRunnable = null
    }

    private fun scheduleEndSessionDebounced() {
        cancelPendingEndSession()
        pendingEndSessionRunnable = Runnable {
            pendingEndSessionRunnable = null
            CoroutineScope(Dispatchers.IO).launch {
                sessionMutex.withLock {
                    endSessionIfRunningLocked()
                }
            }
        }
        mainHandler.postDelayed(pendingEndSessionRunnable!!, leaveDebounceMs)
        Log.d("SESSION", "scheduled end session in ${leaveDebounceMs}ms (debounce)")
    }

    private fun shouldSkipEndBecauseInterventionLaunchInProgress(): Boolean {
        if (interventionUiVisible) return true
        if (interventionLaunchInProgress) return true
        val at = pendingInterventionLaunchAtMs
        if (at == 0L) return false
        val elapsed = System.currentTimeMillis() - at
        return elapsed in 0..pendingInterventionLaunchWindowMs
    }

    private fun beginInterventionLaunch() {
        cancelPendingEndSession()
        interventionLaunchInProgress = true
        pendingInterventionLaunchAtMs = System.currentTimeMillis()
    }

    private fun clearInterventionLaunchGuard() {
        interventionLaunchInProgress = false
        pendingInterventionLaunchAtMs = 0L
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun onForegroundAppChanged(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sessionMutex.withLock {
                maybeNotifyStudyCompletion()
                previousForegroundPackage = lastForegroundPackage
                lastForegroundPackage = packageName
                Log.d("SESSION", "Foreground app changed: $packageName")

                // Don't end session when our app comes to foreground (e.g. FrictionActivity).
                if (packageName == context.packageName) {
                    cancelPendingEndSession()
                    clearInterventionLaunchGuard()
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
                    if (shouldSkipEndBecauseReturningToTarget()) {
                        Log.d("SESSION", "skipping end: returning user to target app (onboarding check)")
                        return@withLock
                    }
                    if (shouldSkipEndBecauseInterventionLaunchInProgress()) {
                        Log.d("SESSION", "skipping end: intervention launch in progress (onboarding check)")
                        return@withLock
                    }
                    scheduleEndSessionDebounced()
                    return@withLock
                }

                val selectedApps = appsRepo.selectedApps.first()
                Log.d("SESSION", "selected apps = $selectedApps")

                if (packageName !in selectedApps) {
                    if (shouldSkipEndBecauseReturningToTarget()) {
                        Log.d("SESSION", "skipping end: returning user to target app (not in selected)")
                        return@withLock
                    }
                    if (shouldSkipEndBecauseInterventionLaunchInProgress()) {
                        Log.d("SESSION", "skipping end: intervention launch in progress (launcher during transition)")
                        return@withLock
                    }
                    scheduleEndSessionDebounced()
                    return@withLock
                }

                // Once a selected target app is foreground again, any intervention UI is no longer visible.
                interventionUiVisible = false

                val bedtimeStart = bedtimeRepo.bedtime.first()
                val goals = goalsRepo.goals.first()
                val submittedLocations = locationsRepo.locations.first()
                val activeGeofences = GeofenceContextStore.activeGeofenceIds.value
                val snapshot = studyRepo.getCurrentStudySnapshot() ?: run {
                    if (shouldSkipEndBecauseReturningToTarget()) {
                        Log.d("SESSION", "skipping end: returning user to target app (no snapshot)")
                        return@withLock
                    }
                    if (shouldSkipEndBecauseInterventionLaunchInProgress()) {
                        Log.d("SESSION", "skipping end: intervention launch in progress (no snapshot)")
                        return@withLock
                    }
                    Log.d("SESSION", "no active study snapshot (not started or finished); ending any active session")
                    scheduleEndSessionDebounced()
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

                // Goal Advancement: first target-app open after 4 AM should prompt for daily goals.
                // Check this BEFORE the context check so we show the prompt even when geofences
                // haven't fired yet (e.g. first open of the day right after unlocking).
                if (snapshot.activeInterventionType == InterventionType.GOAL_ADVANCEMENT) {
                    val launchedPrompt = maybeLaunchDailyGoalsPromptForNewDay(packageName)
                    // If we showed the prompt, don't start a session yet; we'll start it
                    // when the user returns to the target app after setting goals.
                    if (launchedPrompt) {
                        return@withLock
                    }
                }

                if (detectedContext == DetectedContext.NONE) {
                    // Allow session start when returning from goal prompt even with NONE context
                    // (geofences may not have fired yet when user confirms goals and returns to target app).
                    val boundaryMs = getCurrentStudyDayBoundary4AmMs()
                    val lastPrompted = goalsRepo.getLastDailyGoalsPromptDayMs()
                    val returningFromGoalPrompt = snapshot.activeInterventionType == InterventionType.GOAL_ADVANCEMENT &&
                        lastPrompted == boundaryMs
                    val allowSelfTrackingWithoutContext =
                        snapshot.activeInterventionType == InterventionType.SELF_TRACKING
                    if (!returningFromGoalPrompt && !allowSelfTrackingWithoutContext) {
                        if (shouldSkipEndBecauseReturningToTarget()) {
                            Log.d("SESSION", "skipping end: returning user to target app (context NONE)")
                            return@withLock
                        }
                        if (shouldSkipEndBecauseInterventionLaunchInProgress()) {
                            Log.d("SESSION", "skipping end: intervention launch in progress (context NONE)")
                            return@withLock
                        }
                        scheduleEndSessionDebounced()
                        return@withLock
                    }
                    Log.d(
                        "SESSION",
                        "context NONE but allowing session start (goalPromptReturn=$returningFromGoalPrompt, selfTracking=$allowSelfTrackingWithoutContext)"
                    )
                }

                // Self-tracking: touching stats here ensures they are reset on first open after 4 AM.
                if (snapshot.activeInterventionType == InterventionType.SELF_TRACKING) {
                    usageRepo.touchToday()
                }

                if (activeSessionId != null && activeApp == packageName) {
                    cancelPendingEndSession()
                    pendingReturnToTargetAppPackage = null
                    ensureInterventionStateForActiveSession(snapshot.activeInterventionType)
                    Log.d("SESSION", "session already active for app = $packageName")
                    return@withLock
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

    private suspend fun maybeNotifyStudyCompletion() {
        val start = studyRepo.startDateMs.first() ?: return
        val questionnaireSubmitted = studyRepo.questionnaireSubmitted.first()
        if (questionnaireSubmitted) return
        if (!StudyManager.isStudyComplete(start, System.currentTimeMillis())) return

        val shouldShow = studyRepo.markCompletionNotificationShownIfNeeded()
        if (shouldShow) {
            studyCompletionNotifier.show()
        }
    }

    suspend fun prepareGoalAdvancementSessionAfterDailyGoalsPrompt(targetPackage: String) {
        if (targetPackage.isBlank()) return

        sessionMutex.withLock {
            val snapshot = studyRepo.getCurrentStudySnapshot() ?: return
            if (snapshot.activeInterventionType != InterventionType.GOAL_ADVANCEMENT) return

            if (activeSessionId != null && activeApp == targetPackage) {
                cancelPendingEndSession()
                ensureInterventionStateForActiveSession(snapshot.activeInterventionType)
                Log.d("SESSION", "goal prompt return found existing session for $targetPackage")
                return
            }

            val bedtimeStart = bedtimeRepo.bedtime.first()
            val goals = goalsRepo.goals.first()
            val submittedLocations = locationsRepo.locations.first()
            val activeGeofences = GeofenceContextStore.activeGeofenceIds.value

            val detectedContext = ContextDetector.detect(
                bedtimeStart = bedtimeStart,
                activeGeofenceIds = activeGeofences,
                submittedLocations = submittedLocations
            )

            cancelPendingEndSession()
            startSessionLocked(
                packageName = targetPackage,
                ctx = detectedContext,
                goalsCount = goals.size,
                activeInterventionType = snapshot.activeInterventionType
            )
            Log.d("SESSION", "prepared goal-advancement session after daily goals prompt for $targetPackage")
        }
    }

    fun currentSessionId(): String? = activeSessionId

    fun shouldShowDesignFriction(): Boolean {
        return activeSessionId != null && !designFrictionShownForCurrentSession
    }

    fun markDesignFrictionShown() {
        val sessionId = activeSessionId ?: return
        if (designFrictionShownForCurrentSession) {
            clearInterventionLaunchGuard()
            designFrictionLaunchInProgress = false
            return
        }

        designFrictionShownForCurrentSession = true
        clearInterventionLaunchGuard()
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

    fun launchContextValidationForSession(
        sessionId: String?,
        launchTargetPackageAfterSubmit: String? = null
    ) {
        val safeSessionId = sessionId ?: return
        if (activeSessionId == safeSessionId) {
            contextValidationHandledForActiveSession = true
        }
        val intent = Intent(context, ContextValidationActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(ContextValidationActivity.EXTRA_SESSION_ID, safeSessionId)
            putExtra(
                ContextValidationActivity.EXTRA_LAUNCH_TARGET_PACKAGE_AFTER_SUBMIT,
                launchTargetPackageAfterSubmit
            )
        }
        context.startActivity(intent)
    }

    fun markInterventionUiVisible() {
        interventionUiVisible = true
        clearInterventionLaunchGuard()
    }

    fun markInterventionUiHidden() {
        interventionUiVisible = false
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

    fun saveReactanceResponsesForSession(sessionId: String?, responses: List<Int>) {
        val safeId = sessionId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.saveLatestReactanceResponses(
                sessionId = safeId,
                responses = responses,
                answeredAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "saved reactance for session=$safeId responses=$responses")
        }
    }

    /** Suspend version: saves to Firebase and returns when done. Use before navigating so the save completes. */
    suspend fun saveReactanceResponsesForSessionSync(sessionId: String?, responses: List<Int>) {
        val safeId = sessionId ?: return
        withContext(Dispatchers.IO) {
            sessionRepo.saveLatestReactanceResponses(
                sessionId = safeId,
                responses = responses,
                answeredAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "saved reactance for session=$safeId responses=$responses")
        }
    }

    fun markSelfTrackingShown() {
        val sessionId = activeSessionId ?: return
        clearInterventionLaunchGuard()
        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.addInterventionShown(
                sessionId = sessionId,
                shownAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "self-tracking shown for session=$sessionId")
        }
    }

    fun markSelfTrackingDismissed() {
        val sessionId = activeSessionId ?: return
        CoroutineScope(Dispatchers.IO).launch {
            sessionRepo.markLatestInterventionDismissed(
                sessionId = sessionId,
                dismissedAtMs = System.currentTimeMillis()
            )
            Log.d("SESSION", "self-tracking dismissed for session=$sessionId")
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

        if (activeSessionId != null && activeApp != packageName) {
            Log.d("SESSION", "switching target app from $activeApp to $packageName; ending previous session first")
            endCurrentSessionLocked(launchContextValidation = false)
        }

        val locationAtStart = ContextDetector.toLocationContextType(ctx)

        val record = SessionRecord(
            participantId = studyRepo.getCurrentStudySnapshot()?.participantId ?: return,
            targetAppPackage = packageName,
            openedAtMs = System.currentTimeMillis(),
            studyGroup = studyRepo.getCurrentStudySnapshot()?.studyGroup ?: return,
            studyWeek = studyRepo.getCurrentStudySnapshot()?.studyWeek ?: return,
            activeInterventionType = activeInterventionType,
            detectedContextAtStart = locationAtStart,
            goalsCountAtSessionStart = goalsCount
        )

        activeSessionId = record.sessionId
        activeApp = packageName
        activeSessionOpenedAtMs = record.openedAtMs
        activeLocationAtStart = locationAtStart
        activeIntervention = activeInterventionType
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
        if (activeInterventionType == InterventionType.SELF_TRACKING) {
            withContext(Dispatchers.Main.immediate) {
                scheduleSelfTrackingTrigger()
            }
        }

        try {
            sessionRepo.createSession(record)
            Log.d("SESSION", "started session ${record.sessionId} for $packageName")
        } catch (e: Exception) {
            activeSessionId = null
            activeApp = null
            activeSessionOpenedAtMs = null
            activeLocationAtStart = null
            activeIntervention = null
            designFrictionShownForCurrentSession = false
            designFrictionLaunchInProgress = false
            Log.e("SESSION", "failed to start session for $packageName", e)
        }
    }

    private suspend fun endSessionIfRunningLocked() {
        val id = activeSessionId ?: return

        try {
            if (shouldSkipAutomaticEndForCurrentForeground()) {
                Log.d("SESSION", "skipping automatic end because current foreground still belongs to the active session flow")
                return
            }
            endCurrentSessionLocked(launchContextValidation = true)
        } catch (e: Exception) {
            Log.e("SESSION", "failed to end session $id", e)
        }
    }

    private suspend fun endCurrentSessionLocked(launchContextValidation: Boolean) {
        val id = activeSessionId ?: return

        try {
            val closedAtMs = System.currentTimeMillis()
            val openedAtMs = activeSessionOpenedAtMs
            val durationMs = openedAtMs?.let { closedAtMs - it }

            if (launchContextValidation &&
                !contextValidationHandledForActiveSession &&
                (durationMs == null || durationMs >= minSessionDurationForContextValidationMs)
            ) {
                withContext(Dispatchers.Main.immediate) {
                    launchContextValidationForSession(id)
                }
            } else {
                Log.d(
                    "SESSION",
                    "skipping context validation (requested=$launchContextValidation alreadyHandled=$contextValidationHandledForActiveSession, durationMs=$durationMs)"
                )
            }

            val bedtimeStart = bedtimeRepo.bedtime.first()
            val submittedLocations = locationsRepo.locations.first()
            val activeGeofences = GeofenceContextStore.activeGeofenceIds.value

            val detectedContextEnd = ContextDetector.detect(
                bedtimeStart = bedtimeStart,
                activeGeofenceIds = activeGeofences,
                submittedLocations = submittedLocations
            )

            val locationContextAtEnd =
                if (detectedContextEnd == DetectedContext.NONE) {
                    null
                } else {
                    ContextDetector.toLocationContextType(detectedContextEnd)
                }

            if (activeIntervention == InterventionType.SELF_TRACKING) {
                val openedAt = activeSessionOpenedAtMs ?: closedAtMs
                usageRepo.recordFinishedSession(
                    startMs = openedAt,
                    endMs = closedAtMs,
                    contextType = activeLocationAtStart
                )
            }

            sessionRepo.closeSession(
                sessionId = id,
                closedAtMs = closedAtMs,
                detectedContextAtEnd = locationContextAtEnd
            )
            Log.d("SESSION", "ended session $id")
        } finally {
            resetActiveSessionState()
        }
    }

    private fun resetActiveSessionState() {
        cancelPendingEndSession()
        activeSessionId = null
        activeApp = null
        activeSessionOpenedAtMs = null
        activeLocationAtStart = null
        activeIntervention = null
        contextValidationHandledForActiveSession = false
        interventionUiVisible = false
        clearInterventionLaunchGuard()
        designFrictionShownForCurrentSession = false
        designFrictionLaunchInProgress = false
        cancelGoalAdvancementTrigger()
        cancelSelfTrackingTrigger()
        dailyGoalsPromptLaunchInProgress = false
    }

    private fun shouldSkipAutomaticEndForCurrentForeground(): Boolean {
        if (shouldSkipEndBecauseReturningToTarget()) return true
        if (shouldSkipEndBecauseInterventionLaunchInProgress()) return true

        val foreground = lastForegroundPackage ?: return false
        if (foreground == context.packageName) return true

        val currentActiveApp = activeApp
        if (foreground == currentActiveApp) return true

        return false
    }

    private fun shouldSkipEndBecauseReturningToTarget(): Boolean {
        val pending = pendingReturnToTargetAppPackage ?: return false
        val active = activeApp ?: return false
        if (pending != active) return false

        val previous = previousForegroundPackage
        val cameDirectlyFromOurApp = previous == context.packageName
        if (!cameDirectlyFromOurApp) return false

        val elapsed = System.currentTimeMillis() - pendingReturnToTargetTimestampMs
        return elapsed in 0..pendingReturnToTargetWindowMs
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
        clearInterventionLaunchGuard()
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

    private fun ensureInterventionStateForActiveSession(interventionType: InterventionType) {
        activeIntervention = interventionType
        when (interventionType) {
            InterventionType.GOAL_ADVANCEMENT -> {
                cancelSelfTrackingTrigger()
                if (goalAdvancementRunnable == null) {
                    scheduleGoalAdvancementTrigger()
                }
            }
            InterventionType.SELF_TRACKING -> {
                cancelGoalAdvancementTrigger()
                if (selfTrackingRunnable == null) {
                    scheduleSelfTrackingTrigger()
                }
            }
            InterventionType.DESIGN_FRICTION -> {
                cancelGoalAdvancementTrigger()
                cancelSelfTrackingTrigger()
            }
        }
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

    fun scheduleSelfTrackingTrigger() {
        cancelSelfTrackingTrigger()
        val targetPackage = activeApp ?: return
        if (activeSessionId == null) return
        selfTrackingRunnable = Runnable {
            if (activeSessionId != null &&
                activeApp == targetPackage &&
                activeIntervention == InterventionType.SELF_TRACKING
            ) {
                maybeLaunchSelfTracking()
            }
        }
        mainHandler.postDelayed(selfTrackingRunnable!!, selfTrackingDelayMs)
        Log.d("SESSION", "scheduled self-tracking in 15 min for $targetPackage")
    }

    fun cancelSelfTrackingTrigger() {
        selfTrackingRunnable?.let { mainHandler.removeCallbacks(it) }
        selfTrackingRunnable = null
    }

    /** Called after user chooses "Continue" so we show the self-tracking dialog again after another interval. */
    fun scheduleNextSelfTrackingTrigger() {
        mainHandler.post { scheduleSelfTrackingTrigger() }
    }

    private fun maybeLaunchGoalAdvancement() {
        val targetPackage = activeApp ?: return
        val sessionId = activeSessionId ?: return
        val openedAtMs = activeSessionOpenedAtMs ?: System.currentTimeMillis()
        beginInterventionLaunch()
        val intent = Intent(context, GoalAdvancementActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(GoalAdvancementActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(GoalAdvancementActivity.EXTRA_SESSION_ID, sessionId)
            putExtra(GoalAdvancementActivity.EXTRA_SESSION_OPENED_AT_MS, openedAtMs)
        }
        context.startActivity(intent)
        Log.d("SESSION", "launched GoalAdvancementActivity for $targetPackage")
    }

    private fun maybeLaunchSelfTracking() {
        val targetPackage = activeApp ?: return
        val openedAt = activeSessionOpenedAtMs ?: System.currentTimeMillis()
        val contextType = activeLocationAtStart
        val sessionId = activeSessionId ?: return
        beginInterventionLaunch()
        val intent = Intent(context, SelfTrackingActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(SelfTrackingActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(SelfTrackingActivity.EXTRA_SESSION_OPENED_AT_MS, openedAt)
            putExtra(SelfTrackingActivity.EXTRA_SESSION_CONTEXT_TYPE, contextType?.name)
            putExtra(SelfTrackingActivity.EXTRA_SESSION_ID, sessionId)
        }
        context.startActivity(intent)
        Log.d("SESSION", "launched SelfTrackingActivity for $targetPackage")
    }

    private fun maybeLaunchDesignFriction() {
        if (!shouldShowDesignFriction() || designFrictionLaunchInProgress) return
        val targetPackage = activeApp ?: return
        val sessionId = activeSessionId ?: return

        // Prevent a second FrictionActivity if accessibility events fire again before onCreate runs.
        designFrictionLaunchInProgress = true
        beginInterventionLaunch()

        val intent = Intent(context, FrictionActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(FrictionActivity.EXTRA_TARGET_PACKAGE, targetPackage)
            putExtra(FrictionActivity.EXTRA_SESSION_ID, sessionId)
        }

        context.startActivity(intent)
    }
}
