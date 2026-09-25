package com.personal.clock.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.personal.clock.appContainer
import com.personal.clock.data.AppLanguage
import kotlinx.coroutines.runBlocking

/**
 * Per-app language.
 *  - Android 13+: the platform per-app language API ([LocaleManager]); the choice is
 *    also visible in system Settings → Apps → Language.
 *  - Android 8–12: the choice is stored in DataStore and applied by wrapping the
 *    Context of activities, services and receivers.
 */
object LocaleHelper {

    @Volatile
    private var cachedTag: String? = null

    fun apply(context: Context, language: AppLanguage) {
        cachedTag = language.tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language.tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language.tag)
        }
    }

    /** The language currently in effect for the app (SYSTEM when following the device). */
    fun current(context: Context): AppLanguage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) AppLanguage.SYSTEM else AppLanguage.fromTag(locales[0].language)
        } else {
            AppLanguage.fromTag(storedTag(context))
        }

    /** Returns a Context whose resources use the chosen language (no-op on Android 13+). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(config)
    }

    private fun storedTag(context: Context): String =
        cachedTag ?: runBlocking {
            // One small read, cached afterwards; DataStore is already warm in most cases.
            context.appContainer.settingsRepository.current().language.tag
        }.also { cachedTag = it }
}
