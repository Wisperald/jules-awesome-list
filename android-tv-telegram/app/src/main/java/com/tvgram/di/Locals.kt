package com.tvgram.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvgram.data.files.FileRepository

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer was not provided — wrap the content in ProvideAppContainer")
}

val LocalFileRepository = staticCompositionLocalOf<FileRepository> {
    error("FileRepository was not provided — wrap the content in ProvideAppContainer")
}

/**
 * Creates a ViewModel from the container without reflection or code generation.
 * `key` matters for screens that exist more than once (one per chat, for instance).
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline builder: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = builder(container) as T
    }
    return viewModel(key = key, factory = factory)
}
