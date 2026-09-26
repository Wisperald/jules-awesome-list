package com.personal.clock.ui.alarms

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.clock.R
import com.personal.clock.data.AppSettings
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.AlarmScheduleCalculator
import com.personal.clock.ui.AppViewModels
import com.personal.clock.ui.components.EmptyState
import com.personal.clock.ui.components.PermissionBanners
import com.personal.clock.ui.components.durationText
import com.personal.clock.ui.components.durationUntilText
import com.personal.clock.ui.components.rememberPermissionStatus
import com.personal.clock.ui.components.rememberQuietNotificationRequest
import com.personal.clock.ui.components.rememberWallClock
import com.personal.clock.util.TimeText
import com.personal.clock.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@Composable
fun AlarmsScreen(settings: AppSettings, viewModel: AlarmsViewModel = viewModel(factory = AppViewModels.Factory)) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val now by rememberWallClock()
    val context = LocalContext.current
    val resources = LocalResources.current
    val locale = currentLocale()
    val is24Hour = TimeText.is24Hour(context, settings.timeFormat)
    val permissions = rememberPermissionStatus()
    val askNotifications = rememberQuietNotificationRequest()

    // Editor state: null = closed, id 0 = new alarm.
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing = editingId?.let { id ->
        if (id == 0L) {
            Alarm(id = 0, hour = 7, minute = 0, snoozeMinutes = settings.defaultSnoozeMinutes)
        } else {
            alarms?.firstOrNull { it.id == id }
        }
    }

    val zonedNow = now.atZone(ZoneId.systemDefault())
    val next = alarms?.let { AlarmScheduleCalculator.nextAlarm(it, zonedNow) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "permissions") { PermissionBanners(permissions, showExactAlarm = true) }
            if (next != null) {
                item(key = "next") {
                    val millis = AlarmScheduleCalculator.timeUntil(next.second, zonedNow).toMillis()
                    Text(
                        text = stringResource(R.string.next_alarm_in, durationUntilText(millis)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    )
                }
            }
            if (alarms?.isEmpty() == true) {
                item(key = "empty") { EmptyState(R.drawable.ic_alarm, stringResource(R.string.alarms_empty)) }
            }
            items(alarms.orEmpty(), key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    now = now,
                    locale = locale,
                    is24Hour = is24Hour,
                    onClick = { editingId = alarm.id },
                    onToggle = { enabled -> viewModel.setEnabled(alarm, enabled) },
                    onCancelSnooze = { viewModel.cancelSnooze(alarm) },
                )
            }
        }
        FloatingActionButton(
            onClick = { editingId = 0L },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_alarm))
        }
    }

    if (editing != null) {
        AlarmEditorDialog(
            initial = editing,
            is24Hour = is24Hour,
            onDismiss = { editingId = null },
            onSave = { alarm ->
                editingId = null
                askNotifications()
                viewModel.save(alarm) { saved ->
                    val zonedNow = ZonedDateTime.now()
                    val trigger = AlarmScheduleCalculator.nextTrigger(saved, zonedNow) ?: return@save
                    val left = AlarmScheduleCalculator.timeUntil(trigger, zonedNow).toMillis()
                    val text = resources.getString(R.string.alarm_saved_in, durationText(resources, left))
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = if (editing.id > 0) {
                {
                    editingId = null
                    viewModel.delete(editing)
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun AlarmCard(
    alarm: Alarm,
    now: Instant,
    locale: Locale,
    is24Hour: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onCancelSnooze: () -> Unit,
) {
    val timeText = TimeText.format(alarm.hour, alarm.minute, locale, is24Hour)
    val stateText = stringResource(if (alarm.enabled) R.string.alarm_state_on else R.string.alarm_state_off)
    val title = alarm.label.ifBlank { stringResource(R.string.alarm_default_title) }
    val contentColor = if (alarm.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.enabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$title, $timeText" },
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(timeText, style = MaterialTheme.typography.displaySmall, color = contentColor)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = repeatText(alarm.daysMask, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val snoozedUntil = alarm.snoozedUntil
                if (snoozedUntil != null && alarm.isSnoozedAt(now.toEpochMilli())) {
                    val until = TimeText.format(Instant.ofEpochMilli(snoozedUntil).atZone(ZoneId.systemDefault()), locale, is24Hour)
                    AssistChip(
                        onClick = onCancelSnooze,
                        label = { Text(stringResource(R.string.alarm_snoozed_until_cancel, until)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_snooze), contentDescription = null) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // State is conveyed by text as well as by the switch position and color.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.semantics {
                        contentDescription = "$title $timeText"
                        stateDescription = stateText
                    },
                )
                Text(stateText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
