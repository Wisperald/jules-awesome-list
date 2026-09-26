package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun stopwatchFormat() {
        assertEquals("00:00.00", DurationFormat.stopwatch(0))
        assertEquals("01:02.34", DurationFormat.stopwatch(62_345))
        assertEquals("1:00:00.00", DurationFormat.stopwatch(3_600_000))
        assertEquals("00:00.00", DurationFormat.stopwatch(-5))
    }

    @Test
    fun countdownRoundsUp() {
        assertEquals("00:00", DurationFormat.countdown(0))
        assertEquals("00:01", DurationFormat.countdown(1))
        assertEquals("05:00", DurationFormat.countdown(300_000))
        assertEquals("04:59", DurationFormat.countdown(299_000))
        assertEquals("1:00:00", DurationFormat.countdown(3_600_000))
    }

    @Test
    fun splitDaysHoursMinutes() {
        assertEquals(Triple(0L, 7L, 5L), DurationFormat.splitDaysHoursMinutes(7 * 3_600_000L + 5 * 60_000L))
        assertEquals(Triple(0L, 0L, 1L), DurationFormat.splitDaysHoursMinutes(1_000))
        assertEquals(Triple(1L, 2L, 0L), DurationFormat.splitDaysHoursMinutes(26 * 3_600_000L))
    }

    @Test
    fun offsets() {
        assertEquals("UTC", DurationFormat.utcOffset(0))
        assertEquals("UTC+5", DurationFormat.utcOffset(5 * 3600))
        assertEquals("UTC−3", DurationFormat.utcOffset(-3 * 3600))
        assertEquals(DurationFormat.OffsetParts(-1, 2, 30), DurationFormat.offsetParts(-150))
    }
}
