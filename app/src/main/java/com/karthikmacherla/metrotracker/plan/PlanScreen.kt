package com.karthikmacherla.metrotracker.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.karthikmacherla.metrotracker.R
import com.karthikmacherla.metrotracker.core.Line
import com.karthikmacherla.metrotracker.core.MetroNetwork
import com.karthikmacherla.metrotracker.core.NetworkLoader
import com.karthikmacherla.metrotracker.core.Route
import com.karthikmacherla.metrotracker.core.RoutePlanner
import com.karthikmacherla.metrotracker.core.Station
import com.karthikmacherla.metrotracker.ui.theme.MetroTrackerTheme

private enum class PickTarget { From, To }

/**
 * The plan screen (mockup 1). Stateless: it draws [state] and reports taps through callbacks;
 * [PlanViewModel] decides what the state becomes ("state hoisting").
 */
@Composable
fun PlanScreen(
    state: PlanUiState,
    onSelectFrom: (String) -> Unit,
    onSelectTo: (String) -> Unit,
    onSwap: () -> Unit,
    onStartTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val network = state.network
    if (network == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // Which picker is open is pure UI state, so it lives here rather than in the ViewModel.
    var picking by rememberSaveable { mutableStateOf<PickTarget?>(null) }
    picking?.let { target ->
        StationPicker(
            network = network,
            title = stringResource(if (target == PickTarget.From) R.string.plan_from else R.string.plan_to),
            onPick = { id ->
                if (target == PickTarget.From) onSelectFrom(id) else onSelectTo(id)
                picking = null
            },
            onDismiss = { picking = null },
            modifier = modifier,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.plan_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        StationField(stringResource(R.string.plan_from), state.from, network) { picking = PickTarget.From }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onSwap, enabled = state.from != null || state.to != null) {
                Icon(painterResource(R.drawable.ic_swap_vert), contentDescription = stringResource(R.string.plan_swap))
            }
        }
        StationField(stringResource(R.string.plan_to), state.to, network) { picking = PickTarget.To }

        if (state.isSameStation) {
            Text(
                stringResource(R.string.plan_same_station),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        state.route?.let { route ->
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            RoutePreview(route, network)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onStartTrip, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.plan_start_trip))
            }
        }
    }
}

@Composable
private fun StationField(label: String, station: Station?, network: MetroNetwork, onClick: () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    OutlinedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (station == null) {
                Text(
                    stringResource(R.string.plan_choose_station),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(station.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                LineDots(network, station.id)
            }
        }
    }
}

@Composable
private fun RoutePreview(route: Route, network: MetroNetwork) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            stringResource(R.string.plan_minutes, minutesCeil(route.totalSeconds)),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.weight(1f))
        val changes = if (route.changes == 0) {
            stringResource(R.string.plan_no_changes)
        } else {
            pluralStringResource(R.plurals.plan_changes, route.changes, route.changes)
        }
        Text(
            pluralStringResource(R.plurals.plan_stops, route.stops, route.stops) + " · " + changes,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(10.dp))

    // One coloured segment per leg, sized by its number of stops.
    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
        route.legs.forEachIndexed { index, leg ->
            if (index > 0) Spacer(Modifier.width(3.dp))
            Box(Modifier.weight(leg.stops.toFloat()).fillMaxHeight().background(lineColor(leg.line)))
        }
    }
    Spacer(Modifier.height(12.dp))

    route.legs.forEachIndexed { index, leg ->
        if (index > 0) {
            val station = network.station(leg.fromStationId)
            LegRow(
                dotColor = null,
                title = stringResource(R.string.plan_change_at, station.name),
                subtitle = null,
                trailing = stringResource(R.string.plan_change_minutes, minutesCeil(network.transferSecondsAt(station.id))),
            )
        }
        LegRow(
            dotColor = lineColor(leg.line),
            title = stringResource(R.string.plan_leg_title, leg.line.name, network.station(leg.toStationId).name),
            subtitle = stringResource(R.string.plan_towards, network.station(leg.towardsStationId).name),
            trailing = pluralStringResource(R.plurals.plan_stops, leg.stops, leg.stops),
        )
    }
}

@Composable
private fun LegRow(dotColor: Color?, title: String, subtitle: String?, trailing: String) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(22.dp)) {
            if (dotColor != null) Spacer(Modifier.size(12.dp).background(dotColor, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = if (dotColor == null) secondary else Color.Unspecified)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = secondary)
        }
        Text(trailing, color = secondary)
    }
}

private fun minutesCeil(seconds: Int): Int = (seconds + 59) / 60

/** Line colours come from the station data as "#RRGGBB". */
fun lineColor(line: Line): Color = Color(android.graphics.Color.parseColor(line.color))

@Preview(showBackground = true, heightDp = 720)
@Composable
private fun PlanScreenWithRoutePreview() {
    val network = NetworkLoader.loadHyderabad()
    val route = RoutePlanner(network).plan("miyapur", "uppal")
    MetroTrackerTheme {
        PlanScreen(
            state = PlanUiState(network, network.station("miyapur"), network.station("uppal"), route),
            onSelectFrom = {}, onSelectTo = {}, onSwap = {}, onStartTrip = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlanScreenEmptyPreview() {
    MetroTrackerTheme {
        PlanScreen(PlanUiState(NetworkLoader.loadHyderabad()), {}, {}, {}, {})
    }
}
