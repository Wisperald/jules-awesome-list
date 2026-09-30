package com.tvgram.di

import android.content.Context
import android.os.Build
import com.tvgram.BuildConfig
import com.tvgram.data.auth.AuthRepository
import com.tvgram.data.auth.AuthStage
import com.tvgram.data.chats.ChatRepository
import com.tvgram.data.files.FileRepository
import com.tvgram.data.messages.MessageRepository
import com.tvgram.data.settings.AppSettings
import com.tvgram.data.users.UserRepository
import com.tvgram.td.TdConfiguration
import com.tvgram.td.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * Hand-rolled dependency graph.
 *
 * The app has exactly one long-lived object graph and no build-time code generation is
 * worth its weight here: a container created by the `Application` and read through a
 * `ViewModelProvider.Factory` keeps the whole wiring visible in one screen of code.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val configuration = TdConfiguration(
        apiId = BuildConfig.TELEGRAM_API_ID,
        apiHash = BuildConfig.TELEGRAM_API_HASH,
        databaseDirectory = File(appContext.filesDir, "td/database").apply { mkdirs() }.absolutePath,
        filesDirectory = File(
            appContext.getExternalFilesDir(null) ?: appContext.filesDir,
            "td/files",
        ).apply { mkdirs() }.absolutePath,
        deviceModel = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { "Android TV" },
        systemVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        applicationVersion = BuildConfig.VERSION_NAME,
        systemLanguageCode = Locale.getDefault().language.ifBlank { "en" },
    )

    val settings = AppSettings(appContext)
    val client = TelegramClient(applicationScope)
    val users = UserRepository(client, applicationScope)
    val chats = ChatRepository(client, users, applicationScope)
    val files = FileRepository(client)
    val messages = MessageRepository(client, chats, users)
    val auth = AuthRepository(client, configuration, applicationScope)

    private val _largeText = MutableStateFlow(settings.largeText)

    /** Mirrors the stored preference so Compose can react to it. */
    val largeText: StateFlow<Boolean> = _largeText.asStateFlow()

    fun setLargeText(enabled: Boolean) {
        settings.largeText = enabled
        _largeText.value = enabled
    }

    private val _markAsRead = MutableStateFlow(settings.markAsRead)
    val markAsRead: StateFlow<Boolean> = _markAsRead.asStateFlow()

    fun setMarkAsRead(enabled: Boolean) {
        settings.markAsRead = enabled
        _markAsRead.value = enabled
    }

    fun start() {
        users.observe()
        chats.observe()

        applicationScope.launch {
            auth.stage.distinctUntilChanged().collect { stage ->
                when (stage) {
                    is AuthStage.Ready -> {
                        users.loadMe()
                        chats.loadMore()
                    }

                    is AuthStage.LoggingOut -> {
                        chats.reset()
                        settings.lastOpenedChatId = 0L
                    }

                    else -> Unit
                }
            }
        }

        auth.bootstrap()
    }
}
