package com.personal.clock.ui

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.personal.clock.R

enum class AppTab(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int) {
    CLOCK(R.string.tab_clock, R.drawable.ic_clock),
    ALARMS(R.string.tab_alarms, R.drawable.ic_alarm),
    STOPWATCH(R.string.tab_stopwatch, R.drawable.ic_stopwatch),
    TIMERS(R.string.tab_timers, R.drawable.ic_timer),
    ;

    companion object {
        const val EXTRA_TAB = "com.personal.clock.extra.TAB"

        /** Parses the requested tab; unknown values from outside are ignored. */
        fun fromIntent(intent: Intent?): AppTab? =
            intent?.getStringExtra(EXTRA_TAB)?.let { name -> entries.firstOrNull { it.name == name } }
    }
}
