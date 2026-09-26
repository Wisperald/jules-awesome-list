package com.personal.clock.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personal.clock.AppContainer
import com.personal.clock.ClockApplication
import com.personal.clock.ui.alarms.AlarmsViewModel
import com.personal.clock.ui.clock.ClockViewModel
import com.personal.clock.ui.settings.SettingsViewModel
import com.personal.clock.ui.stopwatch.StopwatchViewModel
import com.personal.clock.ui.timers.TimersViewModel

/** ViewModel factory wired to the manual [AppContainer]. */
object AppViewModels {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { ClockViewModel(container().worldClockRepository, container().settingsRepository) }
        initializer { AlarmsViewModel(container().alarmController, container().alarmRepository) }
        initializer { StopwatchViewModel(container().stopwatchRepository, container().time) }
        initializer { TimersViewModel(application(), container().timerController, container().timerRepository) }
        initializer { SettingsViewModel(container().settingsRepository, container().widgetUpdater) }
    }

    private fun CreationExtras.application(): ClockApplication = this[APPLICATION_KEY] as ClockApplication

    private fun CreationExtras.container(): AppContainer = application().container
}
