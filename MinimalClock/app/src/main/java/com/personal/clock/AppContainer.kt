package com.personal.clock

import android.content.Context
import com.personal.clock.alarm.AlarmController
import com.personal.clock.alarm.AlarmScheduler
import com.personal.clock.alarm.TimerController
import com.personal.clock.data.AlarmRepository
import com.personal.clock.data.ClockDataStore
import com.personal.clock.data.SettingsRepository
import com.personal.clock.data.StopwatchRepository
import com.personal.clock.data.TimerRepository
import com.personal.clock.data.WorldClockRepository
import com.personal.clock.notification.Notifications
import com.personal.clock.util.SystemTimeSource
import com.personal.clock.util.TimeText
import com.personal.clock.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. A DI framework (Hilt/Koin) would add build time and
 * APK size without real benefit for an app of this size.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Process-wide scope for work that must outlive a screen (e.g. receiver follow-ups). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore = ClockDataStore.create(appContext)

    val time = SystemTimeSource
    val settingsRepository = SettingsRepository(dataStore)
    val alarmRepository = AlarmRepository(dataStore)
    val worldClockRepository = WorldClockRepository(dataStore)
    val timerRepository = TimerRepository(dataStore)
    val stopwatchRepository = StopwatchRepository(dataStore)

    val notifications = Notifications(appContext)
    val widgetUpdater = WidgetUpdater(appContext, alarmRepository, settingsRepository)
    private val scheduler = AlarmScheduler(appContext)

    val alarmController = AlarmController(alarmRepository, scheduler, widgetUpdater, time)
    val timerController = TimerController(timerRepository, scheduler, notifications, time) {
        TimeText.is24Hour(appContext, settingsRepository.current().timeFormat)
    }

    val canScheduleExactAlarms: Boolean get() = scheduler.canScheduleExact()
}
