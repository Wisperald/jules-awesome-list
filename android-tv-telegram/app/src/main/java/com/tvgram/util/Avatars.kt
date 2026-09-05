package com.tvgram.util

import androidx.compose.ui.graphics.Color
import com.tvgram.ui.theme.AvatarColors

/**
 * Stable per-chat colour, so the same contact always looks the same.
 *
 * The modulo is folded by hand rather than with `Math.floorMod`: chat ids are negative
 * for groups and channels, and the `long` overload of `floorMod` only exists from API 24.
 */
fun avatarColorFor(id: Long): Color {
    val size = AvatarColors.size
    val index = (((id % size) + size) % size).toInt()
    return AvatarColors[index]
}
