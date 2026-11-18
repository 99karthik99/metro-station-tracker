package com.karthikmacherla.metrotracker

import com.karthikmacherla.metrotracker.plan.RecentTrips
import com.karthikmacherla.metrotracker.plan.SavedTrip
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentTripsTest {
    @Test
    fun `return of the latest trip comes first`() {
        val trips = listOf(
            SavedTrip("tarnaka", "raidurg", count = 5, lastUsedMillis = 100),
            SavedTrip("raidurg", "tarnaka", count = 4, lastUsedMillis = 50),
            SavedTrip("ameerpet", "miyapur", count = 9, lastUsedMillis = 10),
        )

        assertEquals(
            listOf("raidurg" to "tarnaka", "ameerpet" to "miyapur", "tarnaka" to "raidurg"),
            RecentTrips.suggestions(trips),
        )
    }

    @Test
    fun `return is offered even if never taken`() {
        val trips = listOf(SavedTrip("tarnaka", "raidurg", count = 1, lastUsedMillis = 1))

        assertEquals(listOf("raidurg" to "tarnaka", "tarnaka" to "raidurg"), RecentTrips.suggestions(trips))
    }

    @Test
    fun `no history means no suggestions`() {
        assertEquals(emptyList<Pair<String, String>>(), RecentTrips.suggestions(emptyList()))
    }
}
