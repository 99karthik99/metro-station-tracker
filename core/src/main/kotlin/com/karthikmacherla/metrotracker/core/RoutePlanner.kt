package com.karthikmacherla.metrotracker.core

import java.util.PriorityQueue
import kotlin.math.roundToInt

/**
 * Estimates ride time between adjacent stations from straight-line distance.
 * Placeholder constants: calibrate them against real rides (PRD §7).
 */
data class TravelTimeModel(
    val dwellSeconds: Int = 25,
    val cruiseSpeedKmh: Double = 45.0,
) {
    fun segmentSeconds(a: Station, b: Station): Int =
        dwellSeconds + (distanceMeters(a, b) / (cruiseSpeedKmh / 3.6)).roundToInt()
}

/** One continuous ride on a single line. [stationIds] runs from boarding to alighting station. */
data class RouteLeg(
    val line: Line,
    val stationIds: List<String>,
    /** End station of the line in the direction of travel, as shown on platform signs ("towards Nagole"). */
    val towardsStationId: String,
    val rideSeconds: Int,
) {
    val fromStationId: String get() = stationIds.first()
    val toStationId: String get() = stationIds.last()
    val stops: Int get() = stationIds.size - 1
}

data class Route(val legs: List<RouteLeg>, val totalSeconds: Int) {
    val fromStationId: String get() = legs.first().fromStationId
    val toStationId: String get() = legs.last().toStationId
    val stops: Int get() = legs.sumOf { it.stops }
    val changes: Int get() = legs.size - 1
    val interchangeStationIds: List<String> get() = legs.drop(1).map { it.fromStationId }
}

/**
 * Finds the fastest route between two stations, changing lines where that is faster.
 *
 * Runs Dijkstra over (station, line) states: riding to a neighbouring station costs the
 * segment time, and switching line at an interchange costs that station's transfer time.
 * Tracking the line in the state is what lets interchanges carry a cost.
 */
class RoutePlanner(
    private val network: MetroNetwork,
    private val travelTime: TravelTimeModel = TravelTimeModel(),
) {
    private data class State(val stationId: String, val lineId: String)

    private class Entry(val state: State, val seconds: Int, val order: Long)

    fun plan(fromStationId: String, toStationId: String): Route {
        network.station(fromStationId)
        network.station(toStationId)
        require(fromStationId != toStationId) { "Start and destination are the same station" }

        val best = HashMap<State, Int>()
        val previous = HashMap<State, State>()
        var counter = 0L
        // Ties broken by insertion order, so results are deterministic.
        val queue = PriorityQueue<Entry>(compareBy<Entry>({ it.seconds }, { it.order }))

        for (line in network.linesAt(fromStationId)) {
            val start = State(fromStationId, line.id)
            best[start] = 0
            queue.add(Entry(start, 0, counter++))
        }

        while (queue.isNotEmpty()) {
            val entry = queue.poll()
            val state = entry.state
            if (entry.seconds > (best[state] ?: Int.MAX_VALUE)) continue
            if (state.stationId == toStationId) return buildRoute(state, previous, entry.seconds)

            for ((next, cost) in neighbours(state)) {
                val seconds = entry.seconds + cost
                if (seconds < (best[next] ?: Int.MAX_VALUE)) {
                    best[next] = seconds
                    previous[next] = state
                    queue.add(Entry(next, seconds, counter++))
                }
            }
        }
        throw IllegalStateException("No route from $fromStationId to $toStationId")
    }

    private fun neighbours(state: State): List<Pair<State, Int>> {
        val line = network.line(state.lineId)
        val index = line.stationIds.indexOf(state.stationId)
        val here = network.station(state.stationId)
        val result = mutableListOf<Pair<State, Int>>()
        for (nextIndex in listOf(index - 1, index + 1)) {
            val nextId = line.stationIds.getOrNull(nextIndex) ?: continue
            result += State(nextId, line.id) to travelTime.segmentSeconds(here, network.station(nextId))
        }
        for (other in network.linesAt(state.stationId)) {
            if (other.id != line.id) {
                result += State(state.stationId, other.id) to network.transferSecondsAt(state.stationId)
            }
        }
        return result
    }

    private fun buildRoute(end: State, previous: Map<State, State>, totalSeconds: Int): Route {
        val path = generateSequence(end) { previous[it] }.toList().reversed()

        // Split the path wherever the line changes; the interchange station ends one leg and starts the next.
        val legs = mutableListOf<RouteLeg>()
        var legStates = mutableListOf(path.first())
        for (state in path.drop(1)) {
            if (state.lineId == legStates.last().lineId) {
                legStates += state
            } else {
                legs += toLeg(legStates)
                legStates = mutableListOf(state)
            }
        }
        legs += toLeg(legStates)
        return Route(legs.filter { it.stops > 0 }, totalSeconds)
    }

    private fun toLeg(states: List<State>): RouteLeg {
        val line = network.line(states.first().lineId)
        val ids = states.map { it.stationId }
        val forward = ids.size < 2 || line.stationIds.indexOf(ids[1]) > line.stationIds.indexOf(ids[0])
        val rideSeconds = ids.zipWithNext { a, b ->
            travelTime.segmentSeconds(network.station(a), network.station(b))
        }.sum()
        return RouteLeg(
            line = line,
            stationIds = ids,
            towardsStationId = if (forward) line.stationIds.last() else line.stationIds.first(),
            rideSeconds = rideSeconds,
        )
    }
}
