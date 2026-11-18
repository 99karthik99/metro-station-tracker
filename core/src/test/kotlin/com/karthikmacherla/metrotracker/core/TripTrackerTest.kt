package com.karthikmacherla.metrotracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripTrackerTest {
    private val network = NetworkLoader.loadHyderabad()
    private val planner = RoutePlanner(network)

    private val morning = "traces/20261118-091129_tarnaka_raidurg.csv"
    private val evening = "traces/20261118-175244_raidurg_tarnaka.csv"

    /** Reads a TraceRecorder CSV from test resources. */
    private fun loadTrace(name: String): List<LocationFix> =
        javaClass.classLoader.getResourceAsStream(name)!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("timeMillis") }
            .map { line ->
                val p = line.split(',')
                LocationFix(p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[0].toLong(), p[4].toFloatOrNull())
            }

    private fun tracker(from: String, to: String) = TripTracker(network, planner.plan(from, to))

    /**
     * Replays [fixes]; whenever a precise fix sits right on a station, the tracker must say
     * the rider is at that station. Returns the final progress.
     */
    private fun replayAndCheck(tracker: TripTracker, fixes: List<LocationFix>, minChecks: Int = 5): TripProgress {
        var checked = 0
        var previous = tracker.progress
        for (fix in fixes) {
            val progress = tracker.update(fix)
            assertTrue("progress went backwards", progress.fraction >= previous.fraction)
            previous = progress
            if (fix.accuracyMeters > 30f) continue
            val onStation = progress.stationIds.indexOfFirst { id ->
                val s = network.station(id)
                distanceMeters(s, Station("fix", "", fix.lat, fix.lng)) < 80
            }
            if (onStation >= 0) {
                assertEquals("at ${progress.stationIds[onStation]} (t=${fix.timeMillis})", onStation, progress.lastStationIndex)
                assertTrue(progress.atStation)
                checked++
            }
        }
        assertTrue("only $checked fixes were on a station", checked >= minChecks)
        return previous
    }

    @Test
    fun `morning ride is tracked to arrival`() {
        val end = replayAndCheck(tracker("tarnaka", "raidurg"), loadTrace(morning))

        assertTrue(end.arrived)
        assertEquals(100, end.percent)
        assertEquals(0, end.remainingSeconds)
    }

    @Test
    fun `evening ride started after boarding is tracked to arrival`() {
        val end = replayAndCheck(tracker("raidurg", "tarnaka"), loadTrace(evening))

        assertTrue(end.arrived)
    }

    @Test
    fun `fixes every 10 seconds are enough`() {
        for ((trace, from, to) in listOf(Triple(morning, "tarnaka", "raidurg"), Triple(evening, "raidurg", "tarnaka"))) {
            var lastKept = 0L
            val sparse = loadTrace(trace).filter { fix ->
                (fix.timeMillis - lastKept >= 10_000).also { if (it) lastKept = fix.timeMillis }
            }
            assertTrue(trace, replayAndCheck(tracker(from, to), sparse, minChecks = 0).arrived)
        }
    }

    @Test
    fun `progress at the start matches the route estimate`() {
        val route = planner.plan("miyapur", "uppal")
        val progress = TripTracker(network, route).progress

        assertEquals(route.totalSeconds, progress.remainingSeconds)
        assertEquals(route.stops, progress.stopsLeft)
        assertEquals(1, route.changes)
        assertEquals("jntu-college", progress.nextStationId)
        assertFalse(progress.arrived)
    }

    @Test
    fun `inaccurate and far away fixes are ignored`() {
        val tracker = tracker("tarnaka", "raidurg")
        val ameerpet = network.station("ameerpet")

        tracker.update(LocationFix(ameerpet.lat, ameerpet.lng, accuracyMeters = 500f, timeMillis = 1_000))
        assertEquals(0, tracker.progress.lastStationIndex)

        // Charminar, nowhere near the Blue Line.
        tracker.update(LocationFix(17.3616, 78.4747, accuracyMeters = 10f, timeMillis = 2_000))
        assertEquals(0, tracker.progress.lastStationIndex)
    }

    @Test
    fun `a jump faster than a train is ignored`() {
        val tracker = tracker("tarnaka", "raidurg")
        val tarnaka = network.station("tarnaka")
        val ameerpet = network.station("ameerpet")

        tracker.update(LocationFix(tarnaka.lat, tarnaka.lng, 10f, timeMillis = 0))
        tracker.update(LocationFix(ameerpet.lat, ameerpet.lng, 10f, timeMillis = 10_000))

        assertEquals(0, tracker.progress.lastStationIndex)
    }

    @Test
    fun `rider correction moves the position`() {
        val tracker = tracker("tarnaka", "raidurg")
        val index = tracker.stationIds.indexOf("begumpet")

        val progress = tracker.correctTo(index)

        assertEquals(index, progress.lastStationIndex)
        assertTrue(progress.atStation)
        assertEquals("ameerpet", progress.nextStationId)
    }
}
