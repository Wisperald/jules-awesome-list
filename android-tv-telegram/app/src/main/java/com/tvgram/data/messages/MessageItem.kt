package com.tvgram.data.messages

/** A single rendered message, decoupled from `TdApi.Message`. */
data class MessageItem(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val senderId: Long,
    val isOutgoing: Boolean,
    val date: Int,
    val editDate: Int,
    val body: MessageBody,
    val isSending: Boolean = false,
    val failedToSend: Boolean = false,
) {
    val isEdited: Boolean get() = editDate > 0
}

/** The subset of Telegram content this TV client knows how to display. */
sealed interface MessageBody {

    /** Text shown in the chat list and in notifications. */
    val preview: String

    data class Text(val text: String) : MessageBody {
        override val preview: String get() = text
    }

    data class Photo(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val caption: String,
        val width: Int,
        val height: Int,
    ) : MessageBody {
        override val preview: String get() = withCaption("🖼 Photo", caption)
    }

    data class Video(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val caption: String,
        val durationSeconds: Int,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
    ) : MessageBody {
        override val preview: String get() = withCaption("🎬 Video", caption)
    }

    data class Animation(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val caption: String,
    ) : MessageBody {
        override val preview: String get() = withCaption("GIF", caption)
    }

    data class Audio(
        val fileId: Int,
        val title: String,
        val performer: String,
        val durationSeconds: Int,
    ) : MessageBody {
        override val preview: String
            get() = "🎵 " + listOf(performer, title).filter { it.isNotBlank() }
                .joinToString(" — ").ifBlank { "Audio" }
    }

    data class Voice(val fileId: Int, val durationSeconds: Int) : MessageBody {
        override val preview: String get() = "🎤 Voice message"
    }

    data class Document(
        val fileId: Int,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val caption: String,
    ) : MessageBody {
        override val preview: String get() = withCaption("📄 $fileName", caption)
    }

    data class Sticker(val fileId: Int, val emoji: String) : MessageBody {
        override val preview: String get() = "$emoji Sticker".trim()
    }

    data class Location(val latitude: Double, val longitude: Double, val title: String) : MessageBody {
        override val preview: String get() = if (title.isBlank()) "📍 Location" else "📍 $title"
    }

    data class Contact(val displayName: String, val phoneNumber: String) : MessageBody {
        override val preview: String get() = "👤 $displayName"
    }

    data class Call(val durationSeconds: Int, val isVideo: Boolean, val isMissed: Boolean) : MessageBody {
        override val preview: String
            get() = when {
                isMissed && isVideo -> "Missed video call"
                isMissed -> "Missed call"
                isVideo -> "Video call"
                else -> "Call"
            }
    }

    /** Joins, title changes, pinned messages — rendered centered and dimmed. */
    data class Service(val text: String) : MessageBody {
        override val preview: String get() = text
    }

    data class Unsupported(val label: String) : MessageBody {
        override val preview: String get() = label
    }
}

private fun withCaption(label: String, caption: String): String =
    if (caption.isBlank()) label else "$label: $caption"
