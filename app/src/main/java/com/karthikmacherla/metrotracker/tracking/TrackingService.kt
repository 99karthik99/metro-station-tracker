package com.karthikmacherla.metrotracker.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.karthikmacherla.metrotracker.MetroData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs a trip: a foreground service, so Android keeps it alive while the screen is off
 * and the app is in the background. The price is a notification that stays visible,
 * which is also our main UI.
 *
 * Started from the app while it's on screen, so "while using the app" location
 * permission covers the whole trip (no background location permission needed).
 */
class TrackingService : Service() {

    companion object {
        const val ACTION_START = "com.karthikmacherla.metrotracker.START_TRIP"
        const val ACTION_STOP = "com.karthikmacherla.metrotracker.STOP_TRIP"
        private const val EXTRA_FROM = "from"
        private const val EXTRA_TO = "to"
        private const val TAG = "TrackingService"

        /** Frequent updates while we collect real ride data; to be relaxed for battery once detection is tuned. */
        private const val LOCATION_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_REFRESH_MS = 30_000L

        /** Safety net until arrival detection exists: end a trip running this long past its estimate. */
        private const val OVERRUN_LIMIT_MS = 30 * 60_000L

        fun start(context: Context, fromStationId: String, toStationId: String) {
            val intent = Intent(context, TrackingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_FROM, fromStationId)
                .putExtra(EXTRA_TO, toStationId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TrackingService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var recorder: TraceRecorder? = null
    private var tracking = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach(::onLocation)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTrip(
                fromId = intent.getStringExtra(EXTRA_FROM).orEmpty(),
                toId = intent.getStringExtra(EXTRA_TO).orEmpty(),
            )
            else -> stopSelf() // ACTION_STOP from the notification, or an unexpected restart
        }
        // If Android kills the process, don't restart: the trip's state would be gone anyway.
        return START_NOT_STICKY
    }

    private fun startTrip(fromId: String, toId: String) {
        val now = System.currentTimeMillis()
        if (!tracking) {
            TripRepository.start(MetroData.planner.plan(fromId, toId), now)
        }
        val trip = TripRepository.activeTrip.value ?: return stopSelf()

        // startForegroundService() requires startForeground() within a few seconds, on every start.
        TripNotifications.ensureChannels(this)
        try {
            ServiceCompat.startForeground(
                this,
                TripNotifications.NOTIFICATION_ID,
                TripNotifications.build(this, trip, now),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        } catch (e: Exception) {
            // Android refuses a location service without location permission, or from the background.
            Log.w(TAG, "Could not start foreground tracking", e)
            stopSelf()
            return
        }
        if (tracking) return
        tracking = true

        recorder = TraceRecorder(this, trip.route, trip.startedAtMillis)
        requestLocationUpdates()
        scope.launch {
            while (isActive) {
                delay(NOTIFICATION_REFRESH_MS)
                val current = TripRepository.activeTrip.value ?: break
                if (System.currentTimeMillis() > current.estimatedArrivalMillis + OVERRUN_LIMIT_MS) {
                    stopSelf()
                    break
                }
                refreshNotification()
            }
        }
    }

    @SuppressLint("MissingPermission") // checked just above the call
    private fun requestLocationUpdates() {
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
            .setMinUpdateIntervalMillis(LOCATION_INTERVAL_MS / 2)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun onLocation(location: Location) {
        val fix = Fix(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = location.accuracy,
            speedMps = if (location.hasSpeed()) location.speed else null,
            timeMillis = location.time,
        )
        TripRepository.recordFix(fix)
        recorder?.record(fix)
        refreshNotification()
    }

    @SuppressLint("MissingPermission") // checked by canNotify()
    private fun refreshNotification() {
        val trip = TripRepository.activeTrip.value ?: return
        if (!canNotify()) return
        NotificationManagerCompat.from(this)
            .notify(TripNotifications.NOTIFICATION_ID, TripNotifications.build(this, trip, System.currentTimeMillis()))
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        recorder?.close()
        TripRepository.end()
        super.onDestroy()
    }
}
