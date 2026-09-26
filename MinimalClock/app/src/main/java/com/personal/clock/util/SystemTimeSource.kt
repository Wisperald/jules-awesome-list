package com.personal.clock.util

import android.os.SystemClock
import java.time.ZoneId
import java.time.ZonedDateTime

/** Clock abstraction so controllers can be reasoned about (and replaced) easily. */
interface TimeSource {
    /** Monotonic time since boot, including deep sleep. */
    fun elapsed(): Long

    /** Wall-clock epoch millis. */
    fun wall(): Long

    fun zonedNow(): ZonedDateTime
}

object SystemTimeSource : TimeSource {
    override fun elapsed(): Long = SystemClock.elapsedRealtime()
    override fun wall(): Long = System.currentTimeMillis()
    override fun zonedNow(): ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
}
