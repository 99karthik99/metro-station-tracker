package com.karthikmacherla.metrotracker.tracking

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karthikmacherla.metrotracker.MainActivity
import com.karthikmacherla.metrotracker.R
import java.util.Date
import kotlin.math.roundToInt

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

    fun build(context: Context, trip: ActiveTrip, nowMillis: Long): Notification {
        val totalMillis = trip.route.totalSeconds * 1000L
        val elapsedMillis = (nowMillis - trip.startedAtMillis).coerceIn(0, totalMillis)
        val remainingMillis = trip.estimatedArrivalMillis - nowMillis
        val arrival = DateFormat.getTimeFormat(context).format(Date(trip.estimatedArrivalMillis))

        val header = when {
            remainingMillis < 0 -> context.getString(R.string.notif_past_estimate, arrival)
            remainingMillis < 60_000 -> context.getString(R.string.notif_arrive_under_minute, arrival)
            else -> context.getString(R.string.notif_arrive_in, (remainingMillis / 60_000.0).roundToInt(), arrival)
        }
        val fix = trip.lastFix
        val text = if (fix == null) {
            context.getString(R.string.notif_waiting_for_location)
        } else {
            context.resources.getQuantityString(R.plurals.notif_location_status, trip.fixCount, fix.accuracyMeters.roundToInt(), trip.fixCount)
        }

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
            .setSubText(header)
            .setContentTitle(context.getString(R.string.notif_title_tracking))
            .setContentText(text)
            .setProgress(1000, (elapsedMillis * 1000 / totalMillis.coerceAtLeast(1)).toInt(), false)
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
