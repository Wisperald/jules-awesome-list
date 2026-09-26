package com.personal.clock.ui.ringing

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.clock.R
import com.personal.clock.alarm.RingingInfo
import com.personal.clock.alarm.RingingService
import com.personal.clock.alarm.RingingState
import com.personal.clock.appContainer
import com.personal.clock.data.AppSettings
import com.personal.clock.domain.DurationFormat
import com.personal.clock.ui.components.AutoSizeNumber
import com.personal.clock.ui.components.rememberWallClock
import com.personal.clock.ui.theme.ClockTheme
import com.personal.clock.util.LocaleHelper
import com.personal.clock.util.TimeText
import com.personal.clock.ui.components.currentLocale
import java.time.ZoneId

/**
 * Full-screen ringing UI, shown over the lock screen via the notification's
 * full-screen intent. Not exported. Finishes itself as soon as nothing is ringing.
 */
class AlarmRingingActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            val settings by appContainer.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            val ringing by RingingState.current.collectAsStateWithLifecycle()
            LaunchedEffect(ringing) { if (ringing == null) finish() }
            BackHandler { /* an explicit choice is required */ }
            ClockTheme(theme = settings.theme, dynamicColor = settings.dynamicColor) {
                ringing?.let { RingingScreen(it, settings) }
            }
        }
    }
}

@Composable
private fun RingingScreen(info: RingingInfo, settings: AppSettings) {
    val context = LocalContext.current
    val now by rememberWallClock()
    val locale = currentLocale()
    val is24Hour = TimeText.is24Hour(context, settings.timeFormat)
    val clock = TimeText.format(now.atZone(ZoneId.systemDefault()), locale, is24Hour)

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Icon(
                painter = painterResource(if (info is RingingInfo.TimerRinging) R.drawable.ic_timer else R.drawable.ic_alarm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            AutoSizeNumber(text = clock, maxFontSize = 96.sp, modifier = Modifier.fillMaxWidth())
            val title = when (info) {
                is RingingInfo.AlarmRinging -> info.label.ifBlank { stringResource(R.string.alarm_default_title) }
                is RingingInfo.TimerRinging -> stringResource(R.string.timer_finished_title)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (info is RingingInfo.TimerRinging) {
                Text(
                    text = info.label.ifBlank { stringResource(R.string.timer_default_name, DurationFormat.countdown(info.durationMillis)) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.heightIn(min = 32.dp))
            when (info) {
                is RingingInfo.AlarmRinging -> {
                    if (info.canSnooze) {
                        OutlinedButton(
                            onClick = { RingingService.send(context, RingingService.ACTION_SNOOZE, info.id) },
                            modifier = Modifier.bigButton(),
                        ) {
                            Text(stringResource(R.string.action_snooze_minutes, info.snoozeMinutes), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Button(
                        onClick = { RingingService.send(context, RingingService.ACTION_DISMISS, info.id) },
                        modifier = Modifier.bigButton(),
                    ) {
                        Text(stringResource(R.string.action_dismiss), style = MaterialTheme.typography.titleMedium)
                    }
                }
                is RingingInfo.TimerRinging -> {
                    OutlinedButton(
                        onClick = { RingingService.send(context, RingingService.ACTION_RESTART_TIMER, info.id) },
                        modifier = Modifier.bigButton(),
                    ) {
                        Text(stringResource(R.string.action_restart), style = MaterialTheme.typography.titleMedium)
                    }
                    Button(
                        onClick = { RingingService.send(context, RingingService.ACTION_STOP_TIMER, info.id) },
                        modifier = Modifier.bigButton(),
                    ) {
                        Text(stringResource(R.string.action_stop), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private fun Modifier.bigButton(): Modifier = this
    .fillMaxWidth()
    .widthIn(max = 420.dp)
    .heightIn(min = 64.dp)
