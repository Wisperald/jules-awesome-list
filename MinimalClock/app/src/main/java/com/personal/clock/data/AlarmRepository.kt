package com.personal.clock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.AlarmCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AlarmRepository(private val store: DataStore<Preferences>) {

    private val key = stringPreferencesKey("alarms")

    /** All alarms ordered by time of day. */
    val alarms: Flow<List<Alarm>> = store.data
        .map { prefs -> AlarmCodec.decode(prefs[key]).sortedWith(compareBy({ it.hour }, { it.minute }, { it.id })) }
        .distinctUntilChanged()

    suspend fun all(): List<Alarm> = alarms.first()

    suspend fun get(id: Long): Alarm? = all().firstOrNull { it.id == id }

    /** Inserts (id <= 0) or replaces an alarm and returns the stored value. */
    suspend fun upsert(alarm: Alarm): Alarm {
        var stored = alarm
        store.edit { prefs ->
            val list = AlarmCodec.decode(prefs[key])
            stored = if (alarm.id <= 0) alarm.copy(id = (list.maxOfOrNull { it.id } ?: 0L) + 1) else alarm
            val updated = list.filterNot { it.id == stored.id } + stored
            prefs[key] = AlarmCodec.encode(updated)
        }
        return stored
    }

    /** Atomically transforms one alarm; returns the new value or null if it does not exist. */
    suspend fun update(id: Long, transform: (Alarm) -> Alarm): Alarm? {
        var result: Alarm? = null
        store.edit { prefs ->
            val list = AlarmCodec.decode(prefs[key])
            val updated = list.map { if (it.id == id) transform(it).also { new -> result = new } else it }
            if (result != null) prefs[key] = AlarmCodec.encode(updated)
        }
        return result
    }

    suspend fun delete(id: Long) {
        store.edit { prefs ->
            prefs[key] = AlarmCodec.encode(AlarmCodec.decode(prefs[key]).filterNot { it.id == id })
        }
    }
}
