package com.karthikmacherla.metrotracker.tracking

import android.content.Context
import com.karthikmacherla.metrotracker.core.Route
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves every location fix of a trip to a CSV file in the app's private storage, so real
 * rides can be replayed later to tune station detection without riding again.
 *
 * Files: files/traces/<start time>_<from>_<to>.csv. Copy one off a debug build with:
 *   adb shell run-as com.karthikmacherla.metrotracker cat files/traces/<name>.csv > trace.csv
 */
class TraceRecorder(context: Context, route: Route, startedAtMillis: Long) {
    val file: File
    private val writer: Writer

    init {
        val dir = File(context.filesDir, "traces").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAtMillis))
        file = File(dir, "${stamp}_${route.fromStationId}_${route.toStationId}.csv")
        writer = file.bufferedWriter()
        writer.write("# route: ${route.legs.joinToString(" | ") { "${it.line.id}:${it.stationIds.joinToString(",")}" }}\n")
        writer.write("timeMillis,lat,lng,accuracyM,speedMps\n")
    }

    fun record(fix: Fix) {
        writer.write("${fix.timeMillis},${fix.lat},${fix.lng},${fix.accuracyMeters},${fix.speedMps ?: ""}\n")
        writer.flush() // a trip can end by the process being killed, so don't hold rows in memory
    }

    fun close() = writer.close()
}
