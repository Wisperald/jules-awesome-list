package com.personal.clock.util

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs suspend work from a receiver while keeping the broadcast alive
 * (the system allows ~10 s). The process stays alive until finish() is called.
 */
fun BroadcastReceiver.goAsync(block: suspend CoroutineScope.() -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeout(9_000) { block() }
        } catch (e: Exception) {
            // Never crash the process from a background broadcast.
            Log.e("ClockReceiver", "Broadcast work failed", e)
        } finally {
            pending.finish()
        }
    }
}
