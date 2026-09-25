package com.personal.clock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.personal.clock.MainActivity
import com.personal.clock.R
import com.personal.clock.data.AlarmRepository
import com.personal.clock.data.SettingsRepository
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.AlarmScheduleCalculator
import com.personal.clock.ui.AppTab
import com.personal.clock.util.LocaleHelper
import com.personal.clock.util.TimeText
import java.time.ZonedDateTime

/**
 * Builds widget RemoteViews. Time and date are rendered by [android.widget.TextClock]
 * inside the launcher process, which ticks by itself: the app is never woken up to
 * refresh the clock (`updatePeriodMillis = 0`). The app pushes an update only when
 * something it owns changes: alarms, settings, time zone / 12-24h preference, locale.
 */
class WidgetUpdater(
    private val appContext: Context,
    private val alarms: AlarmRepository,
    private val settings: SettingsRepository,
) {

    suspend fun updateAll() {
        val manager = AppWidgetManager.getInstance(appContext) ?: return
        val compactIds = manager.getAppWidgetIds(ComponentName(appContext, CompactClockWidget::class.java))
        val extendedIds = manager.getAppWidgetIds(ComponentName(appContext, ExtendedClockWidget::class.java))
        if (compactIds.isEmpty() && extendedIds.isEmpty()) return

        val context = LocaleHelper.wrap(appContext)
        val is24Hour = TimeText.is24Hour(context, settings.current().timeFormat)
        val next = AlarmScheduleCalculator.nextAlarm(alarms.all(), ZonedDateTime.now())

        compactIds.forEach { id -> manager.updateAppWidget(id, compact(context, is24Hour)) }
        extendedIds.forEach { id ->
            manager.updateAppWidget(id, extended(context, is24Hour, next, manager.getAppWidgetOptions(id)))
        }
    }

    private fun compact(context: Context, is24Hour: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_compact).apply {
            bindClock(context, is24Hour, dateSkeleton = "EEEdMMM")
            setOnClickPendingIntent(R.id.widget_root, openApp(AppTab.CLOCK))
        }

    private fun extended(
        context: Context,
        is24Hour: Boolean,
        next: Pair<Alarm, ZonedDateTime>?,
        options: Bundle?,
    ): RemoteViews {
        val minHeight = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val small = minHeight in 1 until SMALL_HEIGHT_DP
        val layout = if (small) R.layout.widget_extended_small else R.layout.widget_extended
        return RemoteViews(context.packageName, layout).apply {
            bindClock(context, is24Hour, dateSkeleton = "EEEEdMMMM")
            setTextViewText(R.id.widget_next_alarm, nextAlarmText(context, is24Hour, next))
            setOnClickPendingIntent(R.id.widget_root, openApp(AppTab.CLOCK))
            setOnClickPendingIntent(R.id.widget_next_alarm, openApp(AppTab.ALARMS))
            if (!small) {
                setTextViewText(R.id.widget_btn_alarm, context.getString(R.string.tab_alarms))
                setTextViewText(R.id.widget_btn_timer, context.getString(R.string.tab_timers))
                setTextViewText(R.id.widget_btn_stopwatch, context.getString(R.string.tab_stopwatch))
                setOnClickPendingIntent(R.id.widget_btn_alarm, openApp(AppTab.ALARMS))
                setOnClickPendingIntent(R.id.widget_btn_timer, openApp(AppTab.TIMERS))
                setOnClickPendingIntent(R.id.widget_btn_stopwatch, openApp(AppTab.STOPWATCH))
            }
        }
    }

    private fun RemoteViews.bindClock(context: Context, is24Hour: Boolean, dateSkeleton: String) {
        val locale = TimeText.locale(context)
        // Both formats are set explicitly so the widget follows the app's 12/24 preference.
        val timeFormat = if (is24Hour) "HH:mm" else "h:mm"
        setCharSequence(R.id.widget_time, "setFormat12Hour", timeFormat)
        setCharSequence(R.id.widget_time, "setFormat24Hour", timeFormat)
        setViewVisibility(R.id.widget_ampm, if (is24Hour) View.GONE else View.VISIBLE)
        setCharSequence(R.id.widget_ampm, "setFormat12Hour", "a")
        setCharSequence(R.id.widget_ampm, "setFormat24Hour", "a")
        val datePattern = android.text.format.DateFormat.getBestDateTimePattern(locale, dateSkeleton)
        setCharSequence(R.id.widget_date, "setFormat12Hour", datePattern)
        setCharSequence(R.id.widget_date, "setFormat24Hour", datePattern)
        setContentDescription(R.id.widget_root, context.getString(R.string.widget_open_app))
    }

    private fun nextAlarmText(context: Context, is24Hour: Boolean, next: Pair<Alarm, ZonedDateTime>?): String {
        if (next == null) return context.getString(R.string.widget_no_alarms)
        val (alarm, time) = next
        val locale = TimeText.locale(context)
        val day = TimeText.datePattern(locale, "EEE").format(time)
        val clock = TimeText.format(time, locale, is24Hour)
        val base = context.getString(R.string.widget_next_alarm, "$day $clock")
        return if (alarm.label.isBlank()) base else "$base · ${alarm.label}"
    }

    private fun openApp(tab: AppTab): PendingIntent = PendingIntent.getActivity(
        appContext,
        REQUEST_BASE + tab.ordinal,
        MainActivity.intent(appContext, tab),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val SMALL_HEIGHT_DP = 100
        const val REQUEST_BASE = 200
    }
}
