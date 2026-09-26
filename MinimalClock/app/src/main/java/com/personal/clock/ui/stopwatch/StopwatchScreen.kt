package com.personal.clock.ui.stopwatch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.clock.R
import com.personal.clock.data.AppSettings
import com.personal.clock.domain.DurationFormat
import com.personal.clock.domain.StopwatchResult
import com.personal.clock.domain.StopwatchState
import com.personal.clock.ui.AppViewModels
import com.personal.clock.ui.components.AutoSizeNumber
import com.personal.clock.ui.components.MinTouchTarget
import com.personal.clock.ui.components.SectionHeader
import com.personal.clock.ui.components.rememberElapsedClock
import com.personal.clock.util.TimeText
import com.personal.clock.ui.components.currentLocale
import java.time.Instant
import java.time.ZoneId

/** ~25 fps while running and visible; nothing at all when paused or in the background. */
private const val FRAME_MILLIS = 40L

@Composable
fun StopwatchScreen(settings: AppSettings, viewModel: StopwatchViewModel = viewModel(factory = AppViewModels.Factory)) {
    val is24Hour = TimeText.is24Hour(LocalContext.current, settings.timeFormat)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val now by rememberElapsedClock(active = state.running, periodMillis = FRAME_MILLIS)
    val durations = state.lapDurations()
    val fastest = state.fastestLapIndex()
    val slowest = state.slowestLapIndex()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "display") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AutoSizeNumber(
                    text = DurationFormat.stopwatch(state.elapsed(now)),
                    maxFontSize = 80.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.laps.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.current_lap, DurationFormat.stopwatch(state.currentLap(now))),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Controls(
                    state = state,
                    onPrimary = viewModel::startOrPause,
                    onLap = viewModel::lap,
                    onReset = viewModel::reset,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }

        if (state.laps.isNotEmpty()) {
            item(key = "laps_header") { SectionHeader(stringResource(R.string.laps)) }
            items(count = durations.size, key = { "lap${durations.lastIndex - it}" }) { reversed ->
                val index = durations.lastIndex - reversed // newest first
                LapRow(
                    number = index + 1,
                    lap = durations[index],
                    split = state.laps[index],
                    tag = when (index) {
                        fastest -> stringResource(R.string.lap_fastest)
                        slowest -> stringResource(R.string.lap_slowest)
                        else -> null
                    },
                )
            }
        }

        if (history.isNotEmpty()) {
            item(key = "history_header") {
                SectionHeader(
                    text = stringResource(R.string.recent_results),
                    trailing = {
                        TextButton(onClick = viewModel::clearHistory) { Text(stringResource(R.string.clear)) }
                    },
                )
            }
            items(history, key = { "h${it.finishedAt}" }) { HistoryRow(it, is24Hour) }
        }
    }
}

@Composable
private fun Controls(
    state: StopwatchState,
    onPrimary: () -> Unit,
    onLap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        val secondaryEnabled = state.running || !state.isReset
        FilledTonalButton(
            onClick = if (state.running) onLap else onReset,
            enabled = secondaryEnabled,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .widthIn(min = 140.dp),
        ) {
            Icon(painterResource(if (state.running) R.drawable.ic_flag else R.drawable.ic_replay), contentDescription = null)
            Text(
                text = stringResource(if (state.running) R.string.action_lap else R.string.action_reset),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .heightIn(min = 56.dp)
                .widthIn(min = 140.dp),
        ) {
            Icon(painterResource(if (state.running) R.drawable.ic_pause else R.drawable.ic_play), contentDescription = null)
            Text(
                text = stringResource(
                    when {
                        state.running -> R.string.action_pause
                        state.isReset -> R.string.action_start
                        else -> R.string.action_resume
                    },
                ),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun LapRow(number: Int, lap: Long, split: Long, tag: String?) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.lap_number, number), style = MaterialTheme.typography.bodyLarge)
                if (tag != null) {
                    // Fastest/slowest is stated in words, not only by color.
                    Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = DurationFormat.stopwatch(lap),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (tag != null) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(end = 16.dp),
            )
            Text(
                text = DurationFormat.stopwatch(split),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun HistoryRow(result: StopwatchResult, is24Hour: Boolean) {
    val locale = currentLocale()
    val at = Instant.ofEpochMilli(result.finishedAt).atZone(ZoneId.systemDefault())
    val whenText = TimeText.datePattern(locale, "dMMM").format(at) + " " +
        TimeText.format(at, locale, is24Hour)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(whenText, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.laps_count, result.lapCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(DurationFormat.stopwatch(result.totalMillis), style = MaterialTheme.typography.titleMedium)
    }
}
