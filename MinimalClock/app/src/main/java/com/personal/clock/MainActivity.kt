package com.personal.clock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.clock.data.AppSettings
import com.personal.clock.ui.AppTab
import com.personal.clock.ui.ClockApp
import com.personal.clock.ui.theme.ClockTheme
import com.personal.clock.util.LocaleHelper

class MainActivity : ComponentActivity() {

    /** Tab requested by a widget / notification tap; consumed by the UI. */
    private val requestedTab = mutableStateOf<AppTab?>(null)

    @Volatile
    private var settingsLoaded = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until settings (theme) are read to avoid a light/dark flash.
        splash.setKeepOnScreenCondition { !settingsLoaded }
        enableEdgeToEdge()
        if (savedInstanceState == null) requestedTab.value = AppTab.fromIntent(intent)

        setContent {
            val settings: AppSettings? by appContainer.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            SideEffect { settingsLoaded = true }
            ClockTheme(theme = current.theme, dynamicColor = current.dynamicColor) {
                ClockApp(
                    settings = current,
                    requestedTab = requestedTab.value,
                    onRequestedTabHandled = { requestedTab.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppTab.fromIntent(intent)?.let { requestedTab.value = it }
    }

    companion object {
        fun intent(context: Context, tab: AppTab): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(AppTab.EXTRA_TAB, tab.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
