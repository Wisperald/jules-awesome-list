package com.personal.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecsTest {

    @Test
    fun alarmRoundTrip() {
        val alarms = listOf(
            Alarm(1, 7, 0, DaysOfWeek.WEEKDAYS, "Работа", ringtoneUri = "content://media/internal/audio/media/12"),
            Alarm(2, 21, 30, label = "Лекарства \"вечер\"", enabled = false, vibrate = false,
                gradualVolume = false, snoozeMinutes = 5, maxSnoozes = 0, snoozeCount = 1, snoozedUntil = 123L),
        )
        assertEquals(alarms, AlarmCodec.decode(AlarmCodec.encode(alarms)))
    }

    @Test
    fun alarmDecodeIsTolerant() {
        assertEquals(emptyList<Alarm>(), AlarmCodec.decode(null))
        assertEquals(emptyList<Alarm>(), AlarmCodec.decode("not json"))
        val json = """[{"id":1,"hour":99,"minute":0},{"id":2,"hour":6,"minute":15,"snoozeMinutes":500}]"""
        val decoded = AlarmCodec.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(Alarm.SNOOZE_MINUTES_RANGE.last, decoded.single().snoozeMinutes)
    }

    @Test
    fun timerRoundTrip() {
        val timers = listOf(
            TimerItem(1, "Чай", 180_000),
            TimerItem(2, "", 60_000).start(10, 20),
            TimerItem(3, "Паста", 600_000).start(0, 0).pause(1_000),
            TimerItem(4, "x", 1_000).finish(),
        )
        assertEquals(timers, TimerCodec.decode(TimerCodec.encode(timers)))
    }

    @Test
    fun stopwatchRoundTrip() {
        val state = StopwatchState(running = true, startElapsed = 5, accumulatedMillis = 10, laps = listOf(1, 2, 3))
        assertEquals(state, StopwatchCodec.decodeState(StopwatchCodec.encodeState(state)))
        assertTrue(StopwatchCodec.decodeState("{broken").isReset)
        val history = listOf(StopwatchResult(1, 2, 3, 4), StopwatchResult(5, 6, 0, null))
        assertEquals(history, StopwatchCodec.decodeHistory(StopwatchCodec.encodeHistory(history)))
    }
}
