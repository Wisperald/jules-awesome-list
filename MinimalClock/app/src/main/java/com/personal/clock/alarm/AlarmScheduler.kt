package com.personal.clock.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.personal.clock.MainActivity
import com.personal.clock.ui.AppTab

/**
 * Thin wrapper over [AlarmManager]. Nothing runs in the background between rings:
 * the system wakes the app exactly when needed.
 *
 *  - Alarms use [AlarmManager.setAlarmClock]: exact, allowed in Doze, and shown to the
 *    user as the next alarm in the status bar / lock screen.
 *  - Timers use `setExactAndAllowWhileIdle` on the ELAPSED_REALTIME clock so wall-clock
 *    and time-zone changes never shift them.
 *  - If exact alarms are not permitted (only possible on Android 12/12L when the user
 *    revoked the permission) a best-effort inexact alarm is used and the UI warns.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun scheduleAlarm(alarmId: Long, triggerAtWallMillis: Long) {
        val operation = alarmOperation(alarmId)
        try {
            if (canScheduleExact()) {
                val show = PendingIntent.getActivity(
                    context,
                    requestCode(alarmId),
                    MainActivity.intent(context, AppTab.ALARMS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtWallMillis, show), operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtWallMillis, operation)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
            Log.w(TAG, "Exact alarm denied, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtWallMillis, operation)
        }
    }

    fun cancelAlarm(alarmId: Long) {
        alarmManager.cancel(alarmOperation(alarmId))
    }

    fun scheduleTimer(timerId: Long, triggerAtElapsed: Long) {
        val operation = timerOperation(timerId)
        try {
            if (canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, operation)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, operation)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact timer denied, falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsed, operation)
        }
    }

    fun cancelTimer(timerId: Long) {
        alarmManager.cancel(timerOperation(timerId))
    }

    private fun alarmOperation(alarmId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(alarmId),
        AlarmReceiver.fireAlarmIntent(context, alarmId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun timerOperation(timerId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode(timerId),
        AlarmReceiver.fireTimerIntent(context, timerId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestCode(id: Long): Int = (id and 0x7FFFFFFF).toInt()

    private companion object {
        const val TAG = "AlarmScheduler"
    }
}
