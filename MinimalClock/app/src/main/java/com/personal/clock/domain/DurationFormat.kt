package com.personal.clock.domain

import java.util.Locale
import kotlin.math.abs

/** Locale-independent numeric formatting of durations (digits are always ASCII). */
object DurationFormat {

    /** Stopwatch style: `mm:ss.cc` or `h:mm:ss.cc` once an hour has passed. */
    fun stopwatch(millis: Long): String {
        val ms = millis.coerceAtLeast(0)
        val hours = ms / 3_600_000
        val minutes = ms / 60_000 % 60
        val seconds = ms / 1_000 % 60
        val centis = ms / 10 % 100
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d.%02d", hours, minutes, seconds, centis)
        } else {
            String.format(Locale.ROOT, "%02d:%02d.%02d", minutes, seconds, centis)
        }
    }

    /**
     * Countdown style: `mm:ss` or `h:mm:ss`. Rounds *up* to whole seconds so a timer
     * never shows 00:00 while it is still running.
     */
    fun countdown(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1_000
        val hours = totalSeconds / 3_600
        val minutes = totalSeconds / 60 % 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    /** Splits a positive duration into whole days/hours/minutes (seconds are rounded up to a minute). */
    fun splitDaysHoursMinutes(millis: Long): Triple<Long, Long, Long> {
        val totalMinutes = (millis.coerceAtLeast(0) + 59_999) / 60_000
        return Triple(totalMinutes / 1_440, totalMinutes / 60 % 24, totalMinutes % 60)
    }

    /** Sign and absolute hour/minute parts of an offset in minutes. */
    data class OffsetParts(val sign: Int, val hours: Int, val minutes: Int)

    fun offsetParts(offsetMinutes: Int): OffsetParts =
        OffsetParts(
            sign = offsetMinutes.compareTo(0),
            hours = abs(offsetMinutes) / 60,
            minutes = abs(offsetMinutes) % 60,
        )

    /** `UTC+5`, `UTC+5:30`, `UTC−3`, `UTC` (uses the Unicode minus sign). */
    fun utcOffset(totalSeconds: Int): String {
        if (totalSeconds == 0) return "UTC"
        val parts = offsetParts(totalSeconds / 60)
        val sign = if (parts.sign < 0) "−" else "+"
        return if (parts.minutes == 0) {
            "UTC$sign${parts.hours}"
        } else {
            String.format(Locale.ROOT, "UTC%s%d:%02d", sign, parts.hours, parts.minutes)
        }
    }
}
