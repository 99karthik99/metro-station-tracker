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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
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
import com.karthikmacherla.metrotracker.core.GetOffAlert
import com.karthikmacherla.metrotracker.core.GetOffAlerts
import com.karthikmacherla.metrotracker.core.LocationFix
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
 *
 * Kept light on the phone (D27): location callbacks, trace writes and station detection
 * run on a background thread, never the main one; the notification is re-posted only when
 * what it shows changes (about once a minute), because every post makes the system UI redraw.
 */
class TrackingService : Service() {

    companion object {
        const val ACTION_START = "com.karthikmacherla.metrotracker.START_TRIP"
        const val ACTION_STOP = "com.karthikmacherla.metrotracker.STOP_TRIP"
        private const val EXTRA_FROM = "from"
        private const val EXTRA_TO = "to"
        private const val TAG = "TrackingService"

        /** Replays of real rides show 10 s between fixes is enough to catch every station (D27). */
        private const val LOCATION_INTERVAL_MS = 10_000L
        /** How often to recheck the clock-driven parts: minutes left and auto-end. */
        private const val TICK_MS = 15_000L

        /** The trip ends itself this long after arrival (F15). */
        private const val END_AFTER_ARRIVAL_MS = 2 * 60_000L

        /**
         * Safety net if arrival is never detected (e.g. GPS lost, or a trip started and forgotten):
         * end a trip running this long past the route's ride time. Counted from the start, since
         * the arrival estimate keeps moving later while the rider hasn't boarded.
         */
        private const val OVERRUN_LIMIT_MS = 45 * 60_000L

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Background thread that receives location fixes and does all per-fix work. */
    private val locationThread = HandlerThread("trip-location").apply { start() }
    private val locationHandler = Handler(locationThread.looper)
    private var lastPosted: TripNotificationContent? = null
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    @Volatile private var recorder: TraceRecorder? = null
    private var tracking = false
    /** Only touched by the coroutine that collects trip updates. */
    private var alerts: GetOffAlerts? = null

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
                TripNotifications.build(this, TripNotifications.content(this, trip, now)),
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

        // Opening the file is disk work, so it happens on the location thread too (before any fix arrives there).
        locationHandler.post { recorder = TraceRecorder(this, trip.route, trip.startedAtMillis) }
        requestLocationUpdates()
        // Progress changes (new fix, or the rider correcting their station) update the notification.
        alerts = GetOffAlerts(trip.route)
        scope.launch {
            TripRepository.activeTrip.collect { current ->
                refreshNotification()
                current?.let { alerts?.check(it.progress)?.let { alert -> postGetOffAlert(it, alert) } }
            }
        }
        scope.launch {
            while (isActive) {
                delay(TICK_MS)
                val current = TripRepository.activeTrip.value ?: break
                val nowMillis = System.currentTimeMillis()
                val arrivedAt = current.arrivedAtMillis
                val overran = nowMillis > current.startedAtMillis + current.route.totalSeconds * 1000L + OVERRUN_LIMIT_MS
                if (overran || (arrivedAt != null && nowMillis > arrivedAt + END_AFTER_ARRIVAL_MS)) {
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
        locationClient.requestLocationUpdates(request, locationCallback, locationThread.looper)
    }

    /** Runs on [locationThread]. */
    private fun onLocation(location: Location) {
        val fix = LocationFix(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = location.accuracy,
            timeMillis = location.time,
            speedMps = if (location.hasSpeed()) location.speed else null,
        )
        TripRepository.recordFix(fix, System.currentTimeMillis())
        recorder?.record(fix)
    }

    /** Re-posts the notification only if what it shows has changed. Called from two background threads. */
    @SuppressLint("MissingPermission") // checked by canNotify()
    @Synchronized
    private fun refreshNotification() {
        val trip = TripRepository.activeTrip.value ?: return
        if (!canNotify()) return
        val content = TripNotifications.content(this, trip, System.currentTimeMillis())
        if (content == lastPosted) return
        lastPosted = content
        NotificationManagerCompat.from(this)
            .notify(TripNotifications.NOTIFICATION_ID, TripNotifications.build(this, content))
    }

    @SuppressLint("MissingPermission") // checked by canNotify()
    private fun postGetOffAlert(trip: ActiveTrip, alert: GetOffAlert) {
        if (!canNotify()) return
        NotificationManagerCompat.from(this)
            .notify(TripNotifications.GET_OFF_ALERT_ID, TripNotifications.buildGetOffAlert(this, trip, alert))
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || hasPermission(Manifest.permission.POST_NOTIFICATIONS)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        locationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
        // Close the trace on the thread that writes it, after any fix already queued.
        locationHandler.post {
            recorder?.close()
            recorder = null
        }
        locationThread.quitSafely()
        TripRepository.end()
        super.onDestroy()
    }
}
