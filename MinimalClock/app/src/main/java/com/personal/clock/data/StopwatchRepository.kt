package com.personal.clock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.clock.domain.StopwatchCodec
import com.personal.clock.domain.StopwatchHistory
import com.personal.clock.domain.StopwatchResult
import com.personal.clock.domain.StopwatchState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Stopwatch state and the last results. Stored in DataStore (a few hundred bytes):
 * a Room database would cost more APK size and startup time than it saves here.
 */
class StopwatchRepository(private val store: DataStore<Preferences>) {

    private val stateKey = stringPreferencesKey("stopwatch_state")
    private val historyKey = stringPreferencesKey("stopwatch_history")

    val state: Flow<StopwatchState> = store.data
        .map { StopwatchCodec.decodeState(it[stateKey]) }
        .distinctUntilChanged()

    val history: Flow<List<StopwatchResult>> = store.data
        .map { StopwatchCodec.decodeHistory(it[historyKey]) }
        .distinctUntilChanged()

    suspend fun save(state: StopwatchState) {
        store.edit { it[stateKey] = StopwatchCodec.encodeState(state) }
    }

    /** Resets the stopwatch, archiving the finished session in one transaction. */
    suspend fun resetAndArchive(result: StopwatchResult?) {
        store.edit { prefs ->
            if (result != null) {
                val history = StopwatchCodec.decodeHistory(prefs[historyKey])
                prefs[historyKey] = StopwatchCodec.encodeHistory(StopwatchHistory.add(history, result))
            }
            prefs[stateKey] = StopwatchCodec.encodeState(StopwatchState())
        }
    }

    suspend fun clearHistory() {
        store.edit { it.remove(historyKey) }
    }
}
