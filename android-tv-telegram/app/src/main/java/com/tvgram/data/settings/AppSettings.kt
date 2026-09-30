package com.tvgram.data.settings

import android.content.Context
import android.content.SharedPreferences

/** The handful of preferences that are worth surviving a restart. */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tvgram", Context.MODE_PRIVATE)

    /** Re-opened automatically after a crash or a restart; 0 means "none". */
    var lastOpenedChatId: Long
        get() = prefs.getLong(KEY_LAST_CHAT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHAT, value).apply()

    /** Bumps every font size by 15%: TVs are watched from three metres away. */
    var largeText: Boolean
        get() = prefs.getBoolean(KEY_LARGE_TEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_LARGE_TEXT, value).apply()

    /** Send read receipts when a chat is opened. */
    var markAsRead: Boolean
        get() = prefs.getBoolean(KEY_MARK_READ, true)
        set(value) = prefs.edit().putBoolean(KEY_MARK_READ, value).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val KEY_LAST_CHAT = "last_chat_id"
        const val KEY_LARGE_TEXT = "large_text"
        const val KEY_MARK_READ = "mark_as_read"
    }
}
