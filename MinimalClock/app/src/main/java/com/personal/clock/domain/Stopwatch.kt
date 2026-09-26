package com.personal.clock.domain

/**
 * Stopwatch state based on `SystemClock.elapsedRealtime()` so that wall-clock changes
 * never affect measured time. The UI only redraws while visible; no background work runs.
 */
data class StopwatchState(
    val running: Boolean = false,
    val startElapsed: Long = 0L,
    val accumulatedMillis: Long = 0L,
    /** Cumulative split time at each lap press. */
    val laps: List<Long> = emptyList(),
) {
    val isReset: Boolean get() = !running && accumulatedMillis == 0L && laps.isEmpty()

    fun elapsed(nowElapsed: Long): Long =
        accumulatedMillis + if (running) (nowElapsed - startElapsed).coerceAtLeast(0L) else 0L

    fun start(nowElapsed: Long): StopwatchState =
        if (running) this else copy(running = true, startElapsed = nowElapsed)

    fun pause(nowElapsed: Long): StopwatchState =
        if (!running) this else copy(running = false, accumulatedMillis = elapsed(nowElapsed), startElapsed = 0L)

    fun lap(nowElapsed: Long): StopwatchState =
        if (!running || laps.size >= MAX_LAPS) this else copy(laps = laps + elapsed(nowElapsed))

    fun reset(): StopwatchState = StopwatchState()

    /** Duration of each completed lap. */
    fun lapDurations(): List<Long> = laps.mapIndexed { i, split -> split - (if (i == 0) 0L else laps[i - 1]) }

    fun currentLap(nowElapsed: Long): Long = elapsed(nowElapsed) - (laps.lastOrNull() ?: 0L)

    /** Index of the fastest completed lap, only meaningful with 2+ laps. */
    fun fastestLapIndex(): Int? = lapDurations().takeIf { it.size >= 2 }?.let { d -> d.indices.minBy { d[it] } }

    fun slowestLapIndex(): Int? = lapDurations().takeIf { it.size >= 2 }?.let { d -> d.indices.maxBy { d[it] } }

    /**
     * A running stopwatch whose start lies in the "future" means the device rebooted
     * (elapsedRealtime restarted). Keep what was accumulated and stop.
     */
    fun sanitized(nowElapsed: Long): StopwatchState =
        if (running && startElapsed > nowElapsed) copy(running = false, startElapsed = 0L) else this

    fun toResult(finishedAtWall: Long, nowElapsed: Long): StopwatchResult? {
        val total = elapsed(nowElapsed)
        if (total <= 0L) return null
        return StopwatchResult(
            finishedAt = finishedAtWall,
            totalMillis = total,
            lapCount = laps.size,
            bestLapMillis = lapDurations().minOrNull(),
        )
    }

    companion object {
        const val MAX_LAPS = 999
    }
}

data class StopwatchResult(
    val finishedAt: Long,
    val totalMillis: Long,
    val lapCount: Int,
    val bestLapMillis: Long?,
)

object StopwatchHistory {
    const val MAX_RESULTS = 20

    fun add(history: List<StopwatchResult>, result: StopwatchResult): List<StopwatchResult> =
        (listOf(result) + history).take(MAX_RESULTS)
}
