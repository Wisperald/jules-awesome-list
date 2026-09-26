package com.personal.clock.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Computes when alarms ring. All math is done with [ZonedDateTime] in the zone of
 * `now`, so daylight-saving transitions follow the tz database shipped with the OS:
 *  - a time inside a DST gap (e.g. 02:30 when clocks jump 02:00→03:00) rings at the
 *    shifted time (03:30);
 *  - a time inside a DST overlap rings once, at the first (earlier-offset) occurrence.
 * Callers must recompute after time / time-zone changes (see BootReceiver).
 */
object AlarmScheduleCalculator {

    /** Next regular (non-snooze) occurrence strictly after [now]. */
    fun nextOccurrence(alarm: Alarm, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(alarm.hour, alarm.minute)
        val today = now.toLocalDate()
        // 0..7 covers "later today" plus a full week for repeating alarms.
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (alarm.isRepeating && !alarm.repeatsOn(date.dayOfWeek)) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No occurrence found for alarm ${alarm.id}")
    }

    /** When the alarm will ring next, taking snooze into account; null if it will not ring. */
    fun nextTrigger(alarm: Alarm, now: ZonedDateTime): ZonedDateTime? {
        val snooze = alarm.snoozedUntil
        if (snooze != null && snooze > now.toInstant().toEpochMilli()) {
            return Instant.ofEpochMilli(snooze).atZone(now.zone)
        }
        if (!alarm.enabled) return null
        return nextOccurrence(alarm, now)
    }

    /** The alarm that rings first, with its trigger time. */
    fun nextAlarm(alarms: List<Alarm>, now: ZonedDateTime): Pair<Alarm, ZonedDateTime>? =
        alarms.mapNotNull { alarm -> nextTrigger(alarm, now)?.let { alarm to it } }
            .minByOrNull { it.second.toInstant() }

    fun timeUntil(trigger: ZonedDateTime, now: ZonedDateTime): Duration =
        Duration.between(now.toInstant(), trigger.toInstant()).let { if (it.isNegative) Duration.ZERO else it }
}
