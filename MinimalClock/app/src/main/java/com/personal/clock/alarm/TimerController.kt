package com.personal.clock.alarm

import com.personal.clock.data.TimerRepository
import com.personal.clock.domain.TimerItem
import com.personal.clock.domain.TimerStatus
import com.personal.clock.notification.Notifications
import com.personal.clock.util.TimeSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Timer state changes: persist → (re)schedule the exact alarm → update the countdown
 * notification. The countdown in the notification is rendered by the system
 * (chronometer), so no service or wake-ups are needed while a timer runs.
 */
class TimerController(
    private val repository: TimerRepository,
    private val scheduler: AlarmScheduler,
    private val notifications: Notifications,
    private val time: TimeSource,
    /** Whether times are shown in 24-hour format (reads the current setting). */
    private val is24Hour: suspend () -> Boolean,
) {
    private val mutex = Mutex()

    suspend fun get(id: Long): TimerItem? = repository.get(id)

    suspend fun create(label: String, durationMillis: Long, start: Boolean): TimerItem? = mutex.withLock {
        repository.add { id ->
            val timer = TimerItem(id = id, label = label.trim(), durationMillis = durationMillis)
            if (start) timer.start(time.elapsed(), time.wall()) else timer
        }?.also { sync(it) }
    }

    suspend fun start(id: Long) = change(id) { it.start(time.elapsed(), time.wall()) }
    suspend fun pause(id: Long) = change(id) { it.pause(time.elapsed()) }
    suspend fun reset(id: Long) = change(id) { it.reset() }
    suspend fun restart(id: Long) = change(id) { it.restart(time.elapsed(), time.wall()) }
    suspend fun addMinute(id: Long) = change(id) { it.plus(ONE_MINUTE, time.elapsed(), time.wall()) }
    suspend fun rename(id: Long, label: String) = change(id) { it.copy(label = label.trim()) }
    suspend fun finish(id: Long) = change(id) { it.finish() }

    suspend fun delete(id: Long) = mutex.withLock {
        scheduler.cancelTimer(id)
        notifications.cancelTimer(id)
        repository.delete(id)
    }

    /**
     * Re-registers running timers. After a reboot elapsed time restarted from zero,
     * so running timers are re-anchored from the wall clock first.
     */
    suspend fun rescheduleAll(afterReboot: Boolean) = mutex.withLock {
        val timers = if (afterReboot) {
            repository.updateAll { it.afterReboot(time.elapsed(), time.wall()) }
        } else {
            repository.all()
        }
        // Timers that ran out while the phone was off are due now: AlarmManager fires them
        // immediately. (Starting the ringing service directly from BOOT_COMPLETED is not
        // allowed for media-playback foreground services on Android 15+.)
        timers.forEach { sync(it) }
    }

    private suspend fun change(id: Long, transform: (TimerItem) -> TimerItem): TimerItem? = mutex.withLock {
        repository.update(id, transform)?.also { sync(it) }
    }

    private suspend fun sync(timer: TimerItem) {
        when (timer.status) {
            TimerStatus.RUNNING -> {
                scheduler.scheduleTimer(timer.id, timer.endElapsed)
                notifications.showTimerRunning(timer, is24Hour())
            }
            TimerStatus.PAUSED -> {
                scheduler.cancelTimer(timer.id)
                notifications.showTimerPaused(timer)
            }
            TimerStatus.IDLE, TimerStatus.FINISHED -> {
                scheduler.cancelTimer(timer.id)
                notifications.cancelTimer(timer.id)
            }
        }
    }

    private companion object {
        const val ONE_MINUTE = 60_000L
    }
}
