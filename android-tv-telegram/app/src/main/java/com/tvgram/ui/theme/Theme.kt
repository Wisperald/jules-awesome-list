package com.tvgram.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvGramColorScheme = darkColorScheme(
    primary = TelegramBlue,
    onPrimary = Ink,
    primaryContainer = TelegramBlueDeep,
    onPrimaryContainer = TextPrimary,
    secondary = TelegramBlueSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = Slate,
    onSurface = TextPrimary,
    surfaceVariant = SlateVariant,
    onSurfaceVariant = TextSecondary,
    error = Danger,
    onError = Ink,
)

/** True when the user asked for bigger type in Settings. */
val LocalLargeText = staticCompositionLocalOf { false }

@Composable
fun TvGramTheme(
    largeText: Boolean = false,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLargeText provides largeText) {
        MaterialTheme(
            colorScheme = TvGramColorScheme,
            typography = tvGramTypography(largeText),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                content()
            }
        }
    }
}
