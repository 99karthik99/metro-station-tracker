package com.karthikmacherla.metrotracker.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.toColorInt
import com.karthikmacherla.metrotracker.MainActivity
import com.karthikmacherla.metrotracker.MetroData
import com.karthikmacherla.metrotracker.R

/**
 * What the trip notification shows. Kept apart from the Notification object so the service
 * can skip re-posting when nothing visible changed: every post makes the system UI redraw.
 */
data class TripNotificationContent(
    val title: String,
    val text: String,
    val percent: Int,
    /** Short text for the status bar chip of a Live Update, e.g. "12 min". */
    val chipText: String,
    /** One progress bar segment per leg: (stops, line colour as ARGB). */
    val segments: List<Pair<Int, Int>>,
)

/** Builds the ongoing trip notification (PRD §4 step 5). */
object TripNotifications {
    private const val UNITS_PER_STOP = 100

    /**
     * Default importance, so the trip sits with the alerting notifications at the top of the
     * shade and shows on the lock screen; low importance put it in the collapsed "Silent"
     * section at the bottom (D31). No sound or vibration: it updates often.
     *
     * A channel's importance can't be raised once created, so this is a new channel id.
     */
    const val CHANNEL_TRIP = "trip_progress"
    private const val OLD_CHANNEL_TRIP = "trip"
    const val NOTIFICATION_ID = 1

    /** Channels are how users control notifications per type in system settings. Safe to call repeatedly. */
    fun ensureChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.deleteNotificationChannel(OLD_CHANNEL_TRIP)
        val channel = NotificationChannelCompat.Builder(CHANNEL_TRIP, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(context.getString(R.string.channel_trip_name))
            .setDescription(context.getString(R.string.channel_trip_description))
            .setSound(null, null)
            .setVibrationEnabled(false)
            .setShowBadge(false)
            .build()
        manager.createNotificationChannel(channel)
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

        val segments = trip.route.legs.map { it.stops to it.line.color.toColorInt() }
        if (progress.arrived) {
            return TripNotificationContent(
                title = context.getString(R.string.notif_arrived, network.station(trip.route.toStationId).name),
                text = context.getString(R.string.notif_trip_ends_soon),
                percent = 100,
                chipText = context.getString(R.string.notif_chip_arrived),
                segments = segments,
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
        return TripNotificationContent(title, "$stops · $time", progress.percent, chipText = time, segments = segments)
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

        // Progress bar in "stop" units, one coloured segment per line ridden. Older Android
        // versions get a plain progress bar from the compat library.
        val totalUnits = content.segments.sumOf { it.first }.coerceAtLeast(1) * UNITS_PER_STOP
        val style = NotificationCompat.ProgressStyle()
            .setProgressSegments(
                content.segments.map { (stops, color) ->
                    NotificationCompat.ProgressStyle.Segment(stops.coerceAtLeast(1) * UNITS_PER_STOP).setColor(color)
                },
            )
            .setProgress(totalUnits * content.percent / 100)
            .setStyledByProgress(true)

        return NotificationCompat.Builder(context, CHANNEL_TRIP)
            .setSmallIcon(R.drawable.ic_stat_train)
            .setSubText(context.getString(R.string.notif_percent, content.percent))
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(style)
            .setShowWhen(false)
            // Android 16+: ask to show as a Live Update. The rider can turn this off per app.
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(content.chipText)
            // Full details on the lock screen, even when other apps' contents are hidden.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
