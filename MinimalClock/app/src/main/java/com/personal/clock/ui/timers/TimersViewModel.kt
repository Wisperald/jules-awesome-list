package com.personal.clock.ui.timers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.clock.alarm.RingingInfo
import com.personal.clock.alarm.RingingService
import com.personal.clock.alarm.RingingState
import com.personal.clock.alarm.TimerController
import com.personal.clock.data.TimerRepository
import com.personal.clock.domain.TimerItem
import com.personal.clock.domain.TimerStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimersViewModel(
    application: Application,
    private val controller: TimerController,
    repository: TimerRepository,
) : AndroidViewModel(application) {

    val timers: StateFlow<List<TimerItem>?> = repository.timers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Creates and starts a timer; returns false via [onLimit] when the limit is reached. */
    fun create(label: String, durationMillis: Long, onLimit: () -> Unit = {}) = viewModelScope.launch {
        if (controller.create(label, durationMillis, start = true) == null) onLimit()
    }

    fun startOrPause(timer: TimerItem) = viewModelScope.launch {
        if (timer.status == TimerStatus.RUNNING) controller.pause(timer.id) else controller.start(timer.id)
    }

    fun addMinute(timer: TimerItem) = viewModelScope.launch { controller.addMinute(timer.id) }

    fun rename(timer: TimerItem, label: String) = viewModelScope.launch { controller.rename(timer.id, label) }

    /** Reset; if this timer is ringing, the ringing service stops the sound and resets it. */
    fun reset(timer: TimerItem) {
        if (isRinging(timer)) {
            RingingService.send(getApplication(), RingingService.ACTION_STOP_TIMER, timer.id)
        } else {
            viewModelScope.launch { controller.reset(timer.id) }
        }
    }

    fun restart(timer: TimerItem) {
        if (isRinging(timer)) {
            RingingService.send(getApplication(), RingingService.ACTION_RESTART_TIMER, timer.id)
        } else {
            viewModelScope.launch { controller.restart(timer.id) }
        }
    }

    fun delete(timer: TimerItem) {
        if (isRinging(timer)) RingingService.send(getApplication(), RingingService.ACTION_STOP_TIMER, timer.id)
        viewModelScope.launch { controller.delete(timer.id) }
    }

    private fun isRinging(timer: TimerItem): Boolean =
        (RingingState.current.value as? RingingInfo.TimerRinging)?.id == timer.id
}
