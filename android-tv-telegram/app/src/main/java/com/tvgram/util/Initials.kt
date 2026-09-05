package com.tvgram.util

/**
 * One or two letters for an avatar placeholder, the way every messenger does it.
 * Kept free of Android and Compose types so it can be unit tested on the JVM.
 */
fun initialsOf(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
