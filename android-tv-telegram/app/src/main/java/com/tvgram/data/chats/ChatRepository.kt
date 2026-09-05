package com.tvgram.data.chats

import android.util.Log
import com.tvgram.data.messages.previewOf
import com.tvgram.data.users.UserRepository
import com.tvgram.data.users.displayName
import com.tvgram.td.TdException
import com.tvgram.td.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.drinkless.tdlib.TdApi

/**
 * Keeps a local mirror of the main chat list.
 *
 * TDLib never sends a ready-made list: it sends chats one by one and then a stream of
 * `UpdateChatPosition` values that define the ordering. The repository holds the chat
 * objects, applies every relevant update in place, and re-publishes a sorted snapshot.
 */
class ChatRepository(
    private val client: TelegramClient,
    private val users: UserRepository,
    private val scope: CoroutineScope,
) {

    private companion object {
        const val TAG = "ChatRepository"
        const val PAGE_SIZE = 30
    }

    private val mutex = Mutex()
    private val chatById = LinkedHashMap<Long, TdApi.Chat>()
    private val mainListOrder = HashMap<Long, TdApi.ChatPosition>()

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chatList: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _endReached = MutableStateFlow(false)
    val endReached: StateFlow<Boolean> = _endReached.asStateFlow()

    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()

    fun observe() {
        scope.launch {
            client.updates.collect { update -> applyUpdate(update) }
        }
    }

    /** Asks TDLib for the next page of the main list. 404 means "that was all". */
    suspend fun loadMore() {
        if (_loading.value || _endReached.value) return
        _loading.value = true
        try {
            client.send(TdApi.LoadChats(TdApi.ChatListMain(), PAGE_SIZE))
        } catch (e: TdException) {
            if (e.isNotFound) {
                _endReached.value = true
            } else {
                Log.w(TAG, "LoadChats failed: ${e.description}")
            }
        } catch (e: IllegalStateException) {
            Log.w(TAG, "LoadChats dropped: ${e.message}")
        } finally {
            _loading.value = false
        }
    }

    fun reset() {
        scope.launch {
            mutex.withLock {
                chatById.clear()
                mainListOrder.clear()
            }
            _endReached.value = false
            _chats.value = emptyList()
        }
    }

    suspend fun chat(chatId: Long): TdApi.Chat? {
        mutex.withLock { chatById[chatId] }?.let { return it }
        val chat = client.sendOrNull(TdApi.GetChat(chatId)) ?: return null
        mutex.withLock { chatById[chat.id] = chat }
        return chat
    }

    suspend fun title(chatId: Long): String = chat(chatId)?.title.orEmpty()

    /**
     * Server-side chat search. Telegram matches titles and @usernames, including chats
     * that were never loaded into the local list.
     */
    suspend fun search(text: String, limit: Int = 40): List<ChatSummary> {
        if (text.isBlank()) return emptyList()
        val request = TdApi.SearchChats()
        request.query = text
        request.limit = limit
        val ids = client.sendOrNull(request)?.chatIds?.toList().orEmpty()
        val orders = mutex.withLock { HashMap(mainListOrder) }
        return ids.mapNotNull { id -> chat(id)?.toSummary(orders[id]) }
    }

    fun openChat(chatId: Long) = client.sendAndForget(TdApi.OpenChat(chatId))

    fun closeChat(chatId: Long) = client.sendAndForget(TdApi.CloseChat(chatId))

    private suspend fun applyUpdate(update: TdApi.Object) {
        var changed = true
        mutex.withLock {
            when (update) {
                is TdApi.UpdateNewChat -> {
                    chatById[update.chat.id] = update.chat
                    update.chat.positions?.forEach { rememberPosition(update.chat.id, it) }
                }

                is TdApi.UpdateChatTitle -> chatById[update.chatId]?.title = update.title

                is TdApi.UpdateChatPhoto -> chatById[update.chatId]?.photo = update.photo

                is TdApi.UpdateChatLastMessage -> {
                    chatById[update.chatId]?.lastMessage = update.lastMessage
                    update.positions?.forEach { rememberPosition(update.chatId, it) }
                }

                is TdApi.UpdateChatPosition -> rememberPosition(update.chatId, update.position)

                is TdApi.UpdateChatReadInbox -> chatById[update.chatId]?.let {
                    it.unreadCount = update.unreadCount
                    it.lastReadInboxMessageId = update.lastReadInboxMessageId
                }

                is TdApi.UpdateChatNotificationSettings ->
                    chatById[update.chatId]?.notificationSettings = update.notificationSettings

                is TdApi.UpdateChatDraftMessage -> {
                    chatById[update.chatId]?.draftMessage = update.draftMessage
                    update.positions?.forEach { rememberPosition(update.chatId, it) }
                }

                is TdApi.UpdateUnreadChatCount -> {
                    if (update.chatList is TdApi.ChatListMain) _unreadTotal.value = update.unreadUnmutedCount
                    changed = false
                }

                else -> changed = false
            }
        }
        if (changed) publish()
    }

    /** Called with [mutex] held. */
    private fun rememberPosition(chatId: Long, position: TdApi.ChatPosition) {
        if (position.list !is TdApi.ChatListMain) return
        if (position.order == 0L) {
            mainListOrder.remove(chatId)
        } else {
            mainListOrder[chatId] = position
        }
    }

    private suspend fun publish() {
        val (snapshot, orders) = mutex.withLock {
            chatById.values.toList() to HashMap(mainListOrder)
        }

        val summaries = snapshot
            .mapNotNull { chat ->
                val position = orders[chat.id] ?: return@mapNotNull null
                chat.toSummary(position)
            }
            .sortedByDescending { it.order }

        _chats.value = summaries
    }

    private suspend fun TdApi.Chat.toSummary(position: TdApi.ChatPosition?): ChatSummary {
        val last = lastMessage
        return ChatSummary(
            id = id,
            title = title.ifBlank { "Untitled" },
            kind = type.toKind(),
            photoFileId = photo?.small?.id,
            preview = last?.content?.previewOf() ?: draftMessage?.previewText() ?: "",
            previewSender = last?.senderLabel(this).orEmpty(),
            date = last?.date ?: 0,
            unreadCount = unreadCount,
            isMuted = (notificationSettings?.muteFor ?: 0) > 0,
            isPinned = position?.isPinned == true,
            order = position?.order ?: 0L,
        )
    }

    // `owner`, not `chat`: a parameter called `chat` would shadow the chat(id) lookup below.
    private suspend fun TdApi.Message.senderLabel(owner: TdApi.Chat): String {
        if (owner.type is TdApi.ChatTypePrivate || owner.type is TdApi.ChatTypeSecret) {
            return if (isOutgoing) "You" else ""
        }
        return when (val sender = senderId) {
            is TdApi.MessageSenderUser ->
                if (isOutgoing) "You" else users.user(sender.userId)?.displayName().orEmpty()

            is TdApi.MessageSenderChat -> chat(sender.chatId)?.title.orEmpty()
            else -> ""
        }
    }
}

private fun TdApi.DraftMessage.previewText(): String {
    val content = inputMessageText as? TdApi.InputMessageText ?: return ""
    return content.text?.text.orEmpty()
}

fun TdApi.ChatType.toKind(): ChatKind = when (this) {
    is TdApi.ChatTypePrivate -> ChatKind.PRIVATE
    is TdApi.ChatTypeSecret -> ChatKind.SECRET
    is TdApi.ChatTypeBasicGroup -> ChatKind.GROUP
    is TdApi.ChatTypeSupergroup -> if (isChannel) ChatKind.CHANNEL else ChatKind.SUPERGROUP
    else -> ChatKind.GROUP
}
