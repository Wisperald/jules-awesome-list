package com.personal.clock

import android.app.Application
import android.content.Context

class ClockApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.notifications.ensureChannels()
    }
}

/** Access to the manual DI container from any component (activities, services, receivers). */
val Context.appContainer: AppContainer
    get() = (applicationContext as ClockApplication).container
