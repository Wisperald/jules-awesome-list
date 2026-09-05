package com.tvgram.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvgram.data.files.FileRepository
import com.tvgram.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface MediaState {
    data class Downloading(val fraction: Float, val downloadedBytes: Long, val totalBytes: Long) : MediaState
    data class Ready(val localPath: String) : MediaState
    data class Failed(val reason: String) : MediaState
}

/**
 * Fetches one media file and reports progress.
 *
 * TDLib can stream a partially downloaded file, but only through a custom data source
 * that reads the growing local copy; this client keeps it simple and honest — it waits
 * for the file, and shows exactly how far along it is while doing so.
 */
class MediaViewModel(
    private val container: AppContainer,
    private val fileId: Int,
) : ViewModel() {

    private val _state = MutableStateFlow<MediaState>(MediaState.Downloading(0f, 0, 0))
    val state: StateFlow<MediaState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val ready = container.files.localPathIfReady(fileId)
            if (ready != null) {
                _state.value = MediaState.Ready(ready)
                return@launch
            }

            val started = container.files.startDownload(fileId, FileRepository.PRIORITY_MEDIA)
            if (started == null) {
                _state.value = MediaState.Failed("Telegram refused to download this file")
                return@launch
            }

            container.files.progressOf(fileId).collect { progress ->
                _state.value = when {
                    progress.isCompleted && progress.localPath != null ->
                        MediaState.Ready(progress.localPath)

                    else -> MediaState.Downloading(
                        fraction = progress.fraction,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = progress.totalBytes,
                    )
                }
            }
        }
    }

    /** Called when the viewer is dismissed mid-download; the partial file is kept. */
    fun cancelDownload() {
        container.files.cancel(fileId)
    }
}
