package com.personal.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.clock.appContainer
import com.personal.clock.domain.TimerStatus
import com.personal.clock.util.LocaleHelper
import com.personal.clock.util.TimeText
import com.personal.clock.util.goAsync

/**
 * Internal, non-exported receiver. It is reachable only through the explicit
 * PendingIntents this app creates (AlarmManager and notification actions).
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        when (intent.action) {
            ACTION_FIRE_ALARM -> startOrFallback(
                start = { RingingService.startAlarm(context, id) },
                fallback = { ringAlarmWithoutService(context, id) },
            )
            ACTION_FIRE_TIMER -> startOrFallback(
                start = { RingingService.startTimer(context, id) },
                fallback = { ringTimerWithoutService(context, id) },
            )
            ACTION_TIMER_PAUSE -> goAsync { context.appContainer.timerController.pause(id) }
            ACTION_TIMER_RESUME -> goAsync { context.appContainer.timerController.start(id) }
            ACTION_TIMER_RESET -> goAsync { context.appContainer.timerController.reset(id) }
            ACTION_TIMER_ADD_MINUTE -> goAsync { context.appContainer.timerController.addMinute(id) }
            ACTION_CANCEL_SNOOZE -> goAsync { context.appContainer.alarmController.cancelSnooze(id) }
        }
    }

    /**
     * Android 12+ allows starting the ringing foreground service from an exact alarm. If the
     * system still refuses (exact alarms revoked on Android 12/12L, so an inexact alarm fired),
     * the alarm rings through a sound-enabled notification instead of being lost.
     */
    private fun startOrFallback(start: () -> Unit, fallback: suspend () -> Unit) {
        try {
            start()
        } catch (e: IllegalStateException) { // includes ForegroundServiceStartNotAllowedException
            Log.w(TAG, "Ringing service not allowed to start, using notification fallback", e)
            goAsync { fallback() }
        }
    }

    private suspend fun ringAlarmWithoutService(context: Context, id: Long) {
        val container = context.appContainer
        val alarm = container.alarmController.onFired(id) ?: return
        val is24Hour = TimeText.is24Hour(context, container.settingsRepository.current().timeFormat)
        val timeText = TimeText.format(alarm.hour, alarm.minute, TimeText.locale(LocaleHelper.wrap(context)), is24Hour)
        container.notifications.showFallbackAlarm(alarm, timeText)
    }

    private suspend fun ringTimerWithoutService(context: Context, id: Long) {
        val container = context.appContainer
        val timer = container.timerController.get(id)
        if (timer == null || timer.status != TimerStatus.RUNNING) return
        val finished = container.timerController.finish(id) ?: return
        container.notifications.showFallbackTimer(finished)
    }

    companion object {
        private const val TAG = "AlarmReceiver"
        private const val PREFIX = "com.personal.clock.action."
        const val ACTION_FIRE_ALARM = PREFIX + "FIRE_ALARM"
        const val ACTION_FIRE_TIMER = PREFIX + "FIRE_TIMER"
        const val ACTION_TIMER_PAUSE = PREFIX + "TIMER_PAUSE"
        const val ACTION_TIMER_RESUME = PREFIX + "TIMER_RESUME"
        const val ACTION_TIMER_RESET = PREFIX + "TIMER_RESET"
        const val ACTION_TIMER_ADD_MINUTE = PREFIX + "TIMER_ADD_MINUTE"
        const val ACTION_CANCEL_SNOOZE = PREFIX + "CANCEL_SNOOZE"
        const val EXTRA_ID = "id"

        fun intent(context: Context, action: String, id: Long): Intent =
            Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)

        fun fireAlarmIntent(context: Context, id: Long) = intent(context, ACTION_FIRE_ALARM, id)
        fun fireTimerIntent(context: Context, id: Long) = intent(context, ACTION_FIRE_TIMER, id)
    }
}
