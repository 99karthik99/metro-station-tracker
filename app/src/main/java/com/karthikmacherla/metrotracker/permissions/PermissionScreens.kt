package com.karthikmacherla.metrotracker.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karthikmacherla.metrotracker.R
import com.karthikmacherla.metrotracker.ui.theme.MetroTrackerTheme

enum class LocationAccess { Precise, Approximate, None }

fun locationAccess(context: Context): LocationAccess = when {
    granted(context, Manifest.permission.ACCESS_FINE_LOCATION) -> LocationAccess.Precise
    granted(context, Manifest.permission.ACCESS_COARSE_LOCATION) -> LocationAccess.Approximate
    else -> LocationAccess.None
}

fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(context, Manifest.permission.POST_NOTIFICATIONS)

/** Asking for both FINE and COARSE is what makes Android show the Precise / Approximate choice (Android 12+). */
val tripPermissions: Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Mockup "permission flow" screen 1: shown before Android's own prompt. */
@Composable
fun PermissionExplainer(onContinue: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onNotNow)
    Column(modifier.fillMaxSize().padding(24.dp)) {
        Text(stringResource(R.string.perm_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.perm_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        Reason(stringResource(R.string.perm_location_title), stringResource(R.string.perm_location_body))
        Reason(stringResource(R.string.perm_notifications_title), stringResource(R.string.perm_notifications_body))
        Reason(stringResource(R.string.perm_privacy_title), stringResource(R.string.perm_privacy_body))
        Spacer(Modifier.weight(1f))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.perm_continue)) }
        TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.perm_not_now)) }
    }
}

@Composable
private fun Reason(title: String, body: String) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mockup "permission flow" screen 3: location is approximate or denied, so trips can't work. */
@Composable
fun PermissionRecovery(
    access: LocationAccess,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val approximate = access == LocationAccess.Approximate
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(if (approximate) R.string.perm_precise_off_title else R.string.perm_denied_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (approximate) R.string.perm_precise_off_body else R.string.perm_denied_body),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Text(
                stringResource(R.string.perm_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.perm_open_settings)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.perm_back)) }
        }
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun PermissionExplainerPreview() {
    MetroTrackerTheme { PermissionExplainer({}, {}) }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun PermissionRecoveryPreview() {
    MetroTrackerTheme { PermissionRecovery(LocationAccess.Approximate, {}, {}) }
}
