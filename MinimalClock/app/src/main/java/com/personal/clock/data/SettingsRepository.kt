package com.personal.clock.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.personal.clock.domain.Alarm
import com.personal.clock.domain.CitySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class TimeFormatMode { SYSTEM, H12, H24 }

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    RUSSIAN("ru"),
    KAZAKH("kk"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag.isNotEmpty() && it.tag == tag?.substringBefore('-') } ?: SYSTEM
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val timeFormat: TimeFormatMode = TimeFormatMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val showSeconds: Boolean = true,
    val citySort: CitySort = CitySort.MANUAL,
    val defaultSnoozeMinutes: Int = Alarm.DEFAULT_SNOOZE_MINUTES,
    /** Sound for finished timers; null = system default alarm sound. */
    val timerRingtone: String? = null,
    val timerVibrate: Boolean = true,
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val TIME_FORMAT = stringPreferencesKey("time_format")
        val LANGUAGE = stringPreferencesKey("language")
        val SHOW_SECONDS = booleanPreferencesKey("show_seconds")
        val CITY_SORT = stringPreferencesKey("city_sort")
        val DEFAULT_SNOOZE = intPreferencesKey("default_snooze")
        val TIMER_RINGTONE = stringPreferencesKey("timer_ringtone")
        val TIMER_VIBRATE = booleanPreferencesKey("timer_vibrate")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            theme = enumOr(p[Keys.THEME], ThemeMode.SYSTEM),
            dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
            timeFormat = enumOr(p[Keys.TIME_FORMAT], TimeFormatMode.SYSTEM),
            language = AppLanguage.fromTag(p[Keys.LANGUAGE]),
            showSeconds = p[Keys.SHOW_SECONDS] ?: true,
            citySort = enumOr(p[Keys.CITY_SORT], CitySort.MANUAL),
            defaultSnoozeMinutes = (p[Keys.DEFAULT_SNOOZE] ?: Alarm.DEFAULT_SNOOZE_MINUTES)
                .coerceIn(Alarm.SNOOZE_MINUTES_RANGE),
            timerRingtone = p[Keys.TIMER_RINGTONE],
            timerVibrate = p[Keys.TIMER_VIBRATE] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(value: ThemeMode) = store.edit { it[Keys.THEME] = value.name }
    suspend fun setDynamicColor(value: Boolean) = store.edit { it[Keys.DYNAMIC_COLOR] = value }
    suspend fun setTimeFormat(value: TimeFormatMode) = store.edit { it[Keys.TIME_FORMAT] = value.name }
    suspend fun setLanguage(value: AppLanguage) = store.edit { it[Keys.LANGUAGE] = value.tag }
    suspend fun setShowSeconds(value: Boolean) = store.edit { it[Keys.SHOW_SECONDS] = value }
    suspend fun setCitySort(value: CitySort) = store.edit { it[Keys.CITY_SORT] = value.name }
    suspend fun setDefaultSnooze(minutes: Int) =
        store.edit { it[Keys.DEFAULT_SNOOZE] = minutes.coerceIn(Alarm.SNOOZE_MINUTES_RANGE) }

    suspend fun setTimerRingtone(uri: String?) = store.edit {
        if (uri == null) it.remove(Keys.TIMER_RINGTONE) else it[Keys.TIMER_RINGTONE] = uri
    }

    suspend fun setTimerVibrate(value: Boolean) = store.edit { it[Keys.TIMER_VIBRATE] = value }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
