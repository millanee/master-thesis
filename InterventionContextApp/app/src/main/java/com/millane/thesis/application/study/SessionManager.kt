package com.millane.thesis.application.study

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.firestore.FirebaseFirestore
import com.millane.thesis.application.FrictionActivity
import com.millane.thesis.application.data.apps.AppSelectionRepository
import com.millane.thesis.application.data.bedtime.BedtimeRepository
import com.millane.thesis.application.data.dailygoals.DailyGoalsRepository
import com.millane.thesis.application.data.location.LocationsRepository
import com.millane.thesis.application.data.study.FirestoreSessionRepository
import com.millane.thesis.application.data.study.StudyRepository
import com.millane.thesis.application.location.geofence.GeofenceContextStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
            maybeLaunchDesignFriction()
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
        }
    }

    private fun maybeLaunchDesignFriction() {
        if (!shouldShowDesignFriction() || designFrictionLaunchInProgress) return
        val targetPackage = activeApp ?: return

        // Prevent a second FrictionActivity if accessibility events fire again before onCreate runs.
        designFrictionLaunchInProgress = true

        val intent = Intent(context, FrictionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(FrictionActivity.EXTRA_TARGET_PACKAGE, targetPackage)
        }

        context.startActivity(intent)
    }
}