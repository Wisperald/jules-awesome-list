package com.personal.clock.domain

enum class TimerStatus { IDLE, RUNNING, PAUSED, FINISHED }

/**
 * A countdown timer as an immutable state machine.
 *
 * While RUNNING the end moment is stored twice:
 *  - [endElapsed]  in `SystemClock.elapsedRealtime()` time — immune to wall-clock
 *    and time-zone changes, used for display and AlarmManager scheduling;
 *  - [endWallClock] in epoch millis — used only to recover after a reboot, when the
 *    elapsed-realtime clock restarts from zero.
 */
data class TimerItem(
    val id: Long,
    val label: String,
    val durationMillis: Long,
    val status: TimerStatus = TimerStatus.IDLE,
    /** Remaining time when not RUNNING. */
    val remainingMillis: Long = durationMillis,
    val endElapsed: Long = 0L,
    val endWallClock: Long = 0L,
) {
    init {
        require(durationMillis in MIN_DURATION..MAX_DURATION) { "duration out of range: $durationMillis" }
    }

    fun remaining(nowElapsed: Long): Long = when (status) {
        TimerStatus.RUNNING -> (endElapsed - nowElapsed).coerceAtLeast(0L)
        TimerStatus.FINISHED -> 0L
        TimerStatus.IDLE, TimerStatus.PAUSED -> remainingMillis
    }

    /** Fraction of the countdown already elapsed, 0f..1f. */
    fun progress(nowElapsed: Long): Float {
        val total = maxOf(durationMillis, remaining(nowElapsed))
        return (1f - remaining(nowElapsed).toFloat() / total).coerceIn(0f, 1f)
    }

    fun start(nowElapsed: Long, nowWall: Long): TimerItem = when (status) {
        TimerStatus.RUNNING -> this
        TimerStatus.FINISHED -> restart(nowElapsed, nowWall)
        TimerStatus.IDLE, TimerStatus.PAUSED -> {
            val left = if (remainingMillis > 0) remainingMillis else durationMillis
            copy(
                status = TimerStatus.RUNNING,
                remainingMillis = left,
                endElapsed = nowElapsed + left,
                endWallClock = nowWall + left,
            )
        }
    }

    fun pause(nowElapsed: Long): TimerItem =
        if (status != TimerStatus.RUNNING) {
            this
        } else {
            copy(
                status = TimerStatus.PAUSED,
                remainingMillis = remaining(nowElapsed),
                endElapsed = 0L,
                endWallClock = 0L,
            )
        }

    fun reset(): TimerItem = copy(
        status = TimerStatus.IDLE,
        remainingMillis = durationMillis,
        endElapsed = 0L,
        endWallClock = 0L,
    )

    fun restart(nowElapsed: Long, nowWall: Long): TimerItem = reset().start(nowElapsed, nowWall)

    fun finish(): TimerItem = copy(
        status = TimerStatus.FINISHED,
        remainingMillis = 0L,
        endElapsed = 0L,
        endWallClock = 0L,
    )

    /** Adds time; a finished timer starts again with just the added amount. */
    fun plus(extraMillis: Long, nowElapsed: Long, nowWall: Long): TimerItem = when (status) {
        TimerStatus.RUNNING -> copy(
            endElapsed = endElapsed + extraMillis,
            endWallClock = endWallClock + extraMillis,
        )
        TimerStatus.IDLE, TimerStatus.PAUSED ->
            copy(remainingMillis = (remainingMillis + extraMillis).coerceAtMost(MAX_DURATION))
        TimerStatus.FINISHED -> copy(
            status = TimerStatus.RUNNING,
            remainingMillis = extraMillis,
            endElapsed = nowElapsed + extraMillis,
            endWallClock = nowWall + extraMillis,
        )
    }

    /** True when a RUNNING timer's end moment has passed. */
    fun isDue(nowElapsed: Long): Boolean = status == TimerStatus.RUNNING && endElapsed <= nowElapsed

    /**
     * Re-anchors a RUNNING timer after a reboot using the wall clock
     * (elapsedRealtime restarted from zero). A timer that ran out while the phone was
     * off becomes due immediately, so AlarmManager fires it right away. Idempotent.
     */
    fun afterReboot(nowElapsed: Long, nowWall: Long): TimerItem {
        if (status != TimerStatus.RUNNING) return this
        val left = (endWallClock - nowWall).coerceAtLeast(0L)
        return copy(endElapsed = nowElapsed + left)
    }

    companion object {
        const val MIN_DURATION = 1_000L
        const val MAX_DURATION = 99L * 3_600_000L + 59L * 60_000L + 59_000L
        const val MAX_TIMERS = 20
        val QUICK_MINUTES = listOf(1, 5, 10, 15, 30, 45, 60)

        fun fromParts(hours: Int, minutes: Int, seconds: Int): Long =
            hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L
    }
}
