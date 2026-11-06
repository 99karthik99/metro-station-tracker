package com.karthikmacherla.metrotracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLoaderTest {
    private val network = NetworkLoader.loadHyderabad()

    @Test
    fun `bundled network has the expected shape`() {
        assertEquals(57, network.stations.size)
        assertEquals(listOf("red", "blue", "green"), network.lines.map { it.id })
        assertEquals(listOf(27, 23, 10), network.lines.map { it.stationIds.size })
    }

    @Test
    fun `interchanges are derived from line membership`() {
        val interchanges = network.stations.keys.filter { network.isInterchange(it) }.toSet()

        assertEquals(setOf("ameerpet", "mg-bus-station", "parade-ground"), interchanges)
        assertFalse(network.isInterchange("jbs-parade-ground"))
        assertEquals(listOf("red", "blue"), network.linesAt("ameerpet").map { it.id })
    }

    @Test
    fun `adjacent stations are a plausible distance apart`() {
        for (line in network.lines) {
            line.stationIds.zipWithNext { a, b ->
                val meters = distanceMeters(network.station(a), network.station(b))
                assertTrue("$a to $b is $meters m", meters in 300.0..2500.0)
            }
        }
    }

    @Test
    fun `line referencing an unknown station is rejected`() {
        val text = """
            {"network":"Test","schemaVersion":1,
             "lines":[{"id":"x","name":"X","color":"#000","stationIds":["a","b"]}],
             "stations":[{"id":"a","name":"A","lat":0.0,"lng":0.0}]}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) { NetworkLoader.parse(text) }
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val text = """{"network":"Test","schemaVersion":2,"lines":[],"stations":[]}"""

        assertThrows(IllegalArgumentException::class.java) { NetworkLoader.parse(text) }
    }
}
