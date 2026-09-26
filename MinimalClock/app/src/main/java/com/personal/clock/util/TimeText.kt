package com.personal.clock.util

import android.content.Context
import android.text.format.DateFormat
import com.personal.clock.data.TimeFormatMode
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/** Locale-aware time/date text honoring the app's 12/24-hour preference. */
object TimeText {

    fun is24Hour(context: Context, mode: TimeFormatMode): Boolean = when (mode) {
        TimeFormatMode.SYSTEM -> DateFormat.is24HourFormat(context)
        TimeFormatMode.H12 -> false
        TimeFormatMode.H24 -> true
    }

    fun locale(context: Context): Locale = context.resources.configuration.locales[0] ?: Locale.getDefault()

    /** Localized pattern, e.g. `HH:mm` (ru), `h:mm a` (en). */
    fun pattern(locale: Locale, is24Hour: Boolean, withSeconds: Boolean = false): String {
        val skeleton = when {
            is24Hour && withSeconds -> "Hms"
            is24Hour -> "Hm"
            withSeconds -> "hms"
            else -> "hm"
        }
        return DateFormat.getBestDateTimePattern(locale, skeleton)
    }

    fun format(time: TemporalAccessor, locale: Locale, is24Hour: Boolean, withSeconds: Boolean = false): String =
        DateTimeFormatter.ofPattern(pattern(locale, is24Hour, withSeconds), locale).format(time)

    fun format(hour: Int, minute: Int, locale: Locale, is24Hour: Boolean): String =
        format(LocalTime.of(hour, minute), locale, is24Hour)

    /** Localized date pattern from a skeleton such as `EEEEdMMMM` or `EEEdMMM`. */
    fun datePattern(locale: Locale, skeleton: String): DateTimeFormatter =
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}
