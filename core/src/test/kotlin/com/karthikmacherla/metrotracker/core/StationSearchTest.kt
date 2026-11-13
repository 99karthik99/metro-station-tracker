package com.karthikmacherla.metrotracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationSearchTest {
    private val network = NetworkLoader.loadHyderabad()

    private fun names(query: String) = searchStations(network, query).map { it.name }

    @Test
    fun `empty query returns all stations sorted by name`() {
        val result = names("  ")

        assertEquals(57, result.size)
        assertEquals(result.sorted(), result)
    }

    @Test
    fun `search ignores case, spaces and punctuation`() {
        assertEquals(listOf("LB Nagar"), names("lbnagar"))
        assertEquals(listOf("Road No. 5 Jubilee Hills"), names("road no 5"))
    }

    @Test
    fun `names starting with the query come first`() {
        val result = names("par")

        assertEquals(listOf("Parade Ground", "Paradise", "JBS Parade Ground"), result)
    }

    @Test
    fun `word starts rank above matches inside a word`() {
        // "nagar" starts a word in "LB Nagar" and "SR Nagar" but is inside "Dilsukhnagar".
        val result = names("nagar")

        assertTrue(result.indexOf("Dilsukhnagar") > result.indexOf("LB Nagar"))
        assertTrue(result.indexOf("Dilsukhnagar") > result.indexOf("SR Nagar"))
    }

    @Test
    fun `no match returns empty list`() {
        assertEquals(emptyList<String>(), names("atlantis"))
    }
}
