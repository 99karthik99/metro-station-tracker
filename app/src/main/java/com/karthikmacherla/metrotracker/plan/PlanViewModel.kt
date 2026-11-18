package com.karthikmacherla.metrotracker.plan

import androidx.lifecycle.ViewModel
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
) {
    val isSameStation: Boolean get() = from != null && from.id == to?.id
}

/**
 * Holds the plan screen's state. A ViewModel survives configuration changes such as
 * rotation, so the chosen stations aren't lost when the Activity is recreated.
 */
class PlanViewModel : ViewModel() {
    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    private var planner: RoutePlanner? = null

    init {
        viewModelScope.launch {
            // Parsing JSON is work, so it runs off the main (UI) thread.
            val network = withContext(Dispatchers.Default) { MetroData.network }
            planner = MetroData.planner
            _state.update { it.copy(network = network) }
        }
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
