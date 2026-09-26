package com.personal.clock.ui.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.clock.alarm.AlarmController
import com.personal.clock.data.AlarmRepository
import com.personal.clock.domain.Alarm
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlarmsViewModel(
    private val controller: AlarmController,
    repository: AlarmRepository,
) : ViewModel() {

    /** null until the first load, to avoid flashing the empty state. */
    val alarms: StateFlow<List<Alarm>?> = repository.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(alarm: Alarm, onSaved: (Alarm) -> Unit = {}) = viewModelScope.launch {
        onSaved(controller.save(alarm))
    }

    fun setEnabled(alarm: Alarm, enabled: Boolean) = viewModelScope.launch { controller.setEnabled(alarm.id, enabled) }

    fun cancelSnooze(alarm: Alarm) = viewModelScope.launch { controller.cancelSnooze(alarm.id) }

    fun delete(alarm: Alarm) = viewModelScope.launch { controller.delete(alarm.id) }
}
