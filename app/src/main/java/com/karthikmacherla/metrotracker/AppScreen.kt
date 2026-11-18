package com.karthikmacherla.metrotracker

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karthikmacherla.metrotracker.permissions.LocationAccess
import com.karthikmacherla.metrotracker.permissions.PermissionExplainer
import com.karthikmacherla.metrotracker.permissions.PermissionRecovery
import com.karthikmacherla.metrotracker.permissions.locationAccess
import com.karthikmacherla.metrotracker.permissions.notificationsGranted
import com.karthikmacherla.metrotracker.permissions.openAppSettings
import com.karthikmacherla.metrotracker.permissions.tripPermissions
import com.karthikmacherla.metrotracker.plan.PlanScreen
import com.karthikmacherla.metrotracker.plan.PlanViewModel
import com.karthikmacherla.metrotracker.tracking.TrackingService
import com.karthikmacherla.metrotracker.tracking.TripRepository

/** Which full-screen step of the Start trip flow is showing on top of the plan screen. */
private enum class StartStep { None, Explain, Recovery }

private const val PREFS = "app"
private const val KEY_PERMISSIONS_EXPLAINED = "permissions_explained"

/**
 * Top-level screen: the plan screen plus the Start trip permission flow (PRD §4a).
 * Start trip → explanation (first time, or while location isn't precise) → Android's prompt →
 * trip starts, or a recovery screen if location is approximate or denied.
 */
@Composable
fun AppScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val planViewModel: PlanViewModel = viewModel()
    val state by planViewModel.state.collectAsState()
    val activeTrip by TripRepository.activeTrip.collectAsState()
    var step by rememberSaveable { mutableStateOf(StartStep.None) }

    fun startTrip() {
        val route = state.route ?: return
        TrackingService.start(context, route.fromStationId, route.toStationId)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Read the outcome from the system rather than the callback: it also covers "Only this time".
        step = if (locationAccess(context) == LocationAccess.Precise) {
            startTrip()
            StartStep.None
        } else {
            StartStep.Recovery
        }
    }

    when (step) {
        StartStep.Explain -> PermissionExplainer(
            onContinue = {
                context.prefs().edit { putBoolean(KEY_PERMISSIONS_EXPLAINED, true) }
                permissionLauncher.launch(tripPermissions)
            },
            onNotNow = { step = StartStep.None },
            modifier = modifier,
        )
        StartStep.Recovery -> PermissionRecovery(
            access = locationAccess(context),
            onOpenSettings = {
                step = StartStep.None
                openAppSettings(context)
            },
            onBack = { step = StartStep.None },
            modifier = modifier,
        )
        StartStep.None -> PlanScreen(
            state = state,
            onSelectFrom = planViewModel::selectFrom,
            onSelectTo = planViewModel::selectTo,
            onSwap = planViewModel::swap,
            onStartTrip = {
                val explained = context.prefs().getBoolean(KEY_PERMISSIONS_EXPLAINED, false)
                if (locationAccess(context) == LocationAccess.Precise && explained) startTrip() else step = StartStep.Explain
            },
            activeTrip = activeTrip,
            notificationsOff = activeTrip != null && !notificationsGranted(context),
            onEndTrip = { TrackingService.stop(context) },
            modifier = modifier,
        )
    }
}

private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
