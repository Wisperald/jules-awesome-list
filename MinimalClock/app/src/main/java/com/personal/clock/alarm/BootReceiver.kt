package com.personal.clock.alarm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.personal.clock.appContainer
import com.personal.clock.util.goAsync

/**
 * Restores AlarmManager registrations. Exported only because the system must deliver
 * these broadcasts; every one of them is a protected broadcast that ordinary apps
 * cannot send, and anything else is ignored.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED) return
        val container = context.appContainer
        val afterReboot = action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        goAsync {
            container.alarmController.rescheduleAll()
            container.timerController.rescheduleAll(afterReboot)
            if (action == Intent.ACTION_LOCALE_CHANGED) {
                container.notifications.ensureChannels()
            }
        }
    }

    private companion object {
        // The exact-alarm action is a compile-time String constant: referencing it is safe on
        // API 26–30, where that broadcast simply never arrives.
        @SuppressLint("InlinedApi")
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}
