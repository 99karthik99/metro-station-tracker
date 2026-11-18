package com.karthikmacherla.metrotracker.core

import com.karthikmacherla.metrotracker.core.GetOffAlert.Kind.Arrived
import com.karthikmacherla.metrotracker.core.GetOffAlert.Kind.NextStop
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
    fun `recorded ride buzzes at the stop before the destination, then on arrival`() {
        val route = planner.plan("tarnaka", "raidurg")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)

        // (kind, alerted station, where the rider was)
        val fired = mutableListOf<Triple<GetOffAlert.Kind, String, String>>()
        for (fix in loadTrace("traces/20261118-091129_tarnaka_raidurg.csv")) {
            val progress = tracker.update(fix)
            alerts.check(progress)?.let {
                fired += Triple(it.kind, progress.stationIds[it.stationIndex], progress.stationIds[progress.lastStationIndex])
            }
        }

        assertEquals(
            listOf(Triple(NextStop, "raidurg", "hitec-city"), Triple(Arrived, "raidurg", "raidurg")),
            fired,
        )
    }

    @Test
    fun `interchange and destination each buzz twice`() {
        val route = planner.plan("miyapur", "uppal")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)
        val ids = tracker.stationIds

        val fired = ids.indices.mapNotNull { i -> alerts.check(tracker.correctTo(i))?.let { it.kind to ids[it.stationIndex] } }

        assertEquals(
            listOf(NextStop to "ameerpet", Arrived to "ameerpet", NextStop to "uppal", Arrived to "uppal"),
            fired,
        )
    }

    @Test
    fun `one stop trip only buzzes on arrival`() {
        val route = planner.plan("tarnaka", "mettuguda")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)

        assertNull(alerts.check(tracker.progress))
        assertEquals(GetOffAlert(1, Arrived), alerts.check(tracker.correctTo(1)))
    }

    @Test
    fun `skipping straight to arrival buzzes only for arrival`() {
        val route = planner.plan("tarnaka", "raidurg")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)
        val last = tracker.stationIds.lastIndex

        alerts.check(tracker.correctTo(3))
        assertEquals(GetOffAlert(last, Arrived), alerts.check(tracker.correctTo(last)))
        assertNull(alerts.check(tracker.correctTo(last)))
    }

    @Test
    fun `correcting backwards does not buzz again`() {
        val route = planner.plan("tarnaka", "raidurg")
        val tracker = TripTracker(network, route)
        val alerts = GetOffAlerts(route)
        val beforeLast = tracker.stationIds.lastIndex - 1

        assertEquals(GetOffAlert(beforeLast + 1, NextStop), alerts.check(tracker.correctTo(beforeLast)))
        alerts.check(tracker.correctTo(beforeLast - 3))
        assertNull(alerts.check(tracker.correctTo(beforeLast)))
    }
}
