package com.karthikmacherla.metrotracker.trip

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karthikmacherla.metrotracker.R
import com.karthikmacherla.metrotracker.core.MetroNetwork
import com.karthikmacherla.metrotracker.plan.lineColor
import com.karthikmacherla.metrotracker.tracking.ActiveTrip
import com.karthikmacherla.metrotracker.tracking.minutesLeft
import kotlinx.coroutines.delay
import java.util.Date

/** A fix older than this means the phone has lost GPS (tunnel, deep inside a station). */
private const val STALE_FIX_MS = 45_000L
/** Below this the train is standing still; GPS speed is noisy near zero. */
private const val STOPPED_MPS = 1.5f

/**
 * The trip screen (mockup 2): what a rider glances at between stations.
 * Answers, in order: where do I get off and when, what's next, how far along am I,
 * and is the tracking working. Tapping a station corrects the position (F10).
 */
@Composable
fun TripScreen(
    trip: ActiveTrip,
    network: MetroNetwork,
    notificationsOff: Boolean,
    onCorrectStation: (stationIndex: Int) -> Unit,
    onEndTrip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Minutes left and GPS freshness depend on the clock, not just on new fixes.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }

    val progress = trip.progress
    val destination = network.station(trip.route.toStationId)
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.trip_to, destination.name), style = MaterialTheme.typography.labelLarge, color = secondary)
        Spacer(Modifier.height(4.dp))

        if (progress.arrived) {
            Text(stringResource(R.string.trip_arrived, destination.name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.notif_trip_ends_soon), color = secondary)
        } else {
            val next = network.station(progress.nextStationId)
            val headline = when {
                progress.nextStationIndex == progress.stationIds.lastIndex -> stringResource(R.string.notif_next_get_off, next.name)
                next.id in trip.route.interchangeStationIds -> stringResource(R.string.notif_next_change, next.name)
                else -> stringResource(R.string.notif_next, next.name)
            }
            Text(headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))

            val arrival = DateFormat.getTimeFormat(LocalContext.current).format(Date(trip.estimatedArrivalMillis))
            Row(Modifier.fillMaxWidth()) {
                Stat(stringResource(R.string.notif_minutes, minutesLeft(trip, now)), stringResource(R.string.trip_arrive_at, arrival), Modifier.weight(1f))
                Stat(progress.stopsLeft.toString(), pluralStringResource(R.plurals.trip_stops_left_label, progress.stopsLeft), Modifier.weight(1f))
                Stat("${progress.percent}%", stringResource(R.string.trip_done_label), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(progress = { progress.fraction.toFloat() }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(statusLine(trip, network, now), style = MaterialTheme.typography.bodySmall, color = secondary)

        if (notificationsOff) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.trip_notifications_off), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        StationList(trip, network, onCorrectStation)
        Text(stringResource(R.string.trip_tap_to_correct), style = MaterialTheme.typography.bodySmall, color = secondary)

        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onEndTrip, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.trip_end)) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Is tracking working? Speed is shown here, small: useful as a sign of life, not to act on. */
@Composable
private fun statusLine(trip: ActiveTrip, network: MetroNetwork, now: Long): String {
    val fix = trip.lastFix
    val progress = trip.progress
    return when {
        fix == null -> stringResource(R.string.trip_status_waiting_gps)
        now - fix.timeMillis > STALE_FIX_MS -> stringResource(R.string.trip_status_gps_lost)
        (fix.speedMps ?: 0f) < STOPPED_MPS && progress.atStation ->
            stringResource(R.string.trip_status_stopped_at, network.station(progress.stationIds[progress.lastStationIndex]).name)
        (fix.speedMps ?: 0f) < STOPPED_MPS -> stringResource(R.string.trip_status_stopped)
        else -> stringResource(R.string.trip_status_moving, trip.speedKmh ?: 0)
    }
}

/**
 * The route as a vertical list (mockup 2): passed stations collapsed to a count, the last
 * one reached, then everything still ahead. Each row is tappable to correct the position.
 */
@Composable
private fun StationList(trip: ActiveTrip, network: MetroNetwork, onCorrectStation: (Int) -> Unit) {
    val progress = trip.progress
    val ids = progress.stationIds
    // Line colour of each station's row: the leg it's ridden on (an interchange takes the new line's colour).
    val colors = buildList {
        trip.route.legs.forEachIndexed { index, leg ->
            val count = if (index == 0) leg.stationIds.size else leg.stationIds.size - 1
            repeat(count) { add(lineColor(leg.line)) }
        }
    }
    val firstShown = (progress.lastStationIndex).coerceAtLeast(0)
    if (firstShown > 0) {
        Text(
            pluralStringResource(R.plurals.trip_stations_passed, firstShown, firstShown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 30.dp, bottom = 4.dp),
        )
    }
    for (i in firstShown..ids.lastIndex) {
        val station = network.station(ids[i])
        val tag = when {
            i == progress.lastStationIndex && progress.atStation && !progress.arrived -> stringResource(R.string.trip_tag_here)
            i == progress.nextStationIndex && !progress.arrived -> stringResource(R.string.trip_tag_next)
            station.id in trip.route.interchangeStationIds -> {
                val leg = trip.route.legs.first { it.fromStationId == station.id }
                stringResource(R.string.trip_tag_change, leg.line.name)
            }
            else -> null
        }
        StationRow(
            name = station.name,
            color = colors.getOrElse(i) { Color.Gray },
            highlighted = i == progress.nextStationIndex && !progress.arrived,
            passed = i < progress.lastStationIndex || (i == progress.lastStationIndex && !progress.atStation),
            tag = tag,
            onClick = { onCorrectStation(i) },
        )
    }
}

@Composable
private fun StationRow(name: String, color: Color, highlighted: Boolean, passed: Boolean, tag: String?, onClick: () -> Unit) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            Spacer(Modifier.size(if (highlighted) 16.dp else 10.dp).background(if (passed) color.copy(alpha = 0.35f) else color, CircleShape))
        }
        Text(
            name,
            modifier = Modifier.weight(1f),
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (passed) secondary else Color.Unspecified,
        )
        if (tag != null) Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}
