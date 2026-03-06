package com.millane.thesis.application.study

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
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

    fun onForegroundAppChanged(packageName: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sessionMutex.withLock {
                Log.d("SESSION", "Foreground app changed: $packageName")

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

                // Wenn für genau diese App schon eine Session läuft -> nichts tun
                if (activeSessionId != null && activeApp == packageName) {
                    Log.d("SESSION", "session already active for app = $packageName")
                    return@withLock
                }

                val bedtimeStart = bedtimeRepo.bedtime.first()
                val goals = goalsRepo.goals.first()
                val submittedLocations = locationsRepo.locations.first()
                val activeGeofences = GeofenceContextStore.activeGeofenceIds.value

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
                    goalsCount = goals.size
                )
            }
        }
    }

    private suspend fun startSessionLocked(
        packageName: String,
        ctx: DetectedContext,
        goalsCount: Int
    ) {
        if (activeSessionId != null && activeApp == packageName) {
            Log.d("SESSION", "start skipped because session is already active for $packageName")
            return
        }

        val snapshot = studyRepo.getCurrentStudySnapshot() ?: return

        val record = SessionRecord(
            participantId = snapshot.participantId,
            targetAppPackage = packageName,
            openedAtMs = System.currentTimeMillis(),
            studyGroup = snapshot.studyGroup,
            studyWeek = snapshot.studyWeek,
            activeInterventionType = snapshot.activeInterventionType,
            detectedContextAtStart = ContextDetector.toLocationContextType(ctx),
            goalsCountAtSessionStart = goalsCount
        )

        activeSessionId = record.sessionId
        activeApp = packageName

        try {
            sessionRepo.createSession(record)
            Log.d("SESSION", "started session ${record.sessionId} for $packageName")
        } catch (e: Exception) {
            activeSessionId = null
            activeApp = null
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
        }
    }
}