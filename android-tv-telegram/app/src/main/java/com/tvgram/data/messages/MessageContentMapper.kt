package com.tvgram.data.messages

import org.drinkless.tdlib.TdApi

/**
 * Turns `TdApi.MessageContent` into the small closed set of bodies the TV UI renders.
 *
 * Telegram has well over a hundred content types; a living-room client only needs a
 * handful of them, so anything else degrades gracefully into [MessageBody.Unsupported]
 * rather than crashing on a message somebody sent from a phone.
 */
fun TdApi.MessageContent.toBody(): MessageBody = when (this) {

    is TdApi.MessageText -> MessageBody.Text(text.text.orEmpty())

    is TdApi.MessagePhoto -> {
        val largest = photo.largest()
        MessageBody.Photo(
            fileId = largest?.photo?.id ?: 0,
            thumbnailFileId = photo.smallest()?.photo?.id,
            caption = caption?.text.orEmpty(),
            width = largest?.width ?: 0,
            height = largest?.height ?: 0,
        )
    }

    is TdApi.MessageVideo -> MessageBody.Video(
        fileId = video.video.id,
        thumbnailFileId = video.thumbnail?.file?.id,
        caption = caption?.text.orEmpty(),
        durationSeconds = video.duration,
        fileName = video.fileName.orEmpty().ifBlank { "video.mp4" },
        mimeType = video.mimeType.orEmpty(),
        sizeBytes = video.video.size,
    )

    is TdApi.MessageVideoNote -> MessageBody.Video(
        fileId = videoNote.video.id,
        thumbnailFileId = videoNote.thumbnail?.file?.id,
        caption = "",
        durationSeconds = videoNote.duration,
        fileName = "video_note.mp4",
        mimeType = "video/mp4",
        sizeBytes = videoNote.video.size,
    )

    is TdApi.MessageAnimation -> MessageBody.Animation(
        fileId = animation.animation.id,
        thumbnailFileId = animation.thumbnail?.file?.id,
        caption = caption?.text.orEmpty(),
    )

    is TdApi.MessageAudio -> MessageBody.Audio(
        fileId = audio.audio.id,
        title = audio.title.orEmpty().ifBlank { audio.fileName.orEmpty() },
        performer = audio.performer.orEmpty(),
        durationSeconds = audio.duration,
    )

    is TdApi.MessageVoiceNote -> MessageBody.Voice(
        fileId = voiceNote.voice.id,
        durationSeconds = voiceNote.duration,
    )

    is TdApi.MessageDocument -> MessageBody.Document(
        fileId = document.document.id,
        fileName = document.fileName.orEmpty().ifBlank { "file" },
        mimeType = document.mimeType.orEmpty(),
        sizeBytes = document.document.size,
        caption = caption?.text.orEmpty(),
    )

    is TdApi.MessageSticker -> MessageBody.Sticker(
        fileId = sticker.sticker.id,
        emoji = sticker.emoji.orEmpty(),
    )

    is TdApi.MessageLocation -> MessageBody.Location(
        latitude = location.latitude,
        longitude = location.longitude,
        title = "",
    )

    is TdApi.MessageVenue -> MessageBody.Location(
        latitude = venue.location.latitude,
        longitude = venue.location.longitude,
        title = venue.title.orEmpty(),
    )

    is TdApi.MessageContact -> MessageBody.Contact(
        displayName = listOf(contact.firstName, contact.lastName)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .ifBlank { contact.phoneNumber.orEmpty() },
        phoneNumber = contact.phoneNumber.orEmpty(),
    )

    is TdApi.MessageCall -> MessageBody.Call(
        durationSeconds = duration,
        isVideo = isVideo,
        isMissed = discardReason is TdApi.CallDiscardReasonMissed ||
            discardReason is TdApi.CallDiscardReasonDeclined,
    )

    is TdApi.MessageChatChangeTitle -> MessageBody.Service("Chat renamed to \"$title\"")
    is TdApi.MessageChatChangePhoto -> MessageBody.Service("Chat photo changed")
    is TdApi.MessageChatAddMembers -> MessageBody.Service("Members joined the chat")
    is TdApi.MessageChatDeleteMember -> MessageBody.Service("A member left the chat")
    is TdApi.MessageChatJoinByLink -> MessageBody.Service("Joined the chat via invite link")
    is TdApi.MessagePinMessage -> MessageBody.Service("A message was pinned")
    is TdApi.MessageUnsupported -> MessageBody.Unsupported("Unsupported message")

    else -> MessageBody.Unsupported(javaClass.simpleName.removePrefix("Message"))
}

/** Chat-list preview text; deliberately cheap, it runs for every visible row. */
fun TdApi.MessageContent.previewOf(): String = toBody().preview

fun TdApi.Photo.largest(): TdApi.PhotoSize? = sizes?.maxByOrNull { it.width.toLong() * it.height }

fun TdApi.Photo.smallest(): TdApi.PhotoSize? = sizes?.minByOrNull { it.width.toLong() * it.height }

/** The best photo size that still fits into [maxWidth]; falls back to the smallest one. */
fun TdApi.Photo.bestFor(maxWidth: Int): TdApi.PhotoSize? {
    val all = sizes?.toList().orEmpty()
    if (all.isEmpty()) return null
    return all.filter { it.width <= maxWidth }.maxByOrNull { it.width } ?: all.minByOrNull { it.width }
}
