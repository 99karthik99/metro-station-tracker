package com.karthikmacherla.metrotracker.plan

import android.content.Context
import androidx.core.content.edit

/** A trip the rider has taken, in one direction. */
data class SavedTrip(val fromStationId: String, val toStationId: String, val count: Int, val lastUsedMillis: Long) {
    val reversed: Pair<String, String> get() = toStationId to fromStationId
}

/**
 * Trips the rider has started, so they can repeat one with a tap (F11).
 * Stored in SharedPreferences as "from>to,count,lastUsed;..." — small, and private to the app.
 */
class RecentTrips(context: Context) {
    private val prefs = context.getSharedPreferences("recent_trips", Context.MODE_PRIVATE)

    fun all(): List<SavedTrip> =
        prefs.getString(KEY, "").orEmpty().split(';').mapNotNull { entry ->
            val parts = entry.split(',')
            val stations = parts.getOrNull(0)?.split('>') ?: return@mapNotNull null
            if (parts.size != 3 || stations.size != 2) return@mapNotNull null
            SavedTrip(stations[0], stations[1], parts[1].toIntOrNull() ?: return@mapNotNull null, parts[2].toLongOrNull() ?: 0)
        }

    fun record(fromStationId: String, toStationId: String, nowMillis: Long) {
        val trips = all().toMutableList()
        val index = trips.indexOfFirst { it.fromStationId == fromStationId && it.toStationId == toStationId }
        val updated = if (index >= 0) {
            trips[index].let { it.copy(count = it.count + 1, lastUsedMillis = nowMillis) }.also { trips.removeAt(index) }
        } else {
            SavedTrip(fromStationId, toStationId, 1, nowMillis)
        }
        trips.add(updated)
        val kept = trips.sortedByDescending { it.lastUsedMillis }.take(MAX_STORED)
        prefs.edit { putString(KEY, kept.joinToString(";") { "${it.fromStationId}>${it.toStationId},${it.count},${it.lastUsedMillis}" }) }
    }

    companion object {
        private const val KEY = "trips"
        private const val MAX_STORED = 20

        /**
         * Trips to offer on the plan screen: the return of the latest trip first (a commute is
         * usually there and back), then the most frequent.
         */
        fun suggestions(trips: List<SavedTrip>, limit: Int = 4): List<Pair<String, String>> {
            val latest = trips.maxByOrNull { it.lastUsedMillis } ?: return emptyList()
            val byFrequency = trips
                .sortedWith(compareByDescending<SavedTrip> { it.count }.thenByDescending { it.lastUsedMillis })
                .map { it.fromStationId to it.toStationId }
            return (listOf(latest.reversed) + byFrequency).distinct().take(limit)
        }
    }
}
