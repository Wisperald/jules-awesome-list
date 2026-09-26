package com.personal.clock.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * Tiny JSON codecs built on org.json, which is part of the Android framework,
 * so persistence needs no serialization library. Decoders are tolerant: a
 * corrupt entry is skipped instead of losing the whole list.
 * Null fields are omitted rather than written as JSON null, because Android's
 * and the reference org.json implementations read JSON null differently.
 */

object AlarmCodec {

    fun encode(alarms: List<Alarm>): String = JSONArray().apply {
        alarms.forEach { a ->
            put(
                JSONObject()
                    .put("id", a.id)
                    .put("hour", a.hour)
                    .put("minute", a.minute)
                    .put("days", a.daysMask)
                    .put("label", a.label)
                    .put("enabled", a.enabled)
                    .put("vibrate", a.vibrate)
                    .put("gradual", a.gradualVolume)
                    .put("snoozeMinutes", a.snoozeMinutes)
                    .put("maxSnoozes", a.maxSnoozes)
                    .put("snoozeCount", a.snoozeCount)
                    .apply {
                        a.ringtoneUri?.let { put("ringtone", it) }
                        a.snoozedUntil?.let { put("snoozedUntil", it) }
                    },
            )
        }
    }.toString()

    fun decode(json: String?): List<Alarm> = decodeArray(json) { o ->
        Alarm(
            id = o.getLong("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            daysMask = o.optInt("days", 0) and DaysOfWeek.EVERY_DAY,
            label = o.optString("label", "").take(Alarm.MAX_LABEL_LENGTH),
            enabled = o.optBoolean("enabled", true),
            ringtoneUri = o.stringOrNull("ringtone"),
            vibrate = o.optBoolean("vibrate", true),
            gradualVolume = o.optBoolean("gradual", true),
            snoozeMinutes = o.optInt("snoozeMinutes", Alarm.DEFAULT_SNOOZE_MINUTES)
                .coerceIn(Alarm.SNOOZE_MINUTES_RANGE),
            maxSnoozes = o.optInt("maxSnoozes", Alarm.DEFAULT_MAX_SNOOZES).coerceIn(Alarm.MAX_SNOOZES_RANGE),
            snoozeCount = o.optInt("snoozeCount", 0).coerceAtLeast(0),
            snoozedUntil = if (o.has("snoozedUntil")) o.getLong("snoozedUntil") else null,
        )
    }
}

object TimerCodec {

    fun encode(timers: List<TimerItem>): String = JSONArray().apply {
        timers.forEach { t ->
            put(
                JSONObject()
                    .put("id", t.id)
                    .put("label", t.label)
                    .put("duration", t.durationMillis)
                    .put("status", t.status.name)
                    .put("remaining", t.remainingMillis)
                    .put("endElapsed", t.endElapsed)
                    .put("endWall", t.endWallClock),
            )
        }
    }.toString()

    fun decode(json: String?): List<TimerItem> = decodeArray(json) { o ->
        TimerItem(
            id = o.getLong("id"),
            label = o.optString("label", ""),
            durationMillis = o.getLong("duration"),
            status = TimerStatus.entries.firstOrNull { it.name == o.optString("status") } ?: TimerStatus.IDLE,
            remainingMillis = o.optLong("remaining", o.getLong("duration")),
            endElapsed = o.optLong("endElapsed", 0L),
            endWallClock = o.optLong("endWall", 0L),
        )
    }
}

object StopwatchCodec {

    fun encodeState(state: StopwatchState): String = JSONObject()
        .put("running", state.running)
        .put("start", state.startElapsed)
        .put("accumulated", state.accumulatedMillis)
        .put("laps", JSONArray().apply { state.laps.forEach { put(it) } })
        .toString()

    fun decodeState(json: String?): StopwatchState {
        if (json.isNullOrBlank()) return StopwatchState()
        return try {
            val o = JSONObject(json)
            val laps = o.optJSONArray("laps")
            StopwatchState(
                running = o.optBoolean("running", false),
                startElapsed = o.optLong("start", 0L),
                accumulatedMillis = o.optLong("accumulated", 0L).coerceAtLeast(0L),
                laps = if (laps == null) emptyList() else List(laps.length()) { laps.getLong(it) },
            )
        } catch (_: JSONException) {
            StopwatchState()
        }
    }

    fun encodeHistory(history: List<StopwatchResult>): String = JSONArray().apply {
        history.forEach { r ->
            put(
                JSONObject()
                    .put("at", r.finishedAt)
                    .put("total", r.totalMillis)
                    .put("laps", r.lapCount)
                    .apply { r.bestLapMillis?.let { put("best", it) } },
            )
        }
    }.toString()

    fun decodeHistory(json: String?): List<StopwatchResult> = decodeArray(json) { o ->
        StopwatchResult(
            finishedAt = o.getLong("at"),
            totalMillis = o.getLong("total"),
            lapCount = o.optInt("laps", 0),
            bestLapMillis = if (o.has("best")) o.getLong("best") else null,
        )
    }
}

private fun JSONObject.stringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

private inline fun <T> decodeArray(json: String?, parse: (JSONObject) -> T): List<T> {
    if (json.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(json)
    } catch (_: JSONException) {
        return emptyList()
    }
    val result = ArrayList<T>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        try {
            result += parse(item)
        } catch (_: JSONException) {
            // skip corrupt entry
        } catch (_: IllegalArgumentException) {
            // skip out-of-range entry
        }
    }
    return result
}
