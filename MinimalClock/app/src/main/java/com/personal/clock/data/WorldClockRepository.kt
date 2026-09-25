package com.personal.clock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.clock.domain.CityCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Ordered list of city ids shown on the Clock tab. */
class WorldClockRepository(private val store: DataStore<Preferences>) {

    private val key = stringPreferencesKey("world_cities")

    val cityIds: Flow<List<String>> = store.data
        .map { prefs -> decode(prefs[key]) }
        .distinctUntilChanged()

    suspend fun add(id: String) = edit { if (id in it || CityCatalog.find(id) == null) it else it + id }

    suspend fun remove(id: String) = edit { it - id }

    suspend fun setOrder(ids: List<String>) = edit { current -> ids.filter { it in current } }

    private suspend fun edit(transform: (List<String>) -> List<String>) {
        store.edit { prefs -> prefs[key] = transform(decode(prefs[key])).joinToString(SEPARATOR) }
    }

    private fun decode(raw: String?): List<String> = when {
        raw == null -> CityCatalog.defaultIds // first launch
        raw.isEmpty() -> emptyList()
        else -> raw.split(SEPARATOR).filter { CityCatalog.find(it) != null }.distinct()
    }

    private companion object {
        const val SEPARATOR = ","
    }
}
