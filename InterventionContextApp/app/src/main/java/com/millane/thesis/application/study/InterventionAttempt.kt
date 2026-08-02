package com.millane.thesis.application.study

import kotlinx.serialization.Serializable

@Serializable
data class InterventionAttempt(
    val shownAtMs: Long,
    val dismissedAtMs: Long? = null,
    val appClosedViaIntervention: Boolean = false,
    val appClosedViaInterventionAtMs: Long? = null,

    // Reactance questionnaire
    val reactanceAnsweredAtMs: Long? = null,
    val reactanceResponses: List<Int> = emptyList(),
    val reactanceMeanScore: Double? = null
)