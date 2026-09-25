package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {

    @Test
    fun startPauseResumeAccumulates() {
        var s = StopwatchState().start(1_000)
        assertEquals(500L, s.elapsed(1_500))
        s = s.pause(2_000)
        assertEquals(1_000L, s.elapsed(99_999))
        s = s.start(10_000)
        assertEquals(1_250L, s.elapsed(10_250))
    }

    @Test
    fun lapsRecordSplitsAndDurations() {
        var s = StopwatchState().start(0)
        s = s.lap(1_000).lap(3_500).lap(4_000)
        assertEquals(listOf(1_000L, 3_500L, 4_000L), s.laps)
        assertEquals(listOf(1_000L, 2_500L, 500L), s.lapDurations())
        assertEquals(2, s.fastestLapIndex())
        assertEquals(1, s.slowestLapIndex())
        assertEquals(1_000L, s.currentLap(5_000))
    }

    @Test
    fun lapIgnoredWhilePaused() {
        val s = StopwatchState().start(0).pause(100)
        assertEquals(s, s.lap(200))
    }

    @Test
    fun fastestNeedsTwoLaps() {
        assertNull(StopwatchState().start(0).lap(10).fastestLapIndex())
    }

    @Test
    fun resetClearsEverything() {
        val s = StopwatchState().start(0).lap(10).reset()
        assertTrue(s.isReset)
        assertFalse(StopwatchState().start(0).isReset)
    }

    @Test
    fun sanitizedStopsAfterReboot() {
        val s = StopwatchState(running = true, startElapsed = 50_000, accumulatedMillis = 7_000)
        val fixed = s.sanitized(nowElapsed = 1_000)
        assertFalse(fixed.running)
        assertEquals(7_000L, fixed.elapsed(1_000))
        assertEquals(s, s.sanitized(60_000))
    }

    @Test
    fun resultAndHistory() {
        val s = StopwatchState().start(0).lap(1_000).lap(1_500)
        val r = s.toResult(finishedAtWall = 42, nowElapsed = 2_000)!!
        assertEquals(2_000L, r.totalMillis)
        assertEquals(2, r.lapCount)
        assertEquals(500L, r.bestLapMillis)
        assertNull(StopwatchState().toResult(1, 1))

        var history = emptyList<StopwatchResult>()
        repeat(StopwatchHistory.MAX_RESULTS + 5) { i -> history = StopwatchHistory.add(history, r.copy(finishedAt = i.toLong())) }
        assertEquals(StopwatchHistory.MAX_RESULTS, history.size)
        assertEquals((StopwatchHistory.MAX_RESULTS + 4).toLong(), history.first().finishedAt)
    }
}
