package com.karthikmacherla.metrotracker.tracking

import com.karthikmacherla.metrotracker.MetroData
import com.karthikmacherla.metrotracker.core.LocationFix
import com.karthikmacherla.metrotracker.core.Route
import com.karthikmacherla.metrotracker.core.TripProgress
import com.karthikmacherla.metrotracker.core.TripTracker
import com.karthikmacherla.metrotracker.core.estimateArrivalMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveTrip(
    val route: Route,
    val startedAtMillis: Long,
    val progress: TripProgress,
    /** When the latest progress was computed; the arrival estimate counts from here. */
    val progressAtMillis: Long,
    val lastFix: LocationFix? = null,
    /** Set the first time the rider reaches the destination. */
    val arrivedAtMillis: Long? = null,
) {
    /** See [estimateArrivalMillis]: slides later while waiting to depart, counts down once riding. */
    fun estimatedArrivalMillis(nowMillis: Long): Long = estimateArrivalMillis(progress, progressAtMillis, nowMillis)

    /** Speed of the latest fix in km/h, if the phone reported one. */
    val speedKmh: Int? get() = lastFix?.speedMps?.let { (it * 3.6f).toInt() }
}

/**
 * The running trip, if any, shared by the tracking service (which writes it) and the
 * screens (which observe it). Lives as long as the app process does.
 *
 * Updates come from the service's background thread and from taps on the main thread,
 * so changes go through one lock.
 */
object TripRepository {
    private val _activeTrip = MutableStateFlow<ActiveTrip?>(null)
    val activeTrip: StateFlow<ActiveTrip?> = _activeTrip.asStateFlow()

    private var tracker: TripTracker? = null
    private val lock = Any()

    fun start(route: Route, nowMillis: Long) {
        synchronized(lock) {
            val newTracker = TripTracker(MetroData.network, route)
            tracker = newTracker
            _activeTrip.value = ActiveTrip(route, nowMillis, newTracker.progress, nowMillis)
        }
    }

    fun recordFix(fix: LocationFix, nowMillis: Long) {
        synchronized(lock) {
            val trip = _activeTrip.value ?: return
            val progress = tracker?.update(fix) ?: return
            publish(trip.copy(lastFix = fix), progress, nowMillis)
        }
    }

    /** The rider tapped the station they're at. */
    fun correctTo(stationIndex: Int, nowMillis: Long) {
        synchronized(lock) {
            val trip = _activeTrip.value ?: return
            val progress = tracker?.correctTo(stationIndex) ?: return
            publish(trip, progress, nowMillis)
        }
    }

    fun end() {
        synchronized(lock) {
            tracker = null
            _activeTrip.value = null
        }
    }

    private fun publish(trip: ActiveTrip, progress: TripProgress, nowMillis: Long) {
        _activeTrip.value = trip.copy(
            progress = progress,
            // Keep the previous estimate's clock when nothing moved, so the minutes count down.
            progressAtMillis = if (progress == trip.progress) trip.progressAtMillis else nowMillis,
            arrivedAtMillis = trip.arrivedAtMillis ?: nowMillis.takeIf { progress.arrived },
        )
    }
}
