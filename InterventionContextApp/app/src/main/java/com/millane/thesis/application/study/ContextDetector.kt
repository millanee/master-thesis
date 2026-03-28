package com.millane.thesis.application.study

import android.os.Build
import androidx.annotation.RequiresApi
import com.millane.thesis.application.domain.location.LocationContextType
import com.millane.thesis.application.domain.location.LocationEntry
import java.time.LocalTime

enum class DetectedContext {
    NONE,
    HOME,
    WORK,
    BEDTIME
}

object ContextDetector {

    @RequiresApi(Build.VERSION_CODES.O)
    fun detect(
        bedtimeStart: String?,
        activeGeofenceIds: Set<String>,
        submittedLocations: List<LocationEntry>,
        now: LocalTime = LocalTime.now()
    ): DetectedContext {

        val homeIds = submittedLocations
            .filter { it.contextType == LocationContextType.HOME }
            .map { it.id }
            .toSet()

        val workIds = submittedLocations
            .filter { it.contextType == LocationContextType.WORK }
            .map { it.id }
            .toSet()

        val isHome = activeGeofenceIds.any { it in homeIds }
        val isWork = activeGeofenceIds.any { it in workIds }

        return detectFromLocationPresence(
            bedtimeStart = bedtimeStart,
            isHome = isHome,
            isWork = isWork,
            now = now
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun detectFromLocationPresence(
        bedtimeStart: String?,
        isHome: Boolean,
        isWork: Boolean,
        now: LocalTime = LocalTime.now()
    ): DetectedContext {

        val bedtimeReached = bedtimeStart?.let {
            val parts = it.split(":")
            if (parts.size == 2) {
                val start = LocalTime.of(parts[0].toInt(), parts[1].toInt())
                val end = LocalTime.of(4, 0)

                if (start.isBefore(end)) {
                    !now.isBefore(start) && now.isBefore(end)
                } else {
                    !now.isBefore(start) || now.isBefore(end)
                }
            } else {
                false
            }
        } ?: false

        if (isHome && bedtimeReached) return DetectedContext.BEDTIME
        if (isWork) return DetectedContext.WORK
        if (isHome) return DetectedContext.HOME

        return DetectedContext.NONE
    }

    fun toLocationContextType(ctx: DetectedContext): LocationContextType? {
        return when (ctx) {
            DetectedContext.HOME -> LocationContextType.HOME
            DetectedContext.WORK -> LocationContextType.WORK
            DetectedContext.BEDTIME -> LocationContextType.BEDTIME
            DetectedContext.NONE -> null
        }
    }
}
