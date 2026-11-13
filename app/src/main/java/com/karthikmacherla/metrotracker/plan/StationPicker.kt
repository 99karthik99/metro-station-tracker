package com.karthikmacherla.metrotracker.plan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.karthikmacherla.metrotracker.R
import com.karthikmacherla.metrotracker.core.MetroNetwork
import com.karthikmacherla.metrotracker.core.NetworkLoader
import com.karthikmacherla.metrotracker.core.searchStations
import com.karthikmacherla.metrotracker.ui.theme.MetroTrackerTheme

/** Full-screen searchable list of stations. The system back gesture closes it. */
@Composable
fun StationPicker(
    network: MetroNetwork,
    title: String,
    onPick: (stationId: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    // rememberSaveable keeps the typed query across rotation, unlike plain remember.
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { searchStations(network, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.picker_cancel)) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (results.isEmpty()) {
            Text(
                stringResource(R.string.picker_no_results, query),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { station ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(station.id) }
                        .padding(vertical = 14.dp),
                ) {
                    Text(station.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    LineDots(network, station.id)
                }
                HorizontalDivider()
            }
        }
    }
}

/** One coloured dot per line serving the station; interchanges show two. */
@Composable
fun LineDots(network: MetroNetwork, stationId: String, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        network.linesAt(stationId).forEach { line ->
            Spacer(Modifier.size(10.dp).background(lineColor(line), CircleShape))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StationPickerPreview() {
    MetroTrackerTheme {
        StationPicker(NetworkLoader.loadHyderabad(), title = "To", onPick = {}, onDismiss = {})
    }
}
