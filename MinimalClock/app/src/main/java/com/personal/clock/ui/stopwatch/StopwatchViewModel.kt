package com.personal.clock.ui.stopwatch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.clock.data.StopwatchRepository
import com.personal.clock.domain.StopwatchResult
import com.personal.clock.domain.StopwatchState
import com.personal.clock.util.TimeSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StopwatchViewModel(
    private val repository: StopwatchRepository,
    private val time: TimeSource,
) : ViewModel() {

    private val mutex = Mutex()

    val state: StateFlow<StopwatchState> = repository.state
        .map { it.sanitized(time.elapsed()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopwatchState())

    val history: StateFlow<List<StopwatchResult>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startOrPause() = update { s -> if (s.running) s.pause(time.elapsed()) else s.start(time.elapsed()) }

    fun lap() = update { it.lap(time.elapsed()) }

    fun reset() = viewModelScope.launch {
        mutex.withLock {
            val current = state.value
            repository.resetAndArchive(current.toResult(time.wall(), time.elapsed()))
        }
    }

    fun clearHistory() = viewModelScope.launch { repository.clearHistory() }

    // Reads the latest state inside the lock so rapid taps are applied in order.
    private fun update(transform: (StopwatchState) -> StopwatchState) = viewModelScope.launch {
        mutex.withLock { repository.save(transform(state.value)) }
    }
}
