@file:OptIn(ExperimentalMaterial3Api::class)

package com.personal.clock.ui.settings

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.clock.R
import com.personal.clock.data.AppLanguage
import com.personal.clock.data.AppSettings
import com.personal.clock.data.ThemeMode
import com.personal.clock.data.TimeFormatMode
import com.personal.clock.domain.Alarm
import com.personal.clock.ui.AppViewModels
import com.personal.clock.ui.alarms.SettingRow
import com.personal.clock.ui.alarms.SwitchRow
import com.personal.clock.ui.components.SectionHeader
import com.personal.clock.ui.components.Stepper
import com.personal.clock.ui.components.openExactAlarmSettings
import com.personal.clock.ui.components.openFullScreenSettings
import com.personal.clock.ui.components.rememberNotificationPermissionRequest
import com.personal.clock.ui.components.rememberPermissionStatus
import com.personal.clock.ui.components.rememberRingtonePicker
import com.personal.clock.ui.components.ringtoneTitle
import com.personal.clock.util.LocaleHelper

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModels.Factory),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var language by remember { mutableStateOf(LocaleHelper.current(context)) }
    val pickTimerSound = rememberRingtonePicker(settings.timerRingtone) { viewModel.setTimerRingtone(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_appearance))
            RadioGroup(
                options = ThemeMode.entries,
                selected = settings.theme,
                label = {
                    stringResource(
                        when (it) {
                            ThemeMode.SYSTEM -> R.string.theme_system
                            ThemeMode.LIGHT -> R.string.theme_light
                            ThemeMode.DARK -> R.string.theme_dark
                        },
                    )
                },
                onSelect = viewModel::setTheme,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Padded { SwitchRow(stringResource(R.string.dynamic_color), settings.dynamicColor, viewModel::setDynamicColor) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_time))
            RadioGroup(
                options = TimeFormatMode.entries,
                selected = settings.timeFormat,
                label = {
                    stringResource(
                        when (it) {
                            TimeFormatMode.SYSTEM -> R.string.time_format_system
                            TimeFormatMode.H12 -> R.string.time_format_12
                            TimeFormatMode.H24 -> R.string.time_format_24
                        },
                    )
                },
                onSelect = viewModel::setTimeFormat,
            )
            Padded { SwitchRow(stringResource(R.string.show_seconds), settings.showSeconds, viewModel::setShowSeconds) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_language))
            RadioGroup(
                options = AppLanguage.entries,
                selected = language,
                label = {
                    stringResource(
                        when (it) {
                            AppLanguage.SYSTEM -> R.string.language_system
                            AppLanguage.RUSSIAN -> R.string.language_ru
                            AppLanguage.KAZAKH -> R.string.language_kk
                            AppLanguage.ENGLISH -> R.string.language_en
                        },
                    )
                },
                onSelect = { selected ->
                    if (selected != language) {
                        language = selected
                        viewModel.setLanguage(selected) {
                            LocaleHelper.apply(context, selected)
                            // Android 13+ recreates the activity itself; older versions need a manual recreate.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) activity?.recreate()
                        }
                    }
                },
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_alarms_timers))
            Padded {
                Stepper(
                    label = stringResource(R.string.default_snooze),
                    valueText = stringResource(R.string.minutes_short, settings.defaultSnoozeMinutes),
                    onDecrement = { viewModel.setDefaultSnooze(settings.defaultSnoozeMinutes - 1) },
                    onIncrement = { viewModel.setDefaultSnooze(settings.defaultSnoozeMinutes + 1) },
                    canDecrement = settings.defaultSnoozeMinutes > Alarm.SNOOZE_MINUTES_RANGE.first,
                    canIncrement = settings.defaultSnoozeMinutes < Alarm.SNOOZE_MINUTES_RANGE.last,
                )
            }
            Padded { SettingRow(stringResource(R.string.timer_sound), ringtoneTitle(settings.timerRingtone), pickTimerSound) }
            Padded { SwitchRow(stringResource(R.string.timer_vibration), settings.timerVibrate, viewModel::setTimerVibrate) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_permissions))
            PermissionsSection()

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionHeader(stringResource(R.string.settings_about))
            Padded {
                Text(
                    text = stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionsSection() {
    val context = LocalContext.current
    val status = rememberPermissionStatus()
    val requestNotifications = rememberNotificationPermissionRequest()
    Column(Modifier.padding(horizontal = 16.dp)) {
        PermissionRow(
            title = stringResource(R.string.perm_notifications),
            description = stringResource(R.string.perm_notifications_rationale),
            granted = status.notifications,
            onFix = requestNotifications,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionRow(
                title = stringResource(R.string.perm_exact),
                description = stringResource(R.string.perm_exact_rationale),
                granted = status.exactAlarms,
                onFix = { openExactAlarmSettings(context) },
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PermissionRow(
                title = stringResource(R.string.perm_fullscreen),
                description = stringResource(R.string.perm_fullscreen_rationale),
                granted = status.fullScreen,
                onFix = { openFullScreenSettings(context) },
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, granted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            // Status in words (not only color).
            Text(
                text = stringResource(if (granted) R.string.perm_granted else R.string.perm_denied),
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!granted) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!granted) {
            TextButton(onClick = onFix) { Text(stringResource(R.string.perm_fix)) }
        }
    }
}

@Composable
private fun <T> RadioGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Text(label(option), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) { content() }
}
