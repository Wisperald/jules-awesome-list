package com.tvgram.ui.media

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.di.AppContainer
import com.tvgram.di.containerViewModel
import com.tvgram.ui.components.ErrorState
import com.tvgram.ui.components.TvProgressBar
import com.tvgram.util.formatFileSize
import java.io.File

/**
 * Full-screen playback for video, audio and voice messages.
 *
 * ExoPlayer's own `PlayerView` is used rather than a hand-rolled surface: it already
 * handles D-pad transport controls, subtitles and audio focus, which is most of what a
 * TV player has to get right.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun VideoPlayerScreen(
    fileId: Int,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MediaViewModel = containerViewModel(key = "media-$fileId") { container: AppContainer ->
        MediaViewModel(container, fileId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            is MediaState.Downloading -> DownloadingIndicator(title, current)

            is MediaState.Failed -> ErrorState(
                title = "Cannot play this file",
                detail = current.reason,
                actionLabel = "Back",
                onAction = onBack,
            )

            is MediaState.Ready -> Player(localPath = current.localPath)
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun Player(localPath: String) {
    val context = LocalContext.current

    val player = remember(localPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(localPath))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
    )
}

@Composable
private fun DownloadingIndicator(title: String, state: MediaState.Downloading) {
    Column(
        modifier = Modifier.width(560.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        TvProgressBar(fraction = state.fraction)
        Text(
            text = if (state.totalBytes > 0) {
                "${formatFileSize(state.downloadedBytes)} of ${formatFileSize(state.totalBytes)}"
            } else {
                "Downloading…"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
