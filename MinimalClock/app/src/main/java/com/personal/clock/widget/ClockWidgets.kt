package com.personal.clock.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.personal.clock.appContainer
import com.personal.clock.util.goAsync

/** 2×2 widget: large time, date and weekday. */
class CompactClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        goAsync { context.appContainer.widgetUpdater.updateAll() }
    }
}

/** 4×2 resizable widget: time, date, next alarm and quick buttons. */
class ExtendedClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        goAsync { context.appContainer.widgetUpdater.updateAll() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Resized: switch between the full and the compact layout.
        goAsync { context.appContainer.widgetUpdater.updateAll() }
    }
}
