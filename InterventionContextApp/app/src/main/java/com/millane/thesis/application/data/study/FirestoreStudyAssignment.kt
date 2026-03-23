package com.millane.thesis.application.data.study

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.millane.thesis.application.study.StudyGroup
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirestoreStudyAssignment(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val metaDoc = db.collection("meta").document("assignment")
    private val participantsCol = db.collection("participants")

    /**
     * Atomically assigns the next balanced group (A/B/C),
     * stores participant record, and returns the assigned group.
     */
    suspend fun assignGroup(participantId: String, startDateMs: Long): StudyGroup {
        val studyStartDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(startDateMs))

        val assigned = db.runTransaction { tx ->
            val snap = tx.get(metaDoc)

            val countA = (snap.getLong("countA") ?: 0L)
            val countB = (snap.getLong("countB") ?: 0L)
            val countC = (snap.getLong("countC") ?: 0L)

            val group = when {
                countA <= countB && countA <= countC -> StudyGroup.A
                countB <= countA && countB <= countC -> StudyGroup.B
                else -> StudyGroup.C
            }

            // update counters
            val updates = when (group) {
                StudyGroup.A -> mapOf("countA" to countA + 1)
                StudyGroup.B -> mapOf("countB" to countB + 1)
                StudyGroup.C -> mapOf("countC" to countC + 1)
            }
            tx.set(metaDoc, updates, SetOptions.merge())

            // store participant doc
            val pDoc = participantsCol.document(participantId)
            tx.set(
                pDoc,
                mapOf(
                    "participantId" to participantId,
                    "group" to group.name,
                    "startDateMs" to startDateMs,
                    "studyStartDate" to studyStartDate,
                    "createdAtMs" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )

            group
        }.await()

        return assigned
    }
}
