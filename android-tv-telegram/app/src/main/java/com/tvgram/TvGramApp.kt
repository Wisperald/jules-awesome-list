package com.tvgram

import android.app.Application
import com.tvgram.di.AppContainer

class TvGramApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}
