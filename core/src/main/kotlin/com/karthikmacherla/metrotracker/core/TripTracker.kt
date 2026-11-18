package com.karthikmacherla.metrotracker.core

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One location reading, in the units Android reports. */
data class LocationFix(
    val lat: Double,
    val lng: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
    val speedMps: Float? = null,
)

/** Where the rider is on their route, as far as the tracker can tell. */
data class TripProgress(
    /** Stations of the route in riding order; an interchange station appears once. */
    val stationIds: List<String>,
    /** Index into [stationIds] of the station most recently reached. */
    val lastStationIndex: Int,
    /** True while the rider is at [lastStationIndex] (train in or near the platform). */
    val atStation: Boolean,
    /** Share of the route's length covered, 0.0 to 1.0. */
    val fraction: Double,
    /** Estimated seconds still to ride, including any remaining interchange walks. */
    val remainingSeconds: Int,
    /** True once the rider is at the destination. */
    val arrived: Boolean,
) {
    val nextStationIndex: Int get() = if (arrived) stationIds.lastIndex else lastStationIndex + 1
    val nextStationId: String get() = stationIds[nextStationIndex]
    /** Stations still to reach, counting the destination. */
    val stopsLeft: Int get() = stationIds.lastIndex - lastStationIndex
    val percent: Int get() = (fraction * 100).roundToInt().coerceIn(0, 100)
}

/**
 * Turns location fixes into progress along a route ("station detection").
 *
 * Each fix is projected onto the straight lines between consecutive stations, and the
 * rider's position is the distance along the route. Position only moves forward: GPS on a
 * train jumps around, and a rider never rides backwards on a planned trip. Wrong guesses
 * are fixed by [correctTo] (the rider taps their station).
 *
 * Pure Kotlin with no clock of its own, so recorded rides can be replayed in tests.
 */
class TripTracker(
    private val network: MetroNetwork,
    private val route: Route,
    private val travelTime: TravelTimeModel = TravelTimeModel(),
) {
    private val stations: List<Station> = buildList {
        route.legs.forEachIndexed { index, leg ->
            val ids = if (index == 0) leg.stationIds else leg.stationIds.drop(1)
            ids.forEach { add(network.station(it)) }
        }
    }
    val stationIds: List<String> = stations.map { it.id }

    /** Distance along the route of each station, in metres. */
    private val stationPosition: DoubleArray = DoubleArray(stations.size).also { pos ->
        for (i in 1 until stations.size) pos[i] = pos[i - 1] + distanceMeters(stations[i - 1], stations[i])
    }
    private val totalLength = stationPosition.last()

    /** Ride time of each segment i (station i to i + 1), with the transfer walk added before a new leg. */
    private val segmentSeconds: IntArray = IntArray(stations.size - 1) { i ->
        val transfer = if (i > 0 && stations[i].id in route.interchangeStationIds) network.transferSecondsAt(stations[i].id) else 0
        transfer + travelTime.segmentSeconds(stations[i], stations[i + 1])
    }

    private var position = 0.0
    private var lastAcceptedMillis: Long? = null

    var progress: TripProgress = progressAt(0.0)
        private set

    /** Feeds one fix; returns the (possibly unchanged) progress. */
    fun update(fix: LocationFix): TripProgress {
        if (fix.accuracyMeters > MAX_ACCURACY_M) return progress

        val candidate = project(fix) ?: return progress
        val previousMillis = lastAcceptedMillis
        if (previousMillis != null) {
            // A train can't cover more than this since the last good fix; anything further is a GPS jump.
            val seconds = max(0L, fix.timeMillis - previousMillis) / 1000.0
            val reach = MAX_TRAIN_SPEED_MPS * seconds + fix.accuracyMeters + STATION_RADIUS_M
            if (candidate - position > reach) return progress
        }
        lastAcceptedMillis = fix.timeMillis
        if (candidate > position) position = candidate
        progress = progressAt(position)
        return progress
    }

    /** The rider says they're at [stationIndex]; trust them over GPS. */
    fun correctTo(stationIndex: Int): TripProgress {
        position = stationPosition[stationIndex.coerceIn(0, stations.lastIndex)]
        progress = progressAt(position)
        return progress
    }

    /** Distance along the route of the closest point to [fix], or null if the fix is too far from the line. */
    private fun project(fix: LocationFix): Double? {
        var best: Double? = null
        var bestCross = Double.MAX_VALUE
        for (i in 0 until stations.size - 1) {
            val (along, cross) = projectOnSegment(fix, stations[i], stations[i + 1])
            if (cross < bestCross) {
                bestCross = cross
                best = stationPosition[i] + along
            }
        }
        val allowed = max(MAX_OFF_ROUTE_M, 2.0 * fix.accuracyMeters)
        return best?.takeIf { bestCross <= allowed }
    }

    private fun progressAt(position: Double): TripProgress {
        // The last station whose platform we've reached (or come within STATION_RADIUS_M of).
        var last = 0
        for (i in stations.indices) if (stationPosition[i] - STATION_RADIUS_M <= position) last = i
        val atStation = position <= stationPosition[last] + STATION_RADIUS_M
        val arrived = last == stations.lastIndex

        var remaining = 0.0
        if (!arrived) {
            val segLength = stationPosition[last + 1] - stationPosition[last]
            val done = ((position - stationPosition[last]) / segLength).coerceIn(0.0, 1.0)
            remaining = segmentSeconds[last] * (1 - done)
            for (i in last + 1 until segmentSeconds.size) remaining += segmentSeconds[i]
        }
        return TripProgress(
            stationIds = stationIds,
            lastStationIndex = last,
            atStation = atStation,
            fraction = if (arrived || totalLength == 0.0) 1.0 else (position / totalLength).coerceIn(0.0, 1.0),
            remainingSeconds = remaining.roundToInt(),
            arrived = arrived,
        )
    }

    companion object {
        /** Fixes worse than this are mostly cell-tower guesses on a moving train; skip them. */
        const val MAX_ACCURACY_M = 120f
        /** Within this distance of a station counts as being at it. */
        const val STATION_RADIUS_M = 150.0
        /** Fixes further than this from the route (e.g. walking to the station) are ignored. */
        const val MAX_OFF_ROUTE_M = 250.0
        /** Comfortably above the metro's top speed of 80 km/h. */
        const val MAX_TRAIN_SPEED_MPS = 25.0
    }
}

/**
 * Projects a point onto segment a→b on a local flat map (fine over a few km).
 * Returns (metres along the segment, clamped to it; metres from the segment).
 */
private fun projectOnSegment(fix: LocationFix, a: Station, b: Station): Pair<Double, Double> {
    val metresPerDegLat = 111_320.0
    val metresPerDegLng = metresPerDegLat * cos(Math.toRadians(a.lat))
    val bx = (b.lng - a.lng) * metresPerDegLng
    val by = (b.lat - a.lat) * metresPerDegLat
    val px = (fix.lng - a.lng) * metresPerDegLng
    val py = (fix.lat - a.lat) * metresPerDegLat
    val lengthSq = bx * bx + by * by
    val t = if (lengthSq == 0.0) 0.0 else ((px * bx + py * by) / lengthSq).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return t * sqrt(lengthSq) to sqrt(dx * dx + dy * dy)
}
