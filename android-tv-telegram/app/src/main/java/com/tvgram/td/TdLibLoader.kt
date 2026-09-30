package com.tvgram.td

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loads `libtdjni.so` exactly once per process and silences TDLib's very chatty
 * default logging before any client is created.
 *
 * TDLib is not shipped through Maven for Android, so the shared object has to be
 * supplied by the build (see `docs/BUILDING_TDLIB.md`). Loading is therefore the
 * first thing that can realistically fail, and it fails loudly on purpose.
 */
object TdLibLoader {

    private const val TAG = "TdLibLoader"
    private const val LIBRARY = "tdjni"

    private val loaded = AtomicBoolean(false)

    @Volatile
    var loadError: Throwable? = null
        private set

    val isLoaded: Boolean get() = loaded.get()

    @Synchronized
    fun load(verbosityLevel: Int = 1): Boolean {
        if (loaded.get()) return true
        return try {
            System.loadLibrary(LIBRARY)
            // Must run before the first Client is created, otherwise TDLib spends the
            // first seconds of every launch writing debug output to logcat.
            Client.execute(TdApi.SetLogVerbosityLevel(verbosityLevel))
            loaded.set(true)
            loadError = null
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError when the .so is missing for this ABI,
            // NoClassDefFoundError when the Java bindings jar is missing.
            Log.e(TAG, "Unable to load TDLib native library '$LIBRARY'", t)
            loadError = t
            false
        }
    }
}
