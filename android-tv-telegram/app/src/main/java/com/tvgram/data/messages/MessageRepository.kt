package com.tvgram.data.messages

import android.util.Log
import com.tvgram.data.chats.ChatRepository
import com.tvgram.data.users.UserRepository
import com.tvgram.data.users.displayName
import com.tvgram.td.TdException
import com.tvgram.td.TelegramClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import org.drinkless.tdlib.TdApi

/** What can happen to the messages of an open chat while the user is looking at it. */
sealed interface ChatEvent {
    data class Added(val message: MessageItem) : ChatEvent
    data class Edited(val messageId: Long, val body: MessageBody) : ChatEvent
    data class Deleted(val messageIds: List<Long>) : ChatEvent
    data class SendSucceeded(val oldMessageId: Long, val message: MessageItem) : ChatEvent
    data class SendFailed(val oldMessageId: Long, val reason: String) : ChatEvent
}

/**
 * History paging and outgoing messages for a single chat at a time.
 *
 * TDLib's `GetChatHistory` is deliberately lazy: the first call for a cold chat can
 * come back empty while the messages are still being fetched from the server, so
 * [history] retries once before reporting "no more messages".
 */
class MessageRepository(
    private val client: TelegramClient,
    private val chats: ChatRepository,
    private val users: UserRepository,
) {

    private companion object {
        const val TAG = "MessageRepository"
    }

    /**
     * @param fromMessageId 0 for the newest messages, otherwise the oldest message
     *   already displayed — TDLib returns the messages *before* it.
     * @return messages ordered newest first, exactly as TDLib returns them.
     */
    suspend fun history(chatId: Long, fromMessageId: Long, limit: Int): List<MessageItem> {
        repeat(2) { attempt ->
            val request = TdApi.GetChatHistory()
            request.chatId = chatId
            request.fromMessageId = fromMessageId
            request.offset = 0
            request.limit = limit
            request.onlyLocal = false
            val messages = try {
                client.send(request).messages
            } catch (e: TdException) {
                Log.w(TAG, "GetChatHistory failed: ${e.description}")
                return emptyList()
            } catch (e: IllegalStateException) {
                return emptyList()
            }
            val list = messages?.filterNotNull().orEmpty()
            if (list.isNotEmpty() || attempt == 1) return list.map { map(it) }
        }
        return emptyList()
    }

    fun events(chatId: Long): Flow<ChatEvent> = client.updates
        .filter { it.belongsTo(chatId) }
        .mapNotNull { update ->
            when (update) {
                is TdApi.UpdateNewMessage -> ChatEvent.Added(map(update.message))

                is TdApi.UpdateMessageContent ->
                    ChatEvent.Edited(update.messageId, update.newContent.toBody())

                is TdApi.UpdateDeleteMessages ->
                    if (update.isPermanent) {
                        ChatEvent.Deleted(update.messageIds?.toList().orEmpty())
                    } else {
                        null
                    }

                is TdApi.UpdateMessageSendSucceeded ->
                    ChatEvent.SendSucceeded(update.oldMessageId, map(update.message))

                is TdApi.UpdateMessageSendFailed ->
                    ChatEvent.SendFailed(update.oldMessageId, update.error?.message ?: "Send failed")

                else -> null
            }
        }

    /** Sends plain text; the echo arrives through [events] as `SendSucceeded`. */
    suspend fun sendText(chatId: Long, text: String): MessageItem? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val content = TdApi.InputMessageText()
        content.text = TdApi.FormattedText(trimmed, emptyArray())

        val request = TdApi.SendMessage()
        request.chatId = chatId
        request.inputMessageContent = content
        val message = client.sendOrNull(request) ?: return null
        return map(message, isSending = true)
    }

    /** Tells Telegram the user actually saw these messages, which clears the badge. */
    fun markViewed(chatId: Long, messageIds: List<Long>) {
        if (messageIds.isEmpty()) return
        val request = TdApi.ViewMessages()
        request.chatId = chatId
        request.messageIds = messageIds.toLongArray()
        request.forceRead = true
        client.sendAndForget(request)
    }

    suspend fun map(message: TdApi.Message, isSending: Boolean = false): MessageItem {
        val senderId = message.senderId
        val senderName = when (senderId) {
            is TdApi.MessageSenderUser -> users.user(senderId.userId)?.displayName().orEmpty()
            is TdApi.MessageSenderChat -> chats.chat(senderId.chatId)?.title.orEmpty()
            else -> ""
        }
        val senderKey = when (senderId) {
            is TdApi.MessageSenderUser -> senderId.userId
            is TdApi.MessageSenderChat -> senderId.chatId
            else -> 0L
        }
        return MessageItem(
            id = message.id,
            chatId = message.chatId,
            senderName = senderName,
            senderId = senderKey,
            isOutgoing = message.isOutgoing,
            date = message.date,
            editDate = message.editDate,
            body = message.content.toBody(),
            isSending = isSending || message.sendingState is TdApi.MessageSendingStatePending,
            failedToSend = message.sendingState is TdApi.MessageSendingStateFailed,
        )
    }
}

private fun TdApi.Object.belongsTo(chatId: Long): Boolean = when (this) {
    is TdApi.UpdateNewMessage -> message.chatId == chatId
    is TdApi.UpdateMessageContent -> this.chatId == chatId
    is TdApi.UpdateDeleteMessages -> this.chatId == chatId
    is TdApi.UpdateMessageSendSucceeded -> message.chatId == chatId
    is TdApi.UpdateMessageSendFailed -> message.chatId == chatId
    else -> false
}
