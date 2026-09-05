package com.tvgram.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("HH:mm", Locale.getDefault()) }
private val dayFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMM", Locale.getDefault()) }
private val fullFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
private val dayHeaderFormat = ThreadLocal.withInitial { SimpleDateFormat("d MMMM yyyy", Locale.getDefault()) }

/** `21:43` today, `4 Mar` this year, `4 Mar 2023` before that — the usual messenger rule. */
fun formatMessageTimestamp(unixSeconds: Int): String {
    if (unixSeconds <= 0) return ""
    val date = Date(unixSeconds * 1000L)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }

    return when {
        now.sameDayAs(then) -> timeFormat.get()!!.format(date)
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> dayFormat.get()!!.format(date)
        else -> fullFormat.get()!!.format(date)
    }
}

fun formatClockTime(unixSeconds: Int): String =
    if (unixSeconds <= 0) "" else timeFormat.get()!!.format(Date(unixSeconds * 1000L))

fun formatDayHeader(unixSeconds: Int): String {
    val date = Date(unixSeconds * 1000L)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    return when {
        now.sameDayAs(then) -> "Today"
        yesterday.sameDayAs(then) -> "Yesterday"
        else -> dayHeaderFormat.get()!!.format(date)
    }
}

fun sameDay(firstUnixSeconds: Int, secondUnixSeconds: Int): Boolean {
    val a = Calendar.getInstance().apply { time = Date(firstUnixSeconds * 1000L) }
    val b = Calendar.getInstance().apply { time = Date(secondUnixSeconds * 1000L) }
    return a.sameDayAs(b)
}

/** `1:04` or `1:02:03` for anything an hour or longer. */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val hours = TimeUnit.SECONDS.toHours(seconds.toLong())
    val minutes = TimeUnit.SECONDS.toMinutes(seconds.toLong()) % 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) {
        "${value.toInt()} ${units[unit]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}

/** `1 234` — a badge with five digits in it is not information, it is noise. */
fun formatBadge(count: Int): String = when {
    count <= 0 -> ""
    count < 1000 -> count.toString()
    count < 1_000_000 -> "${count / 1000}K"
    else -> "${count / 1_000_000}M"
}

private fun Calendar.sameDayAs(other: Calendar): Boolean =
    get(Calendar.YEAR) == other.get(Calendar.YEAR) &&
        get(Calendar.DAY_OF_YEAR) == other.get(Calendar.DAY_OF_YEAR)
