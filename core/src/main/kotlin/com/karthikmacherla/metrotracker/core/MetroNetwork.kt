package com.karthikmacherla.metrotracker.core

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Station(val id: String, val name: String, val lat: Double, val lng: Double)

/** A metro line; [stationIds] are in order from one end of the line to the other. */
data class Line(val id: String, val name: String, val color: String, val stationIds: List<String>)

/** An interchange station and the time it takes to change lines there (walk + wait). */
data class Transfer(val stationId: String, val seconds: Int)

/**
 * The whole metro network, immutable and validated on construction.
 * Which lines a station is on is derived from [Line.stationIds], not stored separately.
 */
class MetroNetwork(
    val name: String,
    val lines: List<Line>,
    stations: List<Station>,
    transfers: List<Transfer>,
) {
    val stations: Map<String, Station> = stations.associateBy { it.id }
    private val linesById: Map<String, Line> = lines.associateBy { it.id }
    private val transferSeconds: Map<String, Int> = transfers.associate { it.stationId to it.seconds }
    private val linesByStation: Map<String, List<Line>> =
        lines.flatMap { line -> line.stationIds.map { it to line } }
            .groupBy({ it.first }, { it.second })

    init {
        require(this.stations.size == stations.size) { "Duplicate station ids" }
        for (line in lines) {
            require(line.stationIds.size >= 2) { "Line ${line.id} needs at least 2 stations" }
            require(line.stationIds.toSet().size == line.stationIds.size) { "Line ${line.id} repeats a station" }
            line.stationIds.forEach { require(it in this.stations) { "Line ${line.id} has unknown station $it" } }
        }
        this.stations.keys.forEach { require(it in linesByStation) { "Station $it is not on any line" } }
        transfers.forEach {
            require((linesByStation[it.stationId]?.size ?: 0) > 1) { "Transfer at ${it.stationId}, which is on only one line" }
        }
    }

    fun station(id: String): Station = stations[id] ?: throw IllegalArgumentException("Unknown station $id")

    fun line(id: String): Line = linesById[id] ?: throw IllegalArgumentException("Unknown line $id")

    fun linesAt(stationId: String): List<Line> = linesByStation[stationId].orEmpty()

    fun isInterchange(stationId: String): Boolean = linesAt(stationId).size > 1

    /** Seconds needed to change lines at [stationId]; a default applies where none is given. */
    fun transferSecondsAt(stationId: String): Int = transferSeconds[stationId] ?: DEFAULT_TRANSFER_SECONDS

    companion object {
        const val DEFAULT_TRANSFER_SECONDS = 300
    }
}

/** Great-circle distance in metres between two stations. */
fun distanceMeters(a: Station, b: Station): Double {
    val earthRadius = 6_371_000.0
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = lat2 - lat1
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * earthRadius * asin(sqrt(h))
}
