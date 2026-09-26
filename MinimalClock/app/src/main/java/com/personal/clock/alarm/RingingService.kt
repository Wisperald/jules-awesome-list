package com.personal.clock.alarm

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.personal.clock.appContainer
import com.personal.clock.domain.TimerStatus
import com.personal.clock.util.LocaleHelper
import com.personal.clock.util.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that exists **only while an alarm or timer is ringing**
 * (at most [AUTO_SILENCE_MS]). A broadcast receiver alone cannot keep sound playing,
 * and Android requires a foreground service to keep playing with the screen off.
 * Not exported; started only by this app's AlarmReceiver / notification actions.
 */
class RingingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ringer: Ringer
    private var active: RingingInfo? = null
    private var lastStartId = 0

    private val autoSilence = Runnable { scope.launch { onAutoSilence() } }

    override fun onCreate() {
        super.onCreate()
        ringer = Ringer(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
        val notifications = appContainer.notifications
        when (intent?.action) {
            ACTION_START_ALARM -> {
                // startForegroundService() contract: call startForeground() right away.
                goForeground(currentNotification() ?: notifications.ringingPlaceholder(isAlarm = true))
                scope.launch { beginAlarm(id) }
            }
            ACTION_START_TIMER -> {
                goForeground(currentNotification() ?: notifications.ringingPlaceholder(isAlarm = false))
                scope.launch { beginTimer(id) }
            }
            ACTION_DISMISS -> scope.launch { dismissAlarm(id) }
            ACTION_SNOOZE -> scope.launch { snoozeAlarm(id) }
            ACTION_STOP_TIMER -> scope.launch { stopTimer(id, restart = false) }
            ACTION_RESTART_TIMER -> scope.launch { stopTimer(id, restart = true) }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ringer.stop()
        handler.removeCallbacks(autoSilence)
        RingingState.set(null)
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun beginAlarm(id: Long) {
        val container = appContainer
        val alarm = container.alarmController.onFired(id)
        if (alarm == null) {
            stopIfIdle()
            return
        }
        interruptActive()
        val info = RingingInfo.AlarmRinging(
            id = alarm.id,
            label = alarm.label,
            hour = alarm.hour,
            minute = alarm.minute,
            timeText = TimeText.format(alarm.hour, alarm.minute, TimeText.locale(LocaleHelper.wrap(this)), is24Hour()),
            canSnooze = alarm.canSnooze,
            snoozeMinutes = alarm.snoozeMinutes,
        )
        activate(info)
        container.notifications.cancelAlarmStatus(alarm.id)
        goForeground(container.notifications.alarmRinging(info))
        ringer.start(alarm.ringtoneUri?.toUri(), alarm.vibrate, alarm.gradualVolume)
    }

    private suspend fun beginTimer(id: Long) {
        val container = appContainer
        val timer = container.timerController.get(id)
        if (timer == null || timer.status != TimerStatus.RUNNING) {
            stopIfIdle()
            return
        }
        if (!timer.isDue(container.time.elapsed() + DUE_TOLERANCE_MS)) {
            // Stale trigger (time was added): make sure the new end is scheduled.
            container.timerController.start(id)
            stopIfIdle()
            return
        }
        val finished = container.timerController.finish(id) ?: return stopIfIdle()
        interruptActive()
        val settings = container.settingsRepository.current()
        val info = RingingInfo.TimerRinging(finished.id, finished.label, finished.durationMillis)
        activate(info)
        goForeground(container.notifications.timerRinging(info))
        ringer.start(settings.timerRingtone?.toUri(), settings.timerVibrate, gradualVolume = false)
    }

    private suspend fun dismissAlarm(id: Long) {
        if ((active as? RingingInfo.AlarmRinging)?.id == id) deactivate()
        appContainer.notifications.cancelAlarmStatus(id) // fallback / snoozed notification
        appContainer.alarmController.dismiss(id)
        stopIfIdle()
    }

    private suspend fun snoozeAlarm(id: Long) {
        if ((active as? RingingInfo.AlarmRinging)?.id == id) deactivate()
        appContainer.notifications.cancelAlarmStatus(id)
        val alarm = appContainer.alarmController.snooze(id)
        if (alarm?.snoozedUntil != null) appContainer.notifications.showSnoozed(alarm, is24Hour())
        stopIfIdle()
    }

    private suspend fun stopTimer(id: Long, restart: Boolean) {
        if ((active as? RingingInfo.TimerRinging)?.id == id) deactivate()
        val controller = appContainer.timerController
        if (restart) controller.restart(id) else controller.reset(id)
        stopIfIdle()
    }

    /** A new ring replaces the current one; the interrupted alarm counts as missed. */
    private suspend fun interruptActive() {
        when (val current = active) {
            is RingingInfo.AlarmRinging -> {
                deactivate()
                appContainer.alarmController.dismiss(current.id)
                appContainer.notifications.showMissed(current)
            }
            is RingingInfo.TimerRinging -> {
                deactivate()
                appContainer.notifications.showTimerDone(current)
            }
            null -> Unit
        }
    }

    private suspend fun onAutoSilence() {
        when (val current = active) {
            is RingingInfo.AlarmRinging -> {
                deactivate()
                appContainer.alarmController.dismiss(current.id)
                appContainer.notifications.showMissed(current)
            }
            is RingingInfo.TimerRinging -> {
                deactivate()
                appContainer.notifications.showTimerDone(current)
            }
            null -> Unit
        }
        stopIfIdle()
    }

    private fun activate(info: RingingInfo) {
        active = info
        RingingState.set(info)
        handler.removeCallbacks(autoSilence)
        handler.postDelayed(autoSilence, AUTO_SILENCE_MS)
    }

    private fun deactivate() {
        ringer.stop()
        handler.removeCallbacks(autoSilence)
        active = null
        RingingState.set(null)
    }

    private fun stopIfIdle() {
        if (active != null) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        // Only stops if no newer start command arrived meanwhile.
        stopSelfResult(lastStartId)
    }

    private suspend fun is24Hour(): Boolean =
        TimeText.is24Hour(this, appContainer.settingsRepository.current().timeFormat)

    /** Notification of what is already ringing, so a new start never hides it. */
    private fun currentNotification(): Notification? = when (val current = active) {
        is RingingInfo.AlarmRinging -> appContainer.notifications.alarmRinging(current)
        is RingingInfo.TimerRinging -> appContainer.notifications.timerRinging(current)
        null -> null
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        const val NOTIFICATION_ID = 1
        private const val AUTO_SILENCE_MS = 10 * 60_000L
        private const val DUE_TOLERANCE_MS = 1_500L
        private const val PREFIX = "com.personal.clock.ringing."
        const val ACTION_START_ALARM = PREFIX + "START_ALARM"
        const val ACTION_START_TIMER = PREFIX + "START_TIMER"
        const val ACTION_DISMISS = PREFIX + "DISMISS"
        const val ACTION_SNOOZE = PREFIX + "SNOOZE"
        const val ACTION_STOP_TIMER = PREFIX + "STOP_TIMER"
        const val ACTION_RESTART_TIMER = PREFIX + "RESTART_TIMER"
        const val EXTRA_ID = "id"

        fun intent(context: Context, action: String, id: Long): Intent =
            Intent(context, RingingService::class.java).setAction(action).putExtra(EXTRA_ID, id)

        fun startAlarm(context: Context, id: Long) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_START_ALARM, id))

        fun startTimer(context: Context, id: Long) =
            ContextCompat.startForegroundService(context, intent(context, ACTION_START_TIMER, id))

        /** For in-app buttons (the app is in the foreground, so a plain start is allowed). */
        fun send(context: Context, action: String, id: Long) {
            context.startService(intent(context, action, id))
        }
    }
}
