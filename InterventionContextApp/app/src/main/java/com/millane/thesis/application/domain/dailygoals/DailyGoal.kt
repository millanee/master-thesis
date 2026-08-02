package com.millane.thesis.application.domain.dailygoals

import kotlinx.serialization.Serializable

@Serializable
data class DailyGoal(
    val id: String,
    val text: String
)