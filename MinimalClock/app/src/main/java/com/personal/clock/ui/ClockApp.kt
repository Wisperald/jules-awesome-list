@file:OptIn(ExperimentalMaterial3Api::class)

package com.personal.clock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.clock.R
import com.personal.clock.alarm.RingingInfo
import com.personal.clock.alarm.RingingService
import com.personal.clock.alarm.RingingState
import com.personal.clock.data.AppSettings
import com.personal.clock.ui.alarms.AlarmsScreen
import com.personal.clock.ui.clock.ClockScreen
import com.personal.clock.ui.components.windowSizeDp
import com.personal.clock.ui.settings.SettingsScreen
import com.personal.clock.ui.stopwatch.StopwatchScreen
import com.personal.clock.ui.timers.TimersScreen

@Composable
fun ClockApp(
    settings: AppSettings,
    requestedTab: AppTab?,
    onRequestedTabHandled: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(AppTab.CLOCK) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            showSettings = false
            onRequestedTabHandled()
        }
    }

    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(settings = settings, onBack = { showSettings = false })
        return
    }

    // Wide screens (tablets, landscape, foldables) use a navigation rail instead of a bottom bar.
    val wide = windowSizeDp().width >= 600.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.titleRes)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
                    }
                },
            )
        },
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    AppTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                            label = { Text(stringResource(item.titleRes), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (wide) {
                NavigationRail {
                    AppTab.entries.forEach { item ->
                        NavigationRailItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Icon(painterResource(item.iconRes), contentDescription = null) },
                            label = { Text(stringResource(item.titleRes), maxLines = 1) },
                        )
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxSize()) {
                RingingBanner()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(targetState = tab, label = "tab") { current ->
                        when (current) {
                            AppTab.CLOCK -> ClockScreen(settings)
                            AppTab.ALARMS -> AlarmsScreen(settings)
                            AppTab.STOPWATCH -> StopwatchScreen(settings)
                            AppTab.TIMERS -> TimersScreen()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown while something rings and the app is open, so it can always be silenced
 * from inside the app (also when notifications are disabled).
 */
@Composable
private fun RingingBanner() {
    val ringing by RingingState.current.collectAsStateWithLifecycle()
    val info = ringing ?: return
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_alarm), contentDescription = null)
            val text = when (info) {
                is RingingInfo.AlarmRinging -> info.label.ifBlank { stringResource(R.string.alarm_default_title) } + " · " + info.timeText
                is RingingInfo.TimerRinging -> stringResource(R.string.timer_finished_title)
            }
            Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
            when (info) {
                is RingingInfo.AlarmRinging -> {
                    if (info.canSnooze) {
                        TextButton(onClick = { RingingService.send(context, RingingService.ACTION_SNOOZE, info.id) }) {
                            Text(stringResource(R.string.action_snooze))
                        }
                    }
                    TextButton(onClick = { RingingService.send(context, RingingService.ACTION_DISMISS, info.id) }) {
                        Text(stringResource(R.string.action_dismiss))
                    }
                }
                is RingingInfo.TimerRinging -> TextButton(onClick = { RingingService.send(context, RingingService.ACTION_STOP_TIMER, info.id) }) {
                    Text(stringResource(R.string.action_stop))
                }
            }
        }
    }
}
