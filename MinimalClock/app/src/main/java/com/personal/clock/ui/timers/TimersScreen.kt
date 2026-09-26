@file:OptIn(ExperimentalLayoutApi::class)

package com.personal.clock.ui.timers

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.clock.R
import com.personal.clock.domain.DurationFormat
import com.personal.clock.domain.TimerItem
import com.personal.clock.domain.TimerStatus
import com.personal.clock.ui.AppViewModels
import com.personal.clock.ui.components.AutoSizeNumber
import com.personal.clock.ui.components.EmptyState
import com.personal.clock.ui.components.PermissionBanners
import com.personal.clock.ui.components.SectionHeader
import com.personal.clock.ui.components.rememberElapsedClock
import com.personal.clock.ui.components.rememberPermissionStatus
import com.personal.clock.ui.components.rememberQuietNotificationRequest

@Composable
fun TimersScreen(viewModel: TimersViewModel = viewModel(factory = AppViewModels.Factory)) {
    val timers by viewModel.timers.collectAsStateWithLifecycle()
    val anyRunning = timers?.any { it.status == TimerStatus.RUNNING } == true
    // Countdown shows whole seconds; 4 refreshes per second keep it smooth without waste.
    val now by rememberElapsedClock(active = anyRunning, periodMillis = 250)
    val permissions = rememberPermissionStatus()
    val askNotifications = rememberQuietNotificationRequest()
    val context = LocalContext.current
    val limitText = stringResource(R.string.timers_limit, TimerItem.MAX_TIMERS)
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf<Long?>(null) }

    val create: (String, Long) -> Unit = { label, duration ->
        askNotifications()
        viewModel.create(label, duration) { Toast.makeText(context, limitText, Toast.LENGTH_SHORT).show() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "permissions") { PermissionBanners(permissions, showExactAlarm = true) }
        item(key = "quick") {
            Column {
                SectionHeader(stringResource(R.string.quick_start), modifier = Modifier.padding(horizontal = 0.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    TimerItem.QUICK_MINUTES.forEach { minutes ->
                        val text = stringResource(R.string.minutes_short, minutes)
                        val description = stringResource(R.string.start_timer_for, text)
                        AssistChip(
                            onClick = { create("", minutes * 60_000L) },
                            label = { Text(text) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_play), contentDescription = null) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .semantics { contentDescription = description },
                        )
                    }
                    AssistChip(
                        onClick = { showCustom = true },
                        label = { Text(stringResource(R.string.custom_timer)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
        if (timers?.isEmpty() == true) {
            item(key = "empty") { EmptyState(R.drawable.ic_timer, stringResource(R.string.timers_empty)) }
        }
        items(timers.orEmpty(), key = { it.id }) { timer ->
            TimerCard(
                timer = timer,
                now = now,
                onPrimary = {
                    when (timer.status) {
                        TimerStatus.FINISHED -> viewModel.restart(timer)
                        else -> viewModel.startOrPause(timer)
                    }
                },
                onReset = { viewModel.reset(timer) },
                onAddMinute = { viewModel.addMinute(timer) },
                onDelete = { viewModel.delete(timer) },
                onRename = { renaming = timer.id },
            )
        }
    }

    if (showCustom) {
        CustomTimerDialog(
            onDismiss = { showCustom = false },
            onStart = { label, duration ->
                showCustom = false
                create(label, duration)
            },
        )
    }

    val renameTarget = renaming?.let { id -> timers?.firstOrNull { it.id == id } }
    if (renameTarget != null) {
        RenameDialog(
            initial = renameTarget.label,
            onDismiss = { renaming = null },
            onConfirm = {
                renaming = null
                viewModel.rename(renameTarget, it)
            },
        )
    }
}

@Composable
private fun TimerCard(
    timer: TimerItem,
    now: Long,
    onPrimary: () -> Unit,
    onReset: () -> Unit,
    onAddMinute: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val name = timer.label.ifBlank { stringResource(R.string.timer_default_name, DurationFormat.countdown(timer.durationMillis)) }
    val statusText = stringResource(
        when (timer.status) {
            TimerStatus.IDLE -> R.string.timer_status_ready
            TimerStatus.RUNNING -> R.string.timer_status_running
            TimerStatus.PAUSED -> R.string.timer_status_paused
            TimerStatus.FINISHED -> R.string.timer_status_finished
        },
    )
    val finished = timer.status == TimerStatus.FINISHED
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (finished) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    TextButton(onClick = onRename, contentPadding = PaddingValues(0.dp)) {
                        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(statusText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.delete_timer, name))
                }
            }
            AutoSizeNumber(
                text = DurationFormat.countdown(timer.remaining(now)),
                style = MaterialTheme.typography.displayMedium,
                maxFontSize = 56.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            LinearProgressIndicator(
                progress = { timer.progress(now) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (timer.status == TimerStatus.RUNNING || timer.status == TimerStatus.PAUSED) {
                    TextButton(onClick = onAddMinute) { Text(stringResource(R.string.action_add_minute)) }
                }
                if (timer.status != TimerStatus.IDLE) {
                    FilledTonalButton(onClick = onReset) {
                        Text(stringResource(if (finished) R.string.action_stop else R.string.action_reset))
                    }
                }
                Button(onClick = onPrimary, modifier = Modifier.heightIn(min = 48.dp)) {
                    val (icon, text) = when (timer.status) {
                        TimerStatus.RUNNING -> R.drawable.ic_pause to R.string.action_pause
                        TimerStatus.PAUSED -> R.drawable.ic_play to R.string.action_resume
                        TimerStatus.IDLE -> R.drawable.ic_play to R.string.action_start
                        TimerStatus.FINISHED -> R.drawable.ic_replay to R.string.action_restart
                    }
                    Icon(painterResource(icon), contentDescription = null)
                    Text(stringResource(text), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun CustomTimerDialog(onDismiss: () -> Unit, onStart: (String, Long) -> Unit) {
    var hours by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableStateOf("") }
    var seconds by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val s = seconds.toIntOrNull() ?: 0
    val total = TimerItem.fromParts(h, m, s)
    val valid = h in 0..99 && m in 0..59 && s in 0..59 && total in TimerItem.MIN_DURATION..TimerItem.MAX_DURATION

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_timer)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(hours, { hours = it }, stringResource(R.string.unit_hours), Modifier.weight(1f))
                    NumberField(minutes, { minutes = it }, stringResource(R.string.unit_minutes), Modifier.weight(1f))
                    NumberField(seconds, { seconds = it }, stringResource(R.string.unit_seconds), Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text(stringResource(R.string.timer_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(label, total) }, enabled = valid) { Text(stringResource(R.string.action_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(2)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier,
    )
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.timer_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
