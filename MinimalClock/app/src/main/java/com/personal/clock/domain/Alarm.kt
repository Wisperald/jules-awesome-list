package com.personal.clock.domain

import java.time.DayOfWeek

/**
 * A user alarm. Pure data: scheduling math lives in [AlarmScheduleCalculator],
 * persistence in [AlarmCodec]. Times are local wall-clock times in the device zone.
 */
data class Alarm(
    val id: Long,
    val hour: Int,
    val minute: Int,
    /** Bit mask of repeat days, see [DaysOfWeek]. 0 = one-time alarm. */
    val daysMask: Int = DaysOfWeek.NONE,
    val label: String = "",
    val enabled: Boolean = true,
    /** Ringtone content URI; null = system default alarm sound. */
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val gradualVolume: Boolean = true,
    val snoozeMinutes: Int = DEFAULT_SNOOZE_MINUTES,
    val maxSnoozes: Int = DEFAULT_MAX_SNOOZES,
    /** How many times the current ringing cycle has been snoozed. */
    val snoozeCount: Int = 0,
    /** Epoch millis of a pending snoozed ring, or null. */
    val snoozedUntil: Long? = null,
) {
    init {
        require(hour in 0..23) { "hour out of range: $hour" }
        require(minute in 0..59) { "minute out of range: $minute" }
    }

    val isRepeating: Boolean get() = daysMask and DaysOfWeek.EVERY_DAY != 0

    fun repeatsOn(day: DayOfWeek): Boolean = daysMask and DaysOfWeek.bit(day) != 0

    val canSnooze: Boolean get() = snoozeCount < maxSnoozes

    fun isSnoozedAt(nowMillis: Long): Boolean = snoozedUntil != null && snoozedUntil > nowMillis

    /**
     * The alarm has just started ringing. A pending snooze is consumed; a one-time
     * alarm switches itself off so it can never ring "again tomorrow" if the
     * process dies before the user reacts.
     */
    fun fired(): Alarm = copy(
        snoozedUntil = null,
        enabled = if (isRepeating) enabled else false,
    )

    /** User (or auto-silence) snoozed the ringing alarm. Falls back to dismiss when no snoozes are left. */
    fun snoozed(nowMillis: Long): Alarm =
        if (!canSnooze) {
            dismissed()
        } else {
            copy(
                snoozeCount = snoozeCount + 1,
                snoozedUntil = nowMillis + snoozeMinutes * MILLIS_PER_MINUTE,
            )
        }

    /** Ringing cycle finished: forget snooze state. Repeating alarms stay enabled. */
    fun dismissed(): Alarm = copy(snoozeCount = 0, snoozedUntil = null)

    /** Alarm was edited or toggled by the user: any snooze in progress is cancelled. */
    fun edited(): Alarm = copy(snoozeCount = 0, snoozedUntil = null)

    companion object {
        const val DEFAULT_SNOOZE_MINUTES = 10
        const val DEFAULT_MAX_SNOOZES = 3
        val SNOOZE_MINUTES_RANGE = 1..30
        val MAX_SNOOZES_RANGE = 0..10
        const val MAX_LABEL_LENGTH = 40
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}

/** Helpers for the [Alarm.daysMask] bit set (bit 0 = Monday … bit 6 = Sunday). */
object DaysOfWeek {
    const val NONE = 0
    const val WEEKDAYS = 0b0011111
    const val WEEKEND = 0b1100000
    const val EVERY_DAY = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    fun days(mask: Int): List<DayOfWeek> = DayOfWeek.entries.filter { mask and bit(it) != 0 }

    /** The seven days starting from the locale's first day of week. */
    fun week(firstDay: DayOfWeek): List<DayOfWeek> = (0L..6L).map { firstDay.plus(it) }
}
