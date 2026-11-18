package com.karthikmacherla.metrotracker.plan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karthikmacherla.metrotracker.MetroData
import com.karthikmacherla.metrotracker.core.MetroNetwork
import com.karthikmacherla.metrotracker.core.Route
import com.karthikmacherla.metrotracker.core.RoutePlanner
import com.karthikmacherla.metrotracker.core.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the plan screen draws. The screen holds no state of its own beyond what's on screen. */
data class PlanUiState(
    /** Null while the network is still loading. */
    val network: MetroNetwork? = null,
    val from: Station? = null,
    val to: Station? = null,
    /** Set when both stations are chosen and different. */
    val route: Route? = null,
    /** One-tap trips (from, to), best guess first. */
    val suggestedTrips: List<Pair<Station, Station>> = emptyList(),
) {
    val isSameStation: Boolean get() = from != null && from.id == to?.id
}

/**
 * Holds the plan screen's state. A ViewModel survives configuration changes such as
 * rotation, so the chosen stations aren't lost when the Activity is recreated.
 */
class PlanViewModel(application: Application) : AndroidViewModel(application) {
    private val recentTrips = RecentTrips(application)

    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    private var planner: RoutePlanner? = null

    init {
        viewModelScope.launch {
            // Parsing JSON is work, so it runs off the main (UI) thread.
            val network = withContext(Dispatchers.Default) { MetroData.network }
            planner = MetroData.planner
            _state.update { it.copy(network = network) }
            loadSuggestions()
        }
    }

    /** Fills both stations from a suggested trip. */
    fun selectTrip(fromStationId: String, toStationId: String) =
        updateStations(from = station(fromStationId), to = station(toStationId))

    /** Remembers a started trip so it's offered next time. */
    fun recordTrip(fromStationId: String, toStationId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { recentTrips.record(fromStationId, toStationId, System.currentTimeMillis()) }
            loadSuggestions()
        }
    }

    private suspend fun loadSuggestions() {
        val trips = withContext(Dispatchers.IO) { recentTrips.all() }
        val network = _state.value.network ?: return
        // Skip trips whose stations are no longer in the data (e.g. after a data update).
        val suggested = RecentTrips.suggestions(trips).mapNotNull { (from, to) ->
            val a = network.stations[from] ?: return@mapNotNull null
            val b = network.stations[to] ?: return@mapNotNull null
            a to b
        }
        _state.update { it.copy(suggestedTrips = suggested) }
    }

    fun selectFrom(stationId: String) = updateStations(from = station(stationId), to = _state.value.to)

    fun selectTo(stationId: String) = updateStations(from = _state.value.from, to = station(stationId))

    fun swap() = updateStations(from = _state.value.to, to = _state.value.from)

    private fun station(id: String): Station? = _state.value.network?.station(id)

    private fun updateStations(from: Station?, to: Station?) {
        val route = if (from != null && to != null && from.id != to.id) planner?.plan(from.id, to.id) else null
        _state.update { it.copy(from = from, to = to, route = route) }
    }
}
