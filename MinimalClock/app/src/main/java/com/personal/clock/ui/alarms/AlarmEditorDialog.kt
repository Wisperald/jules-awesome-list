@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.personal.clock.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.personal.clock.R
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.DaysOfWeek
import com.personal.clock.ui.components.MinTouchTarget
import com.personal.clock.ui.components.Stepper
import com.personal.clock.ui.components.rememberRingtonePicker
import com.personal.clock.ui.components.ringtoneTitle
import com.personal.clock.ui.components.currentLocale
import com.personal.clock.ui.components.windowSizeDp
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
fun AlarmEditorDialog(
    initial: Alarm,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val locale = currentLocale()
    val timeState = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    var label by rememberSaveable { mutableStateOf(initial.label) }
    var days by rememberSaveable { mutableIntStateOf(initial.daysMask) }
    var ringtone by rememberSaveable { mutableStateOf(initial.ringtoneUri) }
    var vibrate by rememberSaveable { mutableStateOf(initial.vibrate) }
    var gradual by rememberSaveable { mutableStateOf(initial.gradualVolume) }
    var snoozeMinutes by rememberSaveable { mutableIntStateOf(initial.snoozeMinutes) }
    var maxSnoozes by rememberSaveable { mutableIntStateOf(initial.maxSnoozes) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val pickRingtone = rememberRingtonePicker(ringtone) { ringtone = it }
    // The dial needs ~420 dp of height; on short screens (landscape phones) use keyboard input.
    val useDial = windowSizeDp().height >= 640.dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.imePadding()) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
                    }
                    Text(
                        text = stringResource(if (initial.id > 0) R.string.edit_alarm else R.string.add_alarm),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onSave(
                                initial.copy(
                                    hour = timeState.hour,
                                    minute = timeState.minute,
                                    label = label.trim().take(Alarm.MAX_LABEL_LENGTH),
                                    daysMask = days,
                                    ringtoneUri = ringtone,
                                    vibrate = vibrate,
                                    gradualVolume = gradual,
                                    snoozeMinutes = snoozeMinutes,
                                    maxSnoozes = maxSnoozes,
                                ),
                            )
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text(stringResource(R.string.save)) }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (useDial) TimePicker(state = timeState) else TimeInput(state = timeState)
                    }

                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it.take(Alarm.MAX_LABEL_LENGTH) },
                        label = { Text(stringResource(R.string.alarm_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.label_work, R.string.label_meeting, R.string.label_medicine).forEach { res ->
                            val suggestion = stringResource(res)
                            AssistChip(onClick = { label = suggestion }, label = { Text(suggestion) })
                        }
                    }

                    HorizontalDivider()
                    Text(stringResource(R.string.repeat), style = MaterialTheme.typography.titleSmall)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        DaysOfWeek.week(WeekFields.of(locale).firstDayOfWeek).forEach { day ->
                            val selected = days and DaysOfWeek.bit(day) != 0
                            val fullName = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                            FilterChip(
                                selected = selected,
                                onClick = { days = DaysOfWeek.toggle(days, day) },
                                label = { Text(day.getDisplayName(TextStyle.SHORT_STANDALONE, locale)) },
                                modifier = Modifier.semantics { contentDescription = fullName },
                            )
                        }
                    }
                    Text(
                        text = repeatText(days, locale),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()
                    SettingRow(
                        title = stringResource(R.string.alarm_sound),
                        subtitle = ringtoneTitle(ringtone),
                        onClick = pickRingtone,
                    )
                    SwitchRow(stringResource(R.string.vibration), vibrate) { vibrate = it }
                    SwitchRow(stringResource(R.string.gradual_volume), gradual) { gradual = it }

                    HorizontalDivider()
                    Stepper(
                        label = stringResource(R.string.snooze_length),
                        valueText = stringResource(R.string.minutes_short, snoozeMinutes),
                        onDecrement = { snoozeMinutes -= 1 },
                        onIncrement = { snoozeMinutes += 1 },
                        canDecrement = snoozeMinutes > Alarm.SNOOZE_MINUTES_RANGE.first,
                        canIncrement = snoozeMinutes < Alarm.SNOOZE_MINUTES_RANGE.last,
                    )
                    Stepper(
                        label = stringResource(R.string.snooze_count),
                        valueText = if (maxSnoozes == 0) stringResource(R.string.snooze_off) else maxSnoozes.toString(),
                        onDecrement = { maxSnoozes -= 1 },
                        onIncrement = { maxSnoozes += 1 },
                        canDecrement = maxSnoozes > Alarm.MAX_SNOOZES_RANGE.first,
                        canIncrement = maxSnoozes < Alarm.MAX_SNOOZES_RANGE.last,
                    )

                    if (onDelete != null) {
                        HorizontalDivider()
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouchTarget),
                        ) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                            Text(stringResource(R.string.delete_alarm), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_alarm_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // The row handles clicks; the switch is visual + announced as part of the row.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
