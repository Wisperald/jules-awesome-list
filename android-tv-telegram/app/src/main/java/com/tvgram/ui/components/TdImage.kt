package com.tvgram.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.tvgram.data.files.FileRepository
import com.tvgram.di.LocalFileRepository
import java.io.File

/**
 * Resolves a TDLib file id to a path on disk, downloading it if necessary.
 *
 * Returns `null` while the download is in flight, so callers can render a placeholder
 * without ever showing an empty box.
 */
@Composable
fun rememberTdFilePath(
    fileId: Int?,
    priority: Int = FileRepository.PRIORITY_THUMBNAIL,
): String? {
    val files = LocalFileRepository.current
    var path by remember(fileId) { mutableStateOf<String?>(null) }

    LaunchedEffect(fileId) {
        path = if (fileId == null || fileId == 0) null else files.downloadNow(fileId, priority)
    }
    return path
}

/** An image stored in Telegram's file storage; [placeholder] shows until the bytes land. */
@Composable
fun TdImage(
    fileId: Int?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    priority: Int = FileRepository.PRIORITY_THUMBNAIL,
    placeholder: @Composable () -> Unit = {},
) {
    val path = rememberTdFilePath(fileId, priority)

    Box(modifier = modifier) {
        if (path == null) {
            placeholder()
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
