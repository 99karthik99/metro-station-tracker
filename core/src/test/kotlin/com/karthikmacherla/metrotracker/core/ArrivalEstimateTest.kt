package com.karthikmacherla.metrotracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArrivalEstimateTest {
    private val network = NetworkLoader.loadHyderabad()
    private val planner = RoutePlanner(network)

    private fun loadTrace(name: String): List<LocationFix> =
        javaClass.classLoader.getResourceAsStream(name)!!.bufferedReader().readLines()
            .filter { it.isNotBlank() && it[0].isDigit() }
            .map { line ->
                val p = line.split(',')
                LocationFix(p[1].toDouble(), p[2].toDouble(), p[3].toFloat(), p[0].toLong(), p[4].toFloatOrNull())
            }

    /** One step of a replay: the estimate as the app would show it after this fix. */
    private class Step(val timeMillis: Long, val progress: TripProgress, val etaMillis: Long)

    /** Replays a ride the way the app does: the estimate's clock restarts only when progress changes. */
    private fun replay(from: String, to: String, trace: String): List<Step> {
        val tracker = TripTracker(network, planner.plan(from, to))
        val fixes = loadTrace(trace)
        var progressAt = fixes.first().timeMillis
        var previous = tracker.progress
        return fixes.map { fix ->
            val progress = tracker.update(fix)
            if (progress != previous) progressAt = fix.timeMillis
            previous = progress
            Step(fix.timeMillis, progress, estimateArrivalMillis(progress, progressAt, fix.timeMillis))
        }
    }

    private fun arrivalMillis(steps: List<Step>) = steps.first { it.progress.arrived }.timeMillis

    private fun minutes(millis: Long) = millis / 60_000.0

    @Test
    fun `waiting on the platform pushes the estimate later`() {
        val steps = replay("tarnaka", "raidurg", "traces/20261118-091129_tarnaka_raidurg.csv")
        val waiting = steps.takeWhile { it.progress.waitingToDepart }

        assertTrue("rider waited ~2 min at Tarnaka", waiting.last().timeMillis - waiting.first().timeMillis > 90_000)
        // Each estimate is the full ride from that moment, so it moves with the clock.
        val slip = waiting.last().etaMillis - waiting.first().etaMillis
        val waited = waiting.last().timeMillis - waiting.first().timeMillis
        // Small position changes on the platform shift the ride time by a few seconds.
        assertEquals(waited.toDouble(), slip.toDouble(), 15_000.0)
    }

    @Test
    fun `estimate at departure is within 3 minutes of the real arrival`() {
        val steps = replay("tarnaka", "raidurg", "traces/20261118-091129_tarnaka_raidurg.csv")
        val departure = steps.first { !it.progress.waitingToDepart }

        val error = minutes(departure.etaMillis - arrivalMillis(steps))
        assertTrue("off by $error min", abs(error) <= 3)
    }

    @Test
    fun `starting on board estimates from where the train is`() {
        val steps = replay("raidurg", "tarnaka", "traces/20261118-175244_raidurg_tarnaka.csv")
        // The first fix is already near Madhapur, three stops in.
        val first = steps.first()
        assertTrue(first.progress.lastStationIndex >= 2)

        val error = minutes(first.etaMillis - arrivalMillis(steps))
        assertTrue("off by $error min", abs(error) <= 3)
    }

    @Test
    fun `estimate stays close for the whole ride`() {
        for ((route, trace) in listOf(
            ("tarnaka" to "raidurg") to "traces/20261118-091129_tarnaka_raidurg.csv",
            ("raidurg" to "tarnaka") to "traces/20261118-175244_raidurg_tarnaka.csv",
        )) {
            val steps = replay(route.first, route.second, trace)
            val arrival = arrivalMillis(steps)
            val riding = steps.filter { !it.progress.waitingToDepart && !it.progress.arrived }
            val worst = riding.maxOf { abs(minutes(it.etaMillis - arrival)) }
            assertTrue("$route: worst error $worst min", worst <= 4)
        }
    }

    @Test
    fun `a held train slips the estimate`() {
        val tracker = TripTracker(network, planner.plan("tarnaka", "raidurg"))
        val progress = tracker.correctTo(5)
        val start = 0L
        val tenMinutesLater = 10 * 60_000L

        val eta = estimateArrivalMillis(progress, start, tenMinutesLater)

        assertEquals(tenMinutesLater + progress.remainingAfterNextStationSeconds * 1000L, eta)
        assertTrue(eta > start + progress.remainingSeconds * 1000L)
    }
}
