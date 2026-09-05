package com.tvgram.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.tvgram.di.AppContainer
import com.tvgram.di.containerViewModel
import com.tvgram.ui.components.ErrorState
import com.tvgram.ui.components.TvProgressBar
import java.io.File

/** Full-screen photo, letterboxed on black. */
@Composable
fun PhotoViewerScreen(
    fileId: Int,
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
            is MediaState.Downloading -> Column(
                modifier = Modifier.width(480.dp).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Loading photo…",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                TvProgressBar(fraction = current.fraction)
            }

            is MediaState.Failed -> ErrorState(
                title = "Cannot open this photo",
                detail = current.reason,
                actionLabel = "Back",
                onAction = onBack,
            )

            is MediaState.Ready -> AsyncImage(
                model = File(current.localPath),
                contentDescription = "Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
