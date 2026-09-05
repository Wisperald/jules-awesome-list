package com.tvgram.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvgram.data.users.displayName
import com.tvgram.data.users.primaryUsername
import com.tvgram.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

data class AccountInfo(
    val name: String = "",
    val username: String? = null,
    val phoneNumber: String = "",
    val photoFileId: Int? = null,
    val userId: Long = 0,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _account = MutableStateFlow(AccountInfo())
    val account: StateFlow<AccountInfo> = _account.asStateFlow()

    private val _cacheBytes = MutableStateFlow(-1L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    private val _clearingCache = MutableStateFlow(false)
    val clearingCache: StateFlow<Boolean> = _clearingCache.asStateFlow()

    val largeText: StateFlow<Boolean> = container.largeText
    val markAsRead: StateFlow<Boolean> = container.markAsRead

    init {
        viewModelScope.launch {
            val user = container.users.me.value ?: container.users.loadMe()
            if (user != null) _account.value = user.toAccountInfo()
        }
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val stats = container.client.sendOrNull(TdApi.GetStorageStatisticsFast())
            _cacheBytes.value = stats?.filesSize ?: -1L
        }
    }

    /** Deletes every cached file; the database and the session are untouched. */
    fun clearCache() {
        if (_clearingCache.value) return
        viewModelScope.launch {
            _clearingCache.value = true
            val request = TdApi.OptimizeStorage()
            request.size = 0
            request.ttl = 0
            request.count = 0
            request.immunityDelay = 0
            request.chatLimit = 0
            // TDLib's JNI layer dereferences these arrays; null would crash it.
            request.fileTypes = emptyArray()
            request.chatIds = LongArray(0)
            request.excludeChatIds = LongArray(0)
            request.returnDeletedFileStatistics = false
            container.client.sendOrNull(request)
            _clearingCache.value = false
            refreshCacheSize()
        }
    }

    fun setLargeText(enabled: Boolean) = container.setLargeText(enabled)

    fun setMarkAsRead(enabled: Boolean) = container.setMarkAsRead(enabled)

    fun logOut() = container.auth.logOut()
}

private fun TdApi.User.toAccountInfo() = AccountInfo(
    name = displayName(),
    username = primaryUsername()?.let { "@$it" },
    phoneNumber = phoneNumber?.let { "+$it" }.orEmpty(),
    photoFileId = profilePhoto?.small?.id,
    userId = id,
)
