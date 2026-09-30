package com.tvgram.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvgram.data.chats.ChatKind
import com.tvgram.data.chats.toKind
import com.tvgram.data.messages.ChatEvent
import com.tvgram.data.messages.MessageItem
import com.tvgram.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Header information for the open chat. */
data class ChatHeader(
    val id: Long = 0,
    val title: String = "",
    val photoFileId: Int? = null,
    val kind: ChatKind = ChatKind.PRIVATE,
    val canSendMessages: Boolean = true,
)

/**
 * One open conversation.
 *
 * Messages are kept newest-first, which is exactly the order TDLib returns them in and
 * the order a `reverseLayout` LazyColumn wants: index 0 is the bubble at the bottom of
 * the screen, and "load older" simply appends.
 */
class ChatViewModel(
    private val container: AppContainer,
    val chatId: Long,
) : ViewModel() {

    private companion object {
        const val PAGE_SIZE = 30
        const val READ_BATCH = 20
    }

    private val _messages = MutableStateFlow<List<MessageItem>>(emptyList())
    val messages: StateFlow<List<MessageItem>> = _messages.asStateFlow()

    private val _header = MutableStateFlow(ChatHeader())
    val header: StateFlow<ChatHeader> = _header.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadingOlder = MutableStateFlow(false)
    val loadingOlder: StateFlow<Boolean> = _loadingOlder.asStateFlow()

    private val _reachedBeginning = MutableStateFlow(false)
    val reachedBeginning: StateFlow<Boolean> = _reachedBeginning.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    var draft by mutableStateOf("")

    init {
        container.chats.openChat(chatId)
        loadHeader()
        loadInitialHistory()
        observeEvents()
    }

    private fun loadHeader() {
        viewModelScope.launch {
            val chat = container.chats.chat(chatId) ?: return@launch
            val kind = chat.type.toKind()
            _header.value = ChatHeader(
                id = chat.id,
                title = chat.title.orEmpty().ifBlank { "Chat" },
                photoFileId = chat.photo?.small?.id,
                kind = kind,
                // Broadcast channels reject messages from everyone but admins; rather than
                // offer a composer that always fails, hide it and let admins use a phone.
                canSendMessages = kind != ChatKind.CHANNEL,
            )
        }
    }

    private fun loadInitialHistory() {
        viewModelScope.launch {
            _loading.value = true
            val page = container.messages.history(chatId, fromMessageId = 0, limit = PAGE_SIZE)
            _messages.value = page
            _loading.value = false
            _reachedBeginning.value = page.isEmpty()
            markVisibleAsRead()
        }
    }

    fun loadOlder() {
        if (_loadingOlder.value || _reachedBeginning.value || _loading.value) return
        val oldest = _messages.value.lastOrNull() ?: return

        viewModelScope.launch {
            _loadingOlder.value = true
            val page = container.messages.history(chatId, oldest.id, PAGE_SIZE)
            if (page.isEmpty()) {
                _reachedBeginning.value = true
            } else {
                val known = _messages.value.mapTo(HashSet()) { it.id }
                _messages.update { current -> current + page.filterNot { it.id in known } }
            }
            _loadingOlder.value = false
        }
    }

    private fun observeEvents() {
        viewModelScope.launch {
            container.messages.events(chatId).collect { event ->
                when (event) {
                    is ChatEvent.Added -> {
                        _messages.update { current ->
                            if (current.any { it.id == event.message.id }) {
                                current
                            } else {
                                listOf(event.message) + current
                            }
                        }
                        markVisibleAsRead()
                    }

                    is ChatEvent.Edited -> _messages.update { current ->
                        current.map { item ->
                            if (item.id == event.messageId) item.copy(body = event.body) else item
                        }
                    }

                    is ChatEvent.Deleted -> {
                        val removed = event.messageIds.toHashSet()
                        _messages.update { current -> current.filterNot { it.id in removed } }
                    }

                    is ChatEvent.SendSucceeded -> _messages.update { current ->
                        current.map { item ->
                            if (item.id == event.oldMessageId) event.message else item
                        }
                    }

                    is ChatEvent.SendFailed -> {
                        _sendError.value = event.reason
                        _messages.update { current ->
                            current.map { item ->
                                if (item.id == event.oldMessageId) {
                                    item.copy(isSending = false, failedToSend = true)
                                } else {
                                    item
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        _sendError.value = null

        viewModelScope.launch {
            val pending = container.messages.sendText(chatId, text)
            if (pending == null) {
                _sendError.value = "The message could not be sent"
                draft = text
            } else {
                _messages.update { current -> listOf(pending) + current }
            }
        }
    }

    fun dismissSendError() {
        _sendError.value = null
    }

    private fun markVisibleAsRead() {
        if (!container.settings.markAsRead) return
        val ids = _messages.value.take(READ_BATCH).filterNot { it.isOutgoing }.map { it.id }
        container.messages.markViewed(chatId, ids)
    }

    override fun onCleared() {
        super.onCleared()
        container.chats.closeChat(chatId)
    }
}
