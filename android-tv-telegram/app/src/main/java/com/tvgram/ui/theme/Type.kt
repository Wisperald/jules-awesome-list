package com.tvgram.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

/**
 * Ten-foot typography: everything is a few steps larger than the phone defaults, and
 * nothing lands below 16sp — text smaller than that is unreadable from a sofa.
 */
fun tvGramTypography(largeText: Boolean): Typography {
    val scale = if (largeText) 1.15f else 1f
    fun size(value: Int) = (value * scale).sp

    return Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = size(48),
            lineHeight = size(56),
        ),
        headlineMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(30),
            lineHeight = size(38),
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = size(24),
            lineHeight = size(30),
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = size(20),
            lineHeight = size(26),
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = size(19),
            lineHeight = size(26),
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = size(17),
            lineHeight = size(23),
        ),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = size(16),
            lineHeight = size(21),
        ),
        labelMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = size(15),
            lineHeight = size(20),
        ),
    )
}
