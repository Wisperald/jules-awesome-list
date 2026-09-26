package com.personal.clock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File

/**
 * Single Preferences DataStore for the whole app.
 *
 * It lives in *device-protected* storage so alarms and timers can be restored and
 * ring after a reboot even before the user unlocks the phone (Direct Boot). The data
 * is non-sensitive (alarm times, labels, settings).
 */
object ClockDataStore {

    private const val FILE_NAME = "clock.preferences_pb"

    fun create(context: Context): DataStore<Preferences> {
        val deviceProtected = context.createDeviceProtectedStorageContext()
        return PreferenceDataStoreFactory.create(
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            // Built manually: Context.preferencesDataStoreFile() would resolve against the
            // credential-protected applicationContext.
            produceFile = { File(deviceProtected.filesDir, "datastore/$FILE_NAME") },
        )
    }
}
