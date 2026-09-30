package com.tvgram.data.chats

/** One row of the chat list. */
data class ChatSummary(
    val id: Long,
    val title: String,
    val kind: ChatKind,
    val photoFileId: Int?,
    val preview: String,
    val previewSender: String,
    val date: Int,
    val unreadCount: Int,
    val isMuted: Boolean,
    val isPinned: Boolean,
    val order: Long,
)

enum class ChatKind { PRIVATE, SECRET, GROUP, SUPERGROUP, CHANNEL }
