package com.karthikmacherla.metrotracker.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Reads the network JSON produced by tools/build_station_data.py. */
object NetworkLoader {
    private const val HYDERABAD_RESOURCE = "/hyderabad_metro.json"
    private const val SUPPORTED_SCHEMA = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** Loads the bundled Hyderabad Metro network (a Java resource, so it works on the JVM and on Android). */
    fun loadHyderabad(): MetroNetwork {
        val stream = NetworkLoader::class.java.getResourceAsStream(HYDERABAD_RESOURCE)
            ?: error("Missing resource $HYDERABAD_RESOURCE")
        return parse(stream.bufferedReader().use { it.readText() })
    }

    fun parse(text: String): MetroNetwork {
        val file = json.decodeFromString<NetworkFile>(text)
        require(file.schemaVersion == SUPPORTED_SCHEMA) { "Unsupported schemaVersion ${file.schemaVersion}" }
        return MetroNetwork(
            name = file.network,
            lines = file.lines.map { Line(it.id, it.name, it.color, it.stationIds) },
            stations = file.stations.map { Station(it.id, it.name, it.lat, it.lng) },
            transfers = file.transfers.map { Transfer(it.stationId, it.seconds) },
        )
    }

    @Serializable
    private class NetworkFile(
        val network: String,
        val schemaVersion: Int,
        val lines: List<LineJson>,
        val stations: List<StationJson>,
        val transfers: List<TransferJson> = emptyList(),
    )

    @Serializable
    private class LineJson(val id: String, val name: String, val color: String, val stationIds: List<String>)

    @Serializable
    private class StationJson(val id: String, val name: String, val lat: Double, val lng: Double)

    @Serializable
    private class TransferJson(val stationId: String, val seconds: Int)
}
