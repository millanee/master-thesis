package com.millane.thesis.application.study

import com.millane.thesis.application.domain.location.LocationContextType
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SessionRecord(
    val sessionId: String = UUID.randomUUID().toString(),
    val participantId: String,

    val targetAppPackage: String,

    val openedAtMs: Long,
    val closedAtMs: Long? = null,
    val durationMs: Long? = null,
    val responsiveness: Long? = null,

    val studyGroup: StudyGroup,
    val studyWeek: Int,
    val activeInterventionType: InterventionType,

    val detectedContextAtStart: LocationContextType? = null,
    val detectedContextAtEnd: LocationContextType? = null,

    val contextValidationStatus: ContextValidationStatus? = null,

    val goalsCountAtSessionStart: Int = 0,

    val interventionAttempts: List<InterventionAttempt> = emptyList()
)
