package com.tvgram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tvgram.di.LocalAppContainer
import com.tvgram.di.LocalFileRepository
import com.tvgram.ui.navigation.TvGramNavGraph
import com.tvgram.ui.theme.TvGramTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as TvGramApp).container

        setContent {
            val largeText by container.largeText.collectAsStateWithLifecycle()

            CompositionLocalProvider(
                LocalAppContainer provides container,
                LocalFileRepository provides container.files,
            ) {
                TvGramTheme(largeText = largeText) {
                    TvGramNavGraph()
                }
            }
        }
    }
}
