package com.karthikmacherla.metrotracker.tracking

import com.karthikmacherla.metrotracker.core.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One location reading from the phone. */
data class Fix(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val timeMillis: Long,
)

data class ActiveTrip(
    val route: Route,
    val startedAtMillis: Long,
    val lastFix: Fix? = null,
    val fixCount: Int = 0,
) {
    /** Scheduled arrival from the route estimate; replaced by live progress once station detection exists. */
    val estimatedArrivalMillis: Long get() = startedAtMillis + route.totalSeconds * 1000L
}

/**
 * The running trip, if any, shared by the tracking service (which writes it) and the
 * screens (which observe it). Lives as long as the app process does.
 */
object TripRepository {
    private val _activeTrip = MutableStateFlow<ActiveTrip?>(null)
    val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()

    fun start(route: Route, nowMillis: Long) {
        _activeTrip.value = ActiveTrip(route, nowMillis)
    }

    fun recordFix(fix: Fix) {
        _activeTrip.update { it?.copy(lastFix = fix, fixCount = it.fixCount + 1) }
    }

    fun end() {
        _activeTrip.value = null
    }
}
