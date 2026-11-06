package com.karthikmacherla.metrotracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlannerTest {
    private val network = NetworkLoader.loadHyderabad()
    private val planner = RoutePlanner(network)

    private fun Route.lineIds() = legs.map { it.line.id }

    @Test
    fun `single line trip has no changes`() {
        val route = planner.plan("miyapur", "ameerpet")

        assertEquals(listOf("red"), route.lineIds())
        assertEquals(10, route.stops)
        assertEquals(0, route.changes)
        assertEquals("lb-nagar", route.legs.single().towardsStationId)
    }

    @Test
    fun `direction is the line end in the direction of travel`() {
        val route = planner.plan("ameerpet", "miyapur")

        assertEquals("miyapur", route.legs.single().towardsStationId)
    }

    @Test
    fun `Miyapur to Uppal changes at Ameerpet towards Nagole`() {
        val route = planner.plan("miyapur", "uppal")

        assertEquals(listOf("red", "blue"), route.lineIds())
        assertEquals(listOf("ameerpet"), route.interchangeStationIds)
        assertEquals(10, route.legs[0].stops)
        assertEquals(12, route.legs[1].stops)
        assertEquals("nagole", route.legs[1].towardsStationId)
    }

    @Test
    fun `LB Nagar to JBS Parade Ground changes at MG Bus Station`() {
        val route = planner.plan("lb-nagar", "jbs-parade-ground")

        assertEquals(listOf("red", "green"), route.lineIds())
        assertEquals(listOf("mg-bus-station"), route.interchangeStationIds)
        assertEquals("jbs-parade-ground", route.legs[1].towardsStationId)
    }

    @Test
    fun `two changes win when much faster than one`() {
        // Via Ameerpet and Parade Ground (16 stops) beats staying on Red to MG Bus Station (26 stops).
        val route = planner.plan("miyapur", "secunderabad-west")

        assertEquals(listOf("red", "blue", "green"), route.lineIds())
        assertEquals(listOf("ameerpet", "parade-ground"), route.interchangeStationIds)
        assertEquals(16, route.stops)
    }

    @Test
    fun `legs connect end to end`() {
        val route = planner.plan("raidurg", "lb-nagar")

        route.legs.zipWithNext { a, b -> assertEquals(a.toStationId, b.fromStationId) }
        assertEquals("raidurg", route.fromStationId)
        assertEquals("lb-nagar", route.toStationId)
    }

    @Test
    fun `total time is ride time plus transfer time`() {
        val route = planner.plan("miyapur", "uppal")

        val expected = route.legs.sumOf { it.rideSeconds } + network.transferSecondsAt("ameerpet")
        assertEquals(expected, route.totalSeconds)
    }

    @Test
    fun `end to end Red Line time is plausible`() {
        // Miyapur to LB Nagar is roughly 50 minutes in practice; catch badly wrong constants.
        val minutes = planner.plan("miyapur", "lb-nagar").totalSeconds / 60

        assertTrue("was $minutes min", minutes in 40..65)
    }

    @Test
    fun `every pair of stations has a route`() {
        val ids = network.stations.keys.toList()
        for (from in ids) for (to in ids) if (from != to) planner.plan(from, to)
    }

    @Test
    fun `same start and destination is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { planner.plan("ameerpet", "ameerpet") }
    }

    @Test
    fun `unknown station is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { planner.plan("miyapur", "atlantis") }
    }
}
