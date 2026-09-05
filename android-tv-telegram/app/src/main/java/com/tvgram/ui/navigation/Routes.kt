package com.tvgram.ui.navigation

import android.net.Uri

/** Every destination in the app, in one place. */
object Routes {
    const val AUTH = "auth"
    const val CHATS = "chats"
    const val SETTINGS = "settings"

    const val CHAT = "chat/{chatId}"
    const val PHOTO = "photo/{fileId}"
    const val VIDEO = "video/{fileId}?title={title}"

    const val ARG_CHAT_ID = "chatId"
    const val ARG_FILE_ID = "fileId"
    const val ARG_TITLE = "title"

    fun chat(chatId: Long) = "chat/$chatId"

    fun photo(fileId: Int) = "photo/$fileId"

    fun video(fileId: Int, title: String) = "video/$fileId?title=${Uri.encode(title)}"
}
