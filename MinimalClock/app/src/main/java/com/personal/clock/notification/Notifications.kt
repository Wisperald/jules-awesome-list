package com.personal.clock.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.personal.clock.MainActivity
import com.personal.clock.R
import com.personal.clock.alarm.AlarmReceiver
import com.personal.clock.alarm.RingingInfo
import com.personal.clock.alarm.RingingService
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.DurationFormat
import com.personal.clock.domain.TimerItem
import com.personal.clock.ui.AppTab
import com.personal.clock.ui.ringing.AlarmRingingActivity
import com.personal.clock.util.LocaleHelper
import com.personal.clock.util.TimeText
import java.time.Instant
import java.time.ZoneId

/**
 * All notifications. Channels are silent: sound and vibration are produced by
 * [com.personal.clock.alarm.Ringer] so the user's alarm tone, volume ramp and the
 * alarm audio stream are respected.
 */
class Notifications(private val appContext: Context) {

    private val manager = NotificationManagerCompat.from(appContext)

    /** Strings in the app's chosen language (matters on Android 8–12). */
    private val context: Context get() = LocaleHelper.wrap(appContext)

    fun ensureChannels() {
        val ctx = context
        val channels = listOf(
            NotificationChannel(CH_ALARM, ctx.getString(R.string.channel_alarms), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_alarms_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_TIMER_DONE, ctx.getString(R.string.channel_timer_done), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_timer_done_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_TIMER_RUNNING, ctx.getString(R.string.channel_timer_running), NotificationManager.IMPORTANCE_LOW).apply {
                description = ctx.getString(R.string.channel_timer_running_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            },
            NotificationChannel(CH_STATUS, ctx.getString(R.string.channel_alarm_status), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = ctx.getString(R.string.channel_alarm_status_desc)
                setSound(null, null)
                enableVibration(false)
            },
            // Used only if Android refuses to start the ringing service: the channel itself
            // plays the system alarm sound on the alarm stream.
            NotificationChannel(CH_FALLBACK, ctx.getString(R.string.channel_alarm_fallback), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.channel_alarm_fallback_desc)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 700, 700, 700, 700)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannels(channels)
    }

    // --- Ringing (foreground service) ---------------------------------------------------

    fun ringingPlaceholder(isAlarm: Boolean): Notification =
        NotificationCompat.Builder(appContext, if (isAlarm) CH_ALARM else CH_TIMER_DONE)
            .setSmallIcon(if (isAlarm) R.drawable.ic_alarm else R.drawable.ic_timer)
            .setContentTitle(context.getString(if (isAlarm) R.string.alarm_default_title else R.string.timer_default_title))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(if (isAlarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setSilent(true)
            .build()

    fun alarmRinging(info: RingingInfo.AlarmRinging): Notification {
        val ctx = context
        val fullScreen = ringingActivityIntent()
        return NotificationCompat.Builder(appContext, CH_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(info.label.ifBlank { ctx.getString(R.string.alarm_default_title) })
            .setContentText(info.timeText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .apply {
                if (info.canSnooze) {
                    addAction(
                        R.drawable.ic_snooze,
                        ctx.getString(R.string.action_snooze_minutes, info.snoozeMinutes),
                        serviceIntent(RingingService.ACTION_SNOOZE, info.id),
                    )
                }
            }
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_dismiss), serviceIntent(RingingService.ACTION_DISMISS, info.id))
            .build()
    }

    fun timerRinging(info: RingingInfo.TimerRinging): Notification {
        val ctx = context
        val fullScreen = ringingActivityIntent()
        return NotificationCompat.Builder(appContext, CH_TIMER_DONE)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(ctx.getString(R.string.timer_finished_title))
            .setContentText(timerName(ctx, info.label, info.durationMillis))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setSilent(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(R.drawable.ic_stop, ctx.getString(R.string.action_stop), serviceIntent(RingingService.ACTION_STOP_TIMER, info.id))
            .addAction(R.drawable.ic_replay, ctx.getString(R.string.action_restart), serviceIntent(RingingService.ACTION_RESTART_TIMER, info.id))
            .build()
    }

    // --- Alarm status (snoozed / missed) ----------------------------------------------

    fun showSnoozed(alarm: Alarm, is24Hour: Boolean) {
        val until = alarm.snoozedUntil ?: return
        val ctx = context
        val untilText = TimeText.format(
            Instant.ofEpochMilli(until).atZone(ZoneId.systemDefault()),
            TimeText.locale(ctx),
            is24Hour,
        )
        val notification = NotificationCompat.Builder(appContext, CH_STATUS)
            .setSmallIcon(R.drawable.ic_snooze)
            .setContentTitle(alarm.label.ifBlank { ctx.getString(R.string.alarm_default_title) })
            .setContentText(ctx.getString(R.string.alarm_snoozed_until, untilText))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(AppTab.ALARMS))
            .addAction(
                R.drawable.ic_close,
                ctx.getString(R.string.action_dismiss),
                broadcastIntent(AlarmReceiver.ACTION_CANCEL_SNOOZE, alarm.id),
            )
            .build()
        notify(STATUS_BASE + alarm.id.toInt(), notification)
    }

    fun showMissed(info: RingingInfo.AlarmRinging) {
        val ctx = context
        val notification = NotificationCompat.Builder(appContext, CH_STATUS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(ctx.getString(R.string.alarm_missed_title))
            .setContentText(listOf(info.timeText, info.label).filter { it.isNotBlank() }.joinToString(" · "))
            .setAutoCancel(true)
            .setContentIntent(openApp(AppTab.ALARMS))
            .build()
        notify(STATUS_BASE + info.id.toInt(), notification)
    }

    fun cancelAlarmStatus(alarmId: Long) = manager.cancel(STATUS_BASE + alarmId.toInt())

    // --- Fallback ringing (no foreground service) ----------------------------------------

    /**
     * Rings through a sound-enabled notification. Used only when the system does not allow
     * the ringing service to start (Android 12/12L after the user revoked exact alarms).
     */
    fun showFallbackAlarm(alarm: Alarm, timeText: String) {
        val ctx = context
        val notification = NotificationCompat.Builder(appContext, CH_FALLBACK)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm.label.ifBlank { ctx.getString(R.string.alarm_default_title) })
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(AppTab.ALARMS))
            .apply {
                if (alarm.canSnooze) {
                    addAction(
                        R.drawable.ic_snooze,
                        ctx.getString(R.string.action_snooze_minutes, alarm.snoozeMinutes),
                        serviceIntent(RingingService.ACTION_SNOOZE, alarm.id),
                    )
                }
            }
            .addAction(R.drawable.ic_close, ctx.getString(R.string.action_dismiss), serviceIntent(RingingService.ACTION_DISMISS, alarm.id))
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notify(STATUS_BASE + alarm.id.toInt(), notification)
    }

    fun showFallbackTimer(timer: TimerItem) {
        val ctx = context
        val notification = NotificationCompat.Builder(appContext, CH_FALLBACK)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(ctx.getString(R.string.timer_finished_title))
            .setContentText(timerName(ctx, timer.label, timer.durationMillis))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp(AppTab.TIMERS))
            .addAction(R.drawable.ic_stop, ctx.getString(R.string.action_stop), serviceIntent(RingingService.ACTION_STOP_TIMER, timer.id))
            .addAction(R.drawable.ic_replay, ctx.getString(R.string.action_restart), serviceIntent(RingingService.ACTION_RESTART_TIMER, timer.id))
            .build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        notify(TIMER_BASE + timer.id.toInt(), notification)
    }

    // --- Timers -------------------------------------------------------------------------

    /** Countdown is drawn by the system chronometer: zero wake-ups while the timer runs. */
    fun showTimerRunning(timer: TimerItem, is24Hour: Boolean) {
        val ctx = context
        val endText = TimeText.format(
            Instant.ofEpochMilli(timer.endWallClock).atZone(ZoneId.systemDefault()),
            TimeText.locale(ctx),
            is24Hour,
        )
        val notification = NotificationCompat.Builder(appContext, CH_TIMER_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(timerName(ctx, timer.label, timer.durationMillis))
            .setContentText(ctx.getString(R.string.timer_ends_at, endText))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(timer.endWallClock)
            .setShowWhen(true)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(AppTab.TIMERS))
            .addAction(R.drawable.ic_pause, ctx.getString(R.string.action_pause), broadcastIntent(AlarmReceiver.ACTION_TIMER_PAUSE, timer.id))
            .addAction(R.drawable.ic_add, ctx.getString(R.string.action_add_minute), broadcastIntent(AlarmReceiver.ACTION_TIMER_ADD_MINUTE, timer.id))
            .addAction(R.drawable.ic_stop, ctx.getString(R.string.action_reset), broadcastIntent(AlarmReceiver.ACTION_TIMER_RESET, timer.id))
            .build()
        notify(TIMER_BASE + timer.id.toInt(), notification)
    }

    fun showTimerPaused(timer: TimerItem) {
        val ctx = context
        val notification = NotificationCompat.Builder(appContext, CH_TIMER_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(timerName(ctx, timer.label, timer.durationMillis))
            .setContentText(ctx.getString(R.string.timer_paused_at, DurationFormat.countdown(timer.remainingMillis)))
            .setShowWhen(false)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(AppTab.TIMERS))
            .addAction(R.drawable.ic_play, ctx.getString(R.string.action_resume), broadcastIntent(AlarmReceiver.ACTION_TIMER_RESUME, timer.id))
            .addAction(R.drawable.ic_stop, ctx.getString(R.string.action_reset), broadcastIntent(AlarmReceiver.ACTION_TIMER_RESET, timer.id))
            .build()
        notify(TIMER_BASE + timer.id.toInt(), notification)
    }

    /** Non-ringing "finished" notice (after auto-silence or when another ring took over). */
    fun showTimerDone(info: RingingInfo.TimerRinging) {
        val ctx = context
        val notification = NotificationCompat.Builder(appContext, CH_STATUS)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle(ctx.getString(R.string.timer_finished_title))
            .setContentText(timerName(ctx, info.label, info.durationMillis))
            .setAutoCancel(true)
            .setContentIntent(openApp(AppTab.TIMERS))
            .build()
        notify(TIMER_BASE + info.id.toInt(), notification)
    }

    fun cancelTimer(timerId: Long) = manager.cancel(TIMER_BASE + timerId.toInt())

    // --- Helpers ------------------------------------------------------------------------

    private fun timerName(ctx: Context, label: String, durationMillis: Long): String =
        label.ifBlank { ctx.getString(R.string.timer_default_name, DurationFormat.countdown(durationMillis)) }

    @SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    private fun notify(id: Int, notification: Notification) {
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked concurrently.
        }
    }

    private fun ringingActivityIntent(): PendingIntent = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, AlarmRingingActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(tab: AppTab): PendingIntent = PendingIntent.getActivity(
        appContext,
        tab.ordinal + 100,
        MainActivity.intent(appContext, tab),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun serviceIntent(action: String, id: Long): PendingIntent = PendingIntent.getService(
        appContext,
        (id and 0xFFFFF).toInt(),
        RingingService.intent(appContext, action, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcastIntent(action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        appContext,
        (id and 0xFFFFF).toInt(),
        AlarmReceiver.intent(appContext, action, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CH_ALARM = "alarm_ringing"
        const val CH_TIMER_DONE = "timer_ringing"
        const val CH_TIMER_RUNNING = "timer_running"
        const val CH_STATUS = "alarm_status"
        const val CH_FALLBACK = "alarm_fallback"
        private const val STATUS_BASE = 20_000
        private const val TIMER_BASE = 10_000

        fun canUseFullScreenIntent(context: Context): Boolean =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            } else {
                true
            }
    }
}
