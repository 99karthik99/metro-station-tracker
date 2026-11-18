package com.karthikmacherla.metrotracker.core

/**
 * Decides when to buzz the rider "get off at the next stop" (D10, heads-up part): once per
 * get-off station (each interchange and the destination), when the train reaches the
 * station before it. That leaves one stop's ride, about 2 minutes, to get to the doors.
 */
class GetOffAlerts(route: Route) {
    private val stationIds: List<String> = buildList {
        route.legs.forEachIndexed { index, leg -> addAll(if (index == 0) leg.stationIds else leg.stationIds.drop(1)) }
    }
    private val getOffIndices: List<Int> =
        (route.interchangeStationIds + route.toStationId).map { stationIds.indexOf(it) }
    private val alerted = mutableSetOf<Int>()

    /**
     * Returns the index (into the route's station list) of the get-off station to alert for
     * now, or null. Each station alerts at most once, even if the rider corrects backwards.
     */
    fun check(progress: TripProgress): Int? {
        if (progress.arrived) return null
        // Skip stops that come up at the very start: a one-stop trip needs no reminder.
        val due = getOffIndices.firstOrNull { index ->
            index !in alerted &&
                progress.lastStationIndex > 0 &&
                progress.lastStationIndex >= index - 1 &&
                progress.lastStationIndex < index
        } ?: return null
        // Mark any earlier, never-alerted stops as done too, so a GPS skip can't fire a stale alert later.
        getOffIndices.filter { it <= due }.forEach { alerted += it }
        return due
    }
}
