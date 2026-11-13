package com.karthikmacherla.metrotracker.core

/**
 * Finds stations by name, ignoring case, spaces and punctuation, so "lbnagar" finds "LB Nagar"
 * and "road no 5" finds "Road No. 5 Jubilee Hills".
 *
 * Ranking: names starting with the query, then names with a word starting with it, then any match.
 * An empty query returns every station, sorted by name.
 */
fun searchStations(network: MetroNetwork, query: String): List<Station> {
    val all = network.stations.values.sortedBy { it.name }
    val q = normalize(query)
    if (q.isEmpty()) return all

    return all
        .filter { normalize(it.name).contains(q) }
        .sortedBy { station ->
            when {
                normalize(station.name).startsWith(q) -> 0
                station.name.split(' ', '-').any { normalize(it).startsWith(q) } -> 1
                else -> 2
            }
        }
}

private fun normalize(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }
