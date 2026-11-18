package com.karthikmacherla.metrotracker.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karthikmacherla.metrotracker.MainActivity
import com.karthikmacherla.metrotracker.MetroData
import com.karthikmacherla.metrotracker.R

/**
 * What the trip notification shows. Kept apart from the Notification object so the service
 * can skip re-posting when nothing visible changed: every post makes the system UI redraw.
 */
data class TripNotificationContent(val title: String, val text: String, val percent: Int)

/** Builds the ongoing trip notification (PRD §4 step 5). */
object TripNotifications {
    const val CHANNEL_TRIP = "trip"
    const val NOTIFICATION_ID = 1

    /** Channels are how users control notifications per type in system settings. Safe to call repeatedly. */
    fun ensureChannels(context: Context) {
        val channel = NotificationChannelCompat.Builder(CHANNEL_TRIP, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName(context.getString(R.string.channel_trip_name))
            .setDescription(context.getString(R.string.channel_trip_description))
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /**
     * Short lines, key words first, so nothing is cut off with "…" in the collapsed view:
     *   title  "Next: Begumpet"
     *   text   "6 stops left · 14 min"
     *   header "Metro Tracker · 62%", with a progress bar below the text.
     */
    fun content(context: Context, trip: ActiveTrip, nowMillis: Long): TripNotificationContent {
        val network = MetroData.network
        val progress = trip.progress
        val res = context.resources

        if (progress.arrived) {
            return TripNotificationContent(
                title = context.getString(R.string.notif_arrived, network.station(trip.route.toStationId).name),
                text = context.getString(R.string.notif_trip_ends_soon),
                percent = 100,
            )
        }

        val next = network.station(progress.nextStationId)
        val title = when {
            progress.nextStationIndex == progress.stationIds.lastIndex -> context.getString(R.string.notif_next_get_off, next.name)
            next.id in trip.route.interchangeStationIds -> context.getString(R.string.notif_next_change, next.name)
            else -> context.getString(R.string.notif_next, next.name)
        }
        val minutes = minutesLeft(trip, nowMillis)
        val stops = res.getQuantityString(R.plurals.notif_stops_left, progress.stopsLeft, progress.stopsLeft)
        val time = if (minutes < 1) context.getString(R.string.notif_under_minute) else context.getString(R.string.notif_minutes, minutes)
        return TripNotificationContent(title, "$stops · $time", progress.percent)
    }

    fun build(context: Context, content: TripNotificationContent): Notification {
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val endTrip = PendingIntent.getService(
            context, 1,
            Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(context, CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setSubText(context.getString(R.string.notif_percent, content.percent))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setProgress(100, content.percent, false)
            .setShowWhen(false)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.notif_end_trip), endTrip)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/** Whole minutes to the destination, rounded up so "0 min" never shows before arrival. */
fun minutesLeft(trip: ActiveTrip, nowMillis: Long): Int =
    ((trip.estimatedArrivalMillis - nowMillis).coerceAtLeast(0) + 59_999).div(60_000).toInt()
