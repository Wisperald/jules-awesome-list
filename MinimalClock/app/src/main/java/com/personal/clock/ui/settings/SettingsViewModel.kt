package com.personal.clock.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.clock.data.AppLanguage
import com.personal.clock.data.SettingsRepository
import com.personal.clock.data.ThemeMode
import com.personal.clock.data.TimeFormatMode
import com.personal.clock.widget.WidgetUpdater
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val widgets: WidgetUpdater,
) : ViewModel() {

    fun setTheme(value: ThemeMode) = viewModelScope.launch { repository.setTheme(value) }

    fun setDynamicColor(value: Boolean) = viewModelScope.launch { repository.setDynamicColor(value) }

    fun setTimeFormat(value: TimeFormatMode) = viewModelScope.launch {
        repository.setTimeFormat(value)
        widgets.updateAll()
    }

    fun setShowSeconds(value: Boolean) = viewModelScope.launch { repository.setShowSeconds(value) }

    fun setDefaultSnooze(minutes: Int) = viewModelScope.launch { repository.setDefaultSnooze(minutes) }

    fun setTimerRingtone(uri: String?) = viewModelScope.launch { repository.setTimerRingtone(uri) }

    fun setTimerVibrate(value: Boolean) = viewModelScope.launch { repository.setTimerVibrate(value) }

    /** Persists the language; the caller applies it (LocaleHelper) and recreates the UI if needed. */
    fun setLanguage(value: AppLanguage, onStored: () -> Unit) = viewModelScope.launch {
        repository.setLanguage(value)
        widgets.updateAll()
        onStored()
    }
}
