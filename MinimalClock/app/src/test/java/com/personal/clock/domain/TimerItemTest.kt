package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerItemTest {

    private val fiveMin = 5 * 60_000L
    private fun timer() = TimerItem(id = 1, label = "Tea", durationMillis = fiveMin)

    @Test
    fun startCountsDownOnElapsedClock() {
        val t = timer().start(nowElapsed = 1_000, nowWall = 50_000)
        assertEquals(TimerStatus.RUNNING, t.status)
        assertEquals(1_000 + fiveMin, t.endElapsed)
        assertEquals(50_000 + fiveMin, t.endWallClock)
        assertEquals(fiveMin - 60_000, t.remaining(61_000))
        assertEquals(0L, t.remaining(1_000 + fiveMin + 5_000))
    }

    @Test
    fun pauseAndResumeKeepRemainingTime() {
        val paused = timer().start(0, 0).pause(nowElapsed = 120_000)
        assertEquals(TimerStatus.PAUSED, paused.status)
        assertEquals(fiveMin - 120_000, paused.remaining(999_999))
        val resumed = paused.start(nowElapsed = 500_000, nowWall = 0)
        assertEquals(500_000 + fiveMin - 120_000, resumed.endElapsed)
    }

    @Test
    fun resetRestoresFullDuration() {
        val reset = timer().start(0, 0).pause(10_000).reset()
        assertEquals(TimerStatus.IDLE, reset.status)
        assertEquals(fiveMin, reset.remaining(0))
    }

    @Test
    fun finishedTimerCanBeRestarted() {
        val finished = timer().start(0, 0).finish()
        assertEquals(0L, finished.remaining(0))
        val again = finished.start(nowElapsed = 10_000, nowWall = 20_000)
        assertEquals(TimerStatus.RUNNING, again.status)
        assertEquals(10_000 + fiveMin, again.endElapsed)
    }

    @Test
    fun plusExtendsRunningAndRevivesFinished() {
        val running = timer().start(0, 0).plus(60_000, 0, 0)
        assertEquals(fiveMin + 60_000, running.endElapsed)
        val revived = timer().finish().plus(60_000, nowElapsed = 100, nowWall = 200)
        assertEquals(TimerStatus.RUNNING, revived.status)
        assertEquals(60_100L, revived.endElapsed)
    }

    @Test
    fun isDueOnlyWhenRunningAndPastEnd() {
        val t = timer().start(0, 0)
        assertFalse(t.isDue(fiveMin - 1))
        assertTrue(t.isDue(fiveMin))
        assertFalse(t.pause(10).isDue(Long.MAX_VALUE))
    }

    @Test
    fun rebootReanchorsFromWallClock() {
        val t = timer().start(nowElapsed = 900_000, nowWall = 1_000_000)
        // Device rebooted: elapsed restarted at 0; two minutes of wall time passed.
        val restored = t.afterReboot(nowElapsed = 5_000, nowWall = 1_120_000)
        assertEquals(5_000 + fiveMin - 120_000, restored.endElapsed)
        assertEquals(fiveMin - 120_000, restored.remaining(5_000))
        // Idempotent.
        assertEquals(restored, restored.afterReboot(5_000, 1_120_000))
    }

    @Test
    fun timerThatExpiredWhileOffIsDueRightAfterReboot() {
        val t = timer().start(nowElapsed = 0, nowWall = 0)
        val restored = t.afterReboot(nowElapsed = 7_000, nowWall = fiveMin + 60_000)
        assertEquals(TimerStatus.RUNNING, restored.status)
        assertTrue(restored.isDue(7_000))
        assertEquals(0L, restored.remaining(7_000))
    }

    @Test
    fun progressGoesFromZeroToOne() {
        val t = timer().start(0, 0)
        assertEquals(0f, t.progress(0), 0.0001f)
        assertEquals(0.5f, t.progress(fiveMin / 2), 0.0001f)
        assertEquals(1f, t.progress(fiveMin * 2), 0.0001f)
    }

    @Test
    fun quickPresetsAndParts() {
        assertEquals(listOf(1, 5, 10, 15, 30, 45, 60), TimerItem.QUICK_MINUTES)
        assertEquals(3_723_000L, TimerItem.fromParts(1, 2, 3))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroDurationIsRejected() {
        TimerItem(1, "", 0L)
    }
}
