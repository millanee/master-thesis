package com.millane.thesis.application.data.study

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.study.ContextValidationStatus
import com.millane.thesis.application.study.InterventionAttempt
import com.millane.thesis.application.study.SessionRecord
import kotlinx.coroutines.tasks.await

class FirestoreSessionRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val sessionsCol = db.collection("sessions")

    suspend fun createSession(session: SessionRecord) {
        sessionsCol
            .document(session.sessionId)
            .set(session, SetOptions.merge())
            .await()
    }

    suspend fun closeSession(
        sessionId: String,
        closedAtMs: Long,
        detectedContextAtEnd: LocationContextType?
    ) {
        val doc = sessionsCol.document(sessionId)
        val snap = doc.get().await()

        val openedAtMs = snap.getLong("openedAtMs") ?: closedAtMs
        val durationMs = (closedAtMs - openedAtMs).coerceAtLeast(0L)

        doc.set(
            mapOf(
                "closedAtMs" to closedAtMs,
                "durationMs" to durationMs,
                "detectedContextAtEnd" to detectedContextAtEnd?.name
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun addInterventionShown(
        sessionId: String,
        shownAtMs: Long
    ) {
        val doc = sessionsCol.document(sessionId)
        val snap = doc.get().await()

        val currentAttempts = parseAttempts(snap.get("interventionAttempts"))
        val updated = currentAttempts + InterventionAttempt(
            shownAtMs = shownAtMs
        )

        doc.set(
            mapOf("interventionAttempts" to updated),
            SetOptions.merge()
        ).await()
    }

    suspend fun markLatestInterventionDismissed(
        sessionId: String,
        dismissedAtMs: Long
    ) {
        val doc = sessionsCol.document(sessionId)
        val snap = doc.get().await()

        val currentAttempts = parseAttempts(snap.get("interventionAttempts"))
        if (currentAttempts.isEmpty()) return

        val lastIndex = currentAttempts.lastIndex
        val updated = currentAttempts.toMutableList()
        val last = updated[lastIndex]

        updated[lastIndex] = last.copy(
            dismissedAtMs = dismissedAtMs
        )

        doc.set(
            mapOf("interventionAttempts" to updated),
            SetOptions.merge()
        ).await()
    }

    suspend fun markLatestInterventionClosedApp(
        sessionId: String,
        closedAtMs: Long
    ) {
        val doc = sessionsCol.document(sessionId)
        val snap = doc.get().await()

        val currentAttempts = parseAttempts(snap.get("interventionAttempts"))
        if (currentAttempts.isEmpty()) return

        val lastIndex = currentAttempts.lastIndex
        val updated = currentAttempts.toMutableList()
        val last = updated[lastIndex]

        updated[lastIndex] = last.copy(
            appClosedViaIntervention = true,
            appClosedViaInterventionAtMs = closedAtMs
        )

        doc.set(
            mapOf("interventionAttempts" to updated),
            SetOptions.merge()
        ).await()
    }

    suspend fun setContextValidationStatus(
        sessionId: String,
        status: ContextValidationStatus
    ) {
        sessionsCol.document(sessionId)
            .set(
                mapOf("contextValidationStatus" to status.name),
                SetOptions.merge()
            )
            .await()
    }

    private fun parseAttempts(raw: Any?): List<InterventionAttempt> {
        val list = raw as? List<*> ?: return emptyList()

        return list.mapNotNull { item ->
            val map = item as? Map<*, *> ?: return@mapNotNull null

            val shownAtMs = (map["shownAtMs"] as? Number)?.toLong() ?: return@mapNotNull null
            val dismissedAtMs = (map["dismissedAtMs"] as? Number)?.toLong()
            val appClosedViaIntervention = map["appClosedViaIntervention"] as? Boolean ?: false
            val appClosedViaInterventionAtMs =
                (map["appClosedViaInterventionAtMs"] as? Number)?.toLong()

            InterventionAttempt(
                shownAtMs = shownAtMs,
                dismissedAtMs = dismissedAtMs,
                appClosedViaIntervention = appClosedViaIntervention,
                appClosedViaInterventionAtMs = appClosedViaInterventionAtMs
            )
        }
    }
}