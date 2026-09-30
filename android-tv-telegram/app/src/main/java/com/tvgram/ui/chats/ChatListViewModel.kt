package com.tvgram.ui.chats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvgram.data.chats.ChatSummary
import com.tvgram.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatListViewModel(private val container: AppContainer) : ViewModel() {

    val chats: StateFlow<List<ChatSummary>> = container.chats.chatList
    val loading: StateFlow<Boolean> = container.chats.loading
    val endReached: StateFlow<Boolean> = container.chats.endReached
    val me = container.users.me

    var query by mutableStateOf("")
        private set

    private val _searchResults = MutableStateFlow<List<ChatSummary>>(emptyList())
    val searchResults: StateFlow<List<ChatSummary>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadMore()
    }

    fun loadMore() {
        viewModelScope.launch { container.chats.loadMore() }
    }

    /** Debounced: every keystroke on a TV keyboard would otherwise be a round trip. */
    fun onQueryChange(value: String) {
        query = value
        searchJob?.cancel()

        if (value.isBlank()) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            _searching.value = true
            delay(350)
            _searchResults.value = container.chats.search(value)
            _searching.value = false
        }
    }

    fun clearQuery() = onQueryChange("")

    fun rememberOpened(chatId: Long) {
        container.settings.lastOpenedChatId = chatId
    }
}
