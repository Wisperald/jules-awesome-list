package com.personal.clock.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What is ringing right now; observed by the full-screen ringing activity. */
sealed interface RingingInfo {
    val id: Long

    data class AlarmRinging(
        override val id: Long,
        val label: String,
        val hour: Int,
        val minute: Int,
        /** Pre-formatted time respecting the app's 12/24-hour setting. */
        val timeText: String,
        val canSnooze: Boolean,
        val snoozeMinutes: Int,
    ) : RingingInfo

    data class TimerRinging(
        override val id: Long,
        val label: String,
        val durationMillis: Long,
    ) : RingingInfo
}

object RingingState {
    private val mutable = MutableStateFlow<RingingInfo?>(null)
    val current: StateFlow<RingingInfo?> = mutable.asStateFlow()

    internal fun set(info: RingingInfo?) {
        mutable.value = info
    }
}
