package com.karthikmacherla.metrotracker.core

/** A buzz for one get-off station: the heads-up one stop before, or on arrival there. */
data class GetOffAlert(val stationIndex: Int, val kind: Kind) {
    enum class Kind { NextStop, Arrived }
}

/**
 * Decides when to buzz the rider (D10): twice per get-off station (each interchange and the
 * destination). First when the train reaches the station before it, leaving one stop's ride,
 * about 2 minutes, to get to the doors. Then on arrival at the station itself.
 */
class GetOffAlerts(route: Route) {
    private val stationIds: List<String> = buildList {
        route.legs.forEachIndexed { index, leg -> addAll(if (index == 0) leg.stationIds else leg.stationIds.drop(1)) }
    }
    private val getOffIndices: List<Int> =
        (route.interchangeStationIds + route.toStationId).map { stationIds.indexOf(it) }
    private val headsUpDone = mutableSetOf<Int>()
    private val arrivalDone = mutableSetOf<Int>()

    /**
     * Returns the alert due now, or null. Each alert fires at most once, even if the rider
     * corrects backwards. If GPS skips ahead, alerts for stops already behind are dropped
     * rather than fired late.
     */
    fun check(progress: TripProgress): GetOffAlert? {
        val here = progress.lastStationIndex

        // Arrival wins over a heads-up for the same stop that never got its turn.
        val arrival = getOffIndices.firstOrNull { it !in arrivalDone && here == it && progress.atStation }
        if (arrival != null) {
            markDoneUpTo(arrival, arrivalDone)
            markDoneUpTo(arrival, headsUpDone)
            return GetOffAlert(arrival, GetOffAlert.Kind.Arrived)
        }
        // Passed without stopping (or GPS skipped it): nothing to say any more.
        getOffIndices.filter { it < here }.forEach { arrivalDone += it; headsUpDone += it }

        // Skip stops that come up at the very start: a one-stop trip needs no heads-up.
        val headsUp = getOffIndices.firstOrNull { it !in headsUpDone && here > 0 && here == it - 1 }
            ?: return null
        markDoneUpTo(headsUp, headsUpDone)
        return GetOffAlert(headsUp, GetOffAlert.Kind.NextStop)
    }

    private fun markDoneUpTo(index: Int, done: MutableSet<Int>) {
        getOffIndices.filter { it <= index }.forEach { done += it }
    }
}
