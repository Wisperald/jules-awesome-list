package com.personal.clock.alarm

import com.personal.clock.data.AlarmRepository
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.AlarmScheduleCalculator
import com.personal.clock.util.TimeSource
import com.personal.clock.widget.WidgetUpdater
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single entry point for every alarm state change. Each mutation persists the alarm,
 * re-registers it with AlarmManager and refreshes the widgets, so the three never drift.
 */
class AlarmController(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
    private val widgets: WidgetUpdater,
    private val time: TimeSource,
) {
    private val mutex = Mutex()

    suspend fun get(id: Long): Alarm? = repository.get(id)

    /** Saves a new or edited alarm (always enabled after editing) and returns it. */
    suspend fun save(alarm: Alarm): Alarm = mutex.withLock {
        val stored = repository.upsert(alarm.edited().copy(enabled = true))
        sync(stored)
        stored
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) = mutex.withLock {
        repository.update(id) { it.edited().copy(enabled = enabled) }?.let { sync(it) }
    }

    /** Cancels a pending snooze without touching the alarm's regular schedule. */
    suspend fun cancelSnooze(id: Long) = mutex.withLock {
        repository.update(id) { it.dismissed() }?.let { sync(it) }
    }

    suspend fun delete(id: Long) = mutex.withLock {
        scheduler.cancelAlarm(id)
        repository.delete(id)
        widgets.updateAll()
    }

    /** Called when the alarm starts ringing: schedules the next repeat right away. */
    suspend fun onFired(id: Long): Alarm? = mutex.withLock {
        repository.update(id) { it.fired() }?.also { sync(it) }
    }

    suspend fun snooze(id: Long): Alarm? = mutex.withLock {
        repository.update(id) { it.snoozed(time.wall()) }?.also { sync(it) }
    }

    suspend fun dismiss(id: Long): Alarm? = mutex.withLock {
        repository.update(id) { it.dismissed() }?.also { sync(it) }
    }

    /**
     * Re-registers every alarm. Called after boot, app update, time / time-zone
     * change and exact-alarm permission changes; AlarmManager forgets everything on
     * reboot and RTC alarms must be recomputed when local time changes.
     */
    suspend fun rescheduleAll() = mutex.withLock {
        repository.all().forEach { schedule(it) }
        widgets.updateAll()
    }

    private suspend fun sync(alarm: Alarm) {
        schedule(alarm)
        widgets.updateAll()
    }

    private fun schedule(alarm: Alarm) {
        val trigger = AlarmScheduleCalculator.nextTrigger(alarm, time.zonedNow())
        if (trigger == null) {
            scheduler.cancelAlarm(alarm.id)
        } else {
            scheduler.scheduleAlarm(alarm.id, trigger.toInstant().toEpochMilli())
        }
    }
}
