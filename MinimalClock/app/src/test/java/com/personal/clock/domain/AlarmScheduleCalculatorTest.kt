package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmScheduleCalculatorTest {

    private val almaty = ZoneId.of("Asia/Almaty")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(y, mo, d, h, mi), zone)

    @Test
    fun oneTimeAlarmLaterTodayRingsToday() {
        val now = at(almaty, 2026, 9, 24, 6, 0) // Thursday
        val next = AlarmScheduleCalculator.nextOccurrence(Alarm(1, 7, 30), now)
        assertEquals(at(almaty, 2026, 9, 24, 7, 30), next)
    }

    @Test
    fun oneTimeAlarmEarlierTodayRingsTomorrow() {
        val now = at(almaty, 2026, 9, 24, 8, 0)
        val next = AlarmScheduleCalculator.nextOccurrence(Alarm(1, 7, 30), now)
        assertEquals(at(almaty, 2026, 9, 25, 7, 30), next)
    }

    @Test
    fun alarmAtExactlyNowIsScheduledForTomorrow() {
        val now = at(almaty, 2026, 9, 24, 7, 30)
        val next = AlarmScheduleCalculator.nextOccurrence(Alarm(1, 7, 30), now)
        assertEquals(at(almaty, 2026, 9, 25, 7, 30), next)
    }

    @Test
    fun weekdayAlarmOnFridayEveningSkipsWeekend() {
        val now = at(almaty, 2026, 9, 25, 20, 0) // Friday
        val alarm = Alarm(1, 7, 0, daysMask = DaysOfWeek.WEEKDAYS)
        val next = AlarmScheduleCalculator.nextOccurrence(alarm, now)
        assertEquals(DayOfWeek.MONDAY, next.dayOfWeek)
        assertEquals(at(almaty, 2026, 9, 28, 7, 0), next)
    }

    @Test
    fun weeklyAlarmForTodayThatAlreadyPassedRingsNextWeek() {
        val now = at(almaty, 2026, 9, 24, 9, 0) // Thursday
        val alarm = Alarm(1, 8, 0, daysMask = DaysOfWeek.bit(DayOfWeek.THURSDAY))
        assertEquals(at(almaty, 2026, 10, 1, 8, 0), AlarmScheduleCalculator.nextOccurrence(alarm, now))
    }

    @Test
    fun springForwardGapShiftsAlarmToValidTime() {
        // Europe/Berlin: 2026-03-29 02:00 -> 03:00.
        val now = at(berlin, 2026, 3, 29, 0, 30)
        val next = AlarmScheduleCalculator.nextOccurrence(Alarm(1, 2, 30), now)
        assertEquals(29, next.dayOfMonth)
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
        assertEquals(Duration.ofHours(2), Duration.between(now, next))
    }

    @Test
    fun fallBackOverlapRingsOnlyOnce() {
        // Europe/Berlin: 2026-10-25 03:00 -> 02:00, so 02:30 happens twice.
        val beforeOverlap = at(berlin, 2026, 10, 25, 1, 0)
        val first = AlarmScheduleCalculator.nextOccurrence(Alarm(1, 2, 30), beforeOverlap)
        assertEquals(2, first.hour)
        // Just after the first 02:30 (still summer offset) the next ring must be tomorrow,
        // not the repeated 02:30 one hour later.
        val justAfter = first.plusMinutes(1)
        val second = AlarmScheduleCalculator.nextOccurrence(Alarm(1, 2, 30), justAfter)
        assertEquals(26, second.dayOfMonth)
    }

    @Test
    fun repeatingAlarmKeepsLocalTimeAcrossDst() {
        // New York switches to DST on 2026-03-08. A daily 07:00 alarm stays at 07:00 local.
        val saturday = at(newYork, 2026, 3, 7, 8, 0)
        val alarm = Alarm(1, 7, 0, daysMask = DaysOfWeek.EVERY_DAY)
        val next = AlarmScheduleCalculator.nextOccurrence(alarm, saturday)
        assertEquals(7, next.hour)
        assertEquals(8, next.dayOfMonth)
        assertEquals(Duration.ofHours(22), Duration.between(saturday, next))
    }

    @Test
    fun timeZoneChangeKeepsLocalWallTime() {
        val alarm = Alarm(1, 7, 0)
        val instant = at(almaty, 2026, 9, 24, 5, 0).toInstant()
        val inAlmaty = AlarmScheduleCalculator.nextOccurrence(alarm, instant.atZone(almaty))
        val inBerlin = AlarmScheduleCalculator.nextOccurrence(alarm, instant.atZone(berlin))
        assertEquals(7, inAlmaty.hour)
        assertEquals(7, inBerlin.hour)
        assertFalse(inAlmaty.toInstant() == inBerlin.toInstant())
    }

    @Test
    fun disabledAlarmHasNoTrigger() {
        val now = at(almaty, 2026, 9, 24, 6, 0)
        assertNull(AlarmScheduleCalculator.nextTrigger(Alarm(1, 7, 0, enabled = false), now))
    }

    @Test
    fun pendingSnoozeWinsEvenForDisabledOneTimeAlarm() {
        val now = at(almaty, 2026, 9, 24, 7, 0)
        val nowMs = now.toInstant().toEpochMilli()
        val alarm = Alarm(1, 7, 0).fired().snoozed(nowMs)
        assertFalse(alarm.enabled)
        val trigger = AlarmScheduleCalculator.nextTrigger(alarm, now)
        assertEquals(now.plusMinutes(10), trigger)
    }

    @Test
    fun expiredSnoozeIsIgnored() {
        val now = at(almaty, 2026, 9, 24, 12, 0)
        val alarm = Alarm(1, 7, 0, daysMask = DaysOfWeek.EVERY_DAY, snoozedUntil = now.minusHours(1).toInstant().toEpochMilli())
        assertEquals(at(almaty, 2026, 9, 25, 7, 0), AlarmScheduleCalculator.nextTrigger(alarm, now))
    }

    @Test
    fun nextAlarmPicksEarliest() {
        val now = at(almaty, 2026, 9, 24, 6, 0)
        val alarms = listOf(Alarm(1, 9, 0), Alarm(2, 6, 30), Alarm(3, 5, 0), Alarm(4, 6, 10, enabled = false))
        val (alarm, time) = AlarmScheduleCalculator.nextAlarm(alarms, now)!!
        assertEquals(2L, alarm.id)
        assertEquals(at(almaty, 2026, 9, 24, 6, 30), time)
    }

    @Test
    fun nextAlarmIsNullWhenNothingEnabled() {
        val now = at(almaty, 2026, 9, 24, 6, 0)
        assertNull(AlarmScheduleCalculator.nextAlarm(listOf(Alarm(1, 7, 0, enabled = false)), now))
    }

    @Test
    fun timeUntilIsNeverNegative() {
        val now = at(almaty, 2026, 9, 24, 6, 0)
        assertEquals(Duration.ZERO, AlarmScheduleCalculator.timeUntil(now.minusMinutes(5), now))
        assertTrue(AlarmScheduleCalculator.timeUntil(now.plusMinutes(5), now) == Duration.ofMinutes(5))
    }
}
