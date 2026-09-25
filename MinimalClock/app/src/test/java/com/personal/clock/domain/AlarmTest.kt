package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class AlarmTest {

    @Test
    fun firedOneTimeAlarmDisablesItself() {
        val fired = Alarm(1, 7, 0).fired()
        assertFalse(fired.enabled)
    }

    @Test
    fun firedRepeatingAlarmStaysEnabled() {
        val fired = Alarm(1, 7, 0, daysMask = DaysOfWeek.WEEKDAYS).fired()
        assertTrue(fired.enabled)
    }

    @Test
    fun snoozeCountsAndStopsAtLimit() {
        var alarm = Alarm(1, 7, 0, snoozeMinutes = 5, maxSnoozes = 2)
        alarm = alarm.snoozed(0L)
        assertEquals(1, alarm.snoozeCount)
        assertEquals(5 * 60_000L, alarm.snoozedUntil)
        alarm = alarm.snoozed(1_000L)
        assertEquals(2, alarm.snoozeCount)
        assertFalse(alarm.canSnooze)
        // Third snooze is not allowed: behaves like dismiss.
        alarm = alarm.snoozed(2_000L)
        assertEquals(0, alarm.snoozeCount)
        assertNull(alarm.snoozedUntil)
    }

    @Test
    fun zeroMaxSnoozesMeansNoSnooze() {
        assertFalse(Alarm(1, 7, 0, maxSnoozes = 0).canSnooze)
    }

    @Test
    fun dismissClearsSnooze() {
        val alarm = Alarm(1, 7, 0).snoozed(0L).dismissed()
        assertEquals(0, alarm.snoozeCount)
        assertNull(alarm.snoozedUntil)
        assertFalse(alarm.isSnoozedAt(0L))
    }

    @Test
    fun daysMaskHelpers() {
        assertTrue(Alarm(1, 7, 0, daysMask = DaysOfWeek.WEEKDAYS).repeatsOn(DayOfWeek.FRIDAY))
        assertFalse(Alarm(1, 7, 0, daysMask = DaysOfWeek.WEEKDAYS).repeatsOn(DayOfWeek.SUNDAY))
        assertEquals(DaysOfWeek.EVERY_DAY, DaysOfWeek.WEEKDAYS or DaysOfWeek.WEEKEND)
        val mask = DaysOfWeek.toggle(DaysOfWeek.NONE, DayOfWeek.WEDNESDAY)
        assertEquals(listOf(DayOfWeek.WEDNESDAY), DaysOfWeek.days(mask))
        assertEquals(DaysOfWeek.NONE, DaysOfWeek.toggle(mask, DayOfWeek.WEDNESDAY))
        assertEquals(DayOfWeek.SUNDAY, DaysOfWeek.week(DayOfWeek.SUNDAY).first())
        assertEquals(DayOfWeek.SATURDAY, DaysOfWeek.week(DayOfWeek.SUNDAY).last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHourIsRejected() {
        Alarm(1, 24, 0)
    }
}
