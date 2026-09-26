package com.personal.clock.ui.components

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.personal.clock.R
import com.personal.clock.notification.Notifications

/** Snapshot of the permissions that matter for reliable alarms and timers. */
data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreen: Boolean,
) {
    val allGranted: Boolean get() = notifications && exactAlarms && fullScreen

    companion object {
        fun read(context: Context): PermissionStatus = PermissionStatus(
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            fullScreen = Notifications.canUseFullScreenIntent(context),
        )
    }
}

/** Permission status, refreshed every time the screen resumes (e.g. back from Settings). */
@Composable
fun rememberPermissionStatus(): PermissionStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionStatus.read(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = PermissionStatus.read(context) }
    return status
}

/**
 * Returns an action that asks for POST_NOTIFICATIONS (Android 13+). If the system will
 * not show the dialog any more (denied twice), the app notification settings open instead.
 */
@Composable
fun rememberNotificationPermissionRequest(): () -> Unit {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        return { openNotificationSettings(context) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) openNotificationSettings(context)
    }
    return { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/** Asks once, silently, e.g. when the first alarm is saved. Does nothing if already granted. */
@Composable
fun rememberQuietNotificationRequest(): () -> Unit {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return {}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    context.safeStart(intent)
}

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.safeStart(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
        )
    }
}

fun openFullScreenSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.safeStart(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri()),
        )
    }
}

private fun Context.safeStart(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri()),
        )
    }
}

/** Explains missing permissions with one clear action each. Shown on Alarms and Timers. */
@Composable
fun PermissionBanners(status: PermissionStatus, showExactAlarm: Boolean, modifier: Modifier = Modifier) {
    if (status.allGranted) return
    val context = LocalContext.current
    val requestNotifications = rememberNotificationPermissionRequest()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!status.notifications) {
            WarningCard(
                text = stringResource(R.string.perm_notifications_rationale),
                action = stringResource(R.string.perm_allow),
                onAction = requestNotifications,
            )
        }
        if (showExactAlarm && !status.exactAlarms) {
            WarningCard(
                text = stringResource(R.string.perm_exact_rationale),
                action = stringResource(R.string.perm_open_settings),
                onAction = { openExactAlarmSettings(context) },
            )
        }
        if (showExactAlarm && !status.fullScreen) {
            WarningCard(
                text = stringResource(R.string.perm_fullscreen_rationale),
                action = stringResource(R.string.perm_open_settings),
                onAction = { openFullScreenSettings(context) },
            )
        }
    }
}

@Composable
private fun WarningCard(text: String, action: String, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onAction) { Text(action) }
            }
        }
    }
}
