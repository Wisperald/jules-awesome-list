package com.tvgram.data.files

import com.tvgram.td.TdException
import com.tvgram.td.TelegramClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.drinkless.tdlib.TdApi

/** Progress of a single file download, as the media screens need it. */
data class DownloadProgress(
    val fileId: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val isCompleted: Boolean,
    val localPath: String?,
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (downloadedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

    companion object {
        fun of(file: TdApi.File): DownloadProgress = DownloadProgress(
            fileId = file.id,
            downloadedBytes = file.local?.downloadedSize ?: 0L,
            totalBytes = if (file.size > 0) file.size else file.expectedSize,
            isCompleted = file.local?.isDownloadingCompleted == true,
            localPath = file.local?.path?.takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Downloads from Telegram's distributed file storage.
 *
 * Small files (avatars, thumbnails) are fetched with TDLib's *synchronous* download,
 * which answers only once the bytes are on disk — no progress plumbing needed. Big
 * files (video, documents) are started asynchronously and followed through `UpdateFile`.
 */
class FileRepository(
    private val client: TelegramClient,
) {

    companion object {
        const val PRIORITY_THUMBNAIL = 32
        const val PRIORITY_MEDIA = 16
        const val PRIORITY_BACKGROUND = 1
    }

    /** Suspends until the file is on disk. Returns `null` if it cannot be downloaded. */
    suspend fun downloadNow(fileId: Int, priority: Int = PRIORITY_THUMBNAIL): String? {
        if (fileId == 0) return null
        localPathIfReady(fileId)?.let { return it }

        val request = TdApi.DownloadFile()
        request.fileId = fileId
        request.priority = priority
        request.offset = 0
        request.limit = 0
        request.synchronous = true
        return try {
            client.send(request).local?.path?.takeIf { it.isNotBlank() }
        } catch (e: TdException) {
            null
        } catch (e: IllegalStateException) {
            null
        }
    }

    /** Kicks off a background download; follow it with [progressOf]. */
    suspend fun startDownload(fileId: Int, priority: Int = PRIORITY_MEDIA): DownloadProgress? {
        if (fileId == 0) return null
        val request = TdApi.DownloadFile()
        request.fileId = fileId
        request.priority = priority
        request.offset = 0
        request.limit = 0
        request.synchronous = false
        return client.sendOrNull(request)?.let { DownloadProgress.of(it) }
    }

    fun cancel(fileId: Int) {
        if (fileId == 0) return
        val request = TdApi.CancelDownloadFile()
        request.fileId = fileId
        request.onlyIfPending = false
        client.sendAndForget(request)
    }

    /** Emits the current state first, then every `UpdateFile` for this file. */
    fun progressOf(fileId: Int): Flow<DownloadProgress> = client.updates
        .filterIsInstance<TdApi.UpdateFile>()
        .filter { it.file.id == fileId }
        .map { DownloadProgress.of(it.file) }
        .onStart { file(fileId)?.let { emit(DownloadProgress.of(it)) } }

    suspend fun file(fileId: Int): TdApi.File? {
        if (fileId == 0) return null
        return client.sendOrNull(TdApi.GetFile(fileId))
    }

    suspend fun localPathIfReady(fileId: Int): String? {
        val local = file(fileId)?.local ?: return null
        return local.path?.takeIf { local.isDownloadingCompleted && it.isNotBlank() }
    }
}
