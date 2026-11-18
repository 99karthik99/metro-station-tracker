package com.karthikmacherla.metrotracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetOffAlertsTest {
    private val network = NetworkLoader.loadHyderabad()
    private val planner = RoutePlanner(network)

    private fun loadTrace(name: String): List<LocationFix> =
        javaClass.classLoader.getResourceAsStream(name)!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && it[0].isDigit() }
            .map { line ->
                val p = line.split(',')
                LocationFix(p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[0].toLong(), p[4].toFloatOrNull())
            }

    @Test
    fun `recorded ride buzzes once, at the stop before the destination`() {
        val route = planner.plan("tarnaka", "raidurg")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)

        val fired = mutableListOf<Pair<String, String>>() // (alerted station, where the rider was)
        for (fix in loadTrace("traces/20261118-091129_tarnaka_raidurg.csv")) {
            val progress = tracker.update(fix)
            alerts.check(progress)?.let { fired += progress.stationIds[it] to progress.stationIds[progress.lastStationIndex] }
        }

        assertEquals(listOf("raidurg" to "hitec-city"), fired)
    }

    @Test
    fun `interchange and destination each buzz once`() {
        val route = planner.plan("miyapur", "uppal")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)
        val ids = tracker.stationIds

        val fired = ids.indices.mapNotNull { i -> alerts.check(tracker.correctTo(i))?.let { ids[it] } }

        assertEquals(listOf("ameerpet", "uppal"), fired)
    }

    @Test
    fun `one stop trip does not buzz at the start`() {
        val route = planner.plan("tarnaka", "mettuguda")
        val tracker = TripTracker(network, route)

        assertNull(GetOffAlerts(route).check(tracker.progress))
    }

    @Test
    fun `correcting backwards does not buzz again`() {
        val route = planner.plan("tarnaka", "raidurg")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)
        val beforeLast = tracker.stationIds.lastIndex - 1

        assertEquals(tracker.stationIds.lastIndex, alerts.check(tracker.correctTo(beforeLast)))
        alerts.check(tracker.correctTo(beforeLast - 3))
        assertNull(alerts.check(tracker.correctTo(beforeLast)))
    }
}
