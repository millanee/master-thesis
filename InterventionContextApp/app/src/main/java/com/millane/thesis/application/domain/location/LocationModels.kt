package com.millane.thesis.application.domain.location

import java.util.UUID

enum class LocationContextType { WORK, HOME }

data class LocationEntry(
    val id: String = UUID.randomUUID().toString(),
    val contextType: LocationContextType,
    val displayAddress: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 120f,
    val placeId: String? = null
)