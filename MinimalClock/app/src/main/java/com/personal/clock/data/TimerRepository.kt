package com.personal.clock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.clock.domain.TimerCodec
import com.personal.clock.domain.TimerItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TimerRepository(private val store: DataStore<Preferences>) {

    private val key = stringPreferencesKey("timers")

    /** Timers in creation order. */
    val timers: Flow<List<TimerItem>> = store.data
        .map { prefs -> TimerCodec.decode(prefs[key]).sortedBy { it.id } }
        .distinctUntilChanged()

    suspend fun all(): List<TimerItem> = timers.first()

    suspend fun get(id: Long): TimerItem? = all().firstOrNull { it.id == id }

    /** Adds a timer, assigning a fresh id. Returns null when the limit is reached. */
    suspend fun add(create: (id: Long) -> TimerItem): TimerItem? {
        var created: TimerItem? = null
        store.edit { prefs ->
            val list = TimerCodec.decode(prefs[key])
            if (list.size < TimerItem.MAX_TIMERS) {
                val item = create((list.maxOfOrNull { it.id } ?: 0L) + 1)
                created = item
                prefs[key] = TimerCodec.encode(list + item)
            }
        }
        return created
    }

    suspend fun update(id: Long, transform: (TimerItem) -> TimerItem): TimerItem? {
        var result: TimerItem? = null
        store.edit { prefs ->
            val list = TimerCodec.decode(prefs[key])
            val updated = list.map { if (it.id == id) transform(it).also { new -> result = new } else it }
            if (result != null) prefs[key] = TimerCodec.encode(updated)
        }
        return result
    }

    /** Transforms every timer at once (used after reboot). */
    suspend fun updateAll(transform: (TimerItem) -> TimerItem): List<TimerItem> {
        var result = emptyList<TimerItem>()
        store.edit { prefs ->
            result = TimerCodec.decode(prefs[key]).map(transform)
            prefs[key] = TimerCodec.encode(result)
        }
        return result
    }

    suspend fun delete(id: Long) {
        store.edit { prefs ->
            prefs[key] = TimerCodec.encode(TimerCodec.decode(prefs[key]).filterNot { it.id == id })
        }
    }
}
