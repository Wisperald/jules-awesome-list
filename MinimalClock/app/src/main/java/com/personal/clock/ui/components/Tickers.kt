package com.personal.clock.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.Instant

/*
 * Screen tickers. They run only while the screen is visible (STARTED) and stop
 * completely in the background, so the UI never costs battery when not seen.
 */

/** Wall-clock time, updated exactly at each second boundary. */
@Composable
fun rememberWallClock(): State<Instant> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(Instant.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = Instant.now()
                delay(1_000 - System.currentTimeMillis() % 1_000)
            }
        }
    }
}

/** `elapsedRealtime()` refreshed every [periodMillis] while [active]; frozen otherwise. */
@Composable
fun rememberElapsedClock(active: Boolean, periodMillis: Long): State<Long> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(SystemClock.elapsedRealtime(), active, periodMillis, lifecycle) {
        value = SystemClock.elapsedRealtime()
        if (!active) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = SystemClock.elapsedRealtime()
                delay(periodMillis)
            }
        }
    }
}
