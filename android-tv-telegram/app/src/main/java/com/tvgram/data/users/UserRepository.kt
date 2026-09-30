package com.tvgram.data.users

import com.tvgram.td.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * A read-through cache of `TdApi.User`.
 *
 * TDLib pushes every user it knows about through `UpdateUser`, so the cache is warm
 * for anything the chat list references; [user] only hits the network for strangers.
 */
class UserRepository(
    private val client: TelegramClient,
    private val scope: CoroutineScope,
) {

    private val cache = ConcurrentHashMap<Long, TdApi.User>()

    private val _me = MutableStateFlow<TdApi.User?>(null)
    val me: StateFlow<TdApi.User?> = _me.asStateFlow()

    fun observe() {
        scope.launch {
            client.updates.collect { update ->
                when (update) {
                    is TdApi.UpdateUser -> {
                        cache[update.user.id] = update.user
                        if (update.user.id == _me.value?.id) _me.value = update.user
                    }

                    is TdApi.UpdateUserStatus -> {
                        cache[update.userId]?.let { it.status = update.status }
                    }

                    else -> Unit
                }
            }
        }
    }

    suspend fun loadMe(): TdApi.User? {
        val user = client.sendOrNull(TdApi.GetMe())
        if (user != null) {
            cache[user.id] = user
            _me.value = user
        }
        return user
    }

    fun cached(userId: Long): TdApi.User? = cache[userId]

    suspend fun user(userId: Long): TdApi.User? {
        cache[userId]?.let { return it }
        val user = client.sendOrNull(TdApi.GetUser(userId)) ?: return null
        cache[userId] = user
        return user
    }

    suspend fun displayName(userId: Long): String = user(userId)?.displayName() ?: "Unknown"
}

fun TdApi.User.displayName(): String {
    val full = listOf(firstName, lastName).filter { !it.isNullOrBlank() }.joinToString(" ")
    return when {
        full.isNotBlank() -> full
        !usernames?.activeUsernames.isNullOrEmpty() -> "@" + usernames!!.activeUsernames.first()
        !phoneNumber.isNullOrBlank() -> "+$phoneNumber"
        else -> "Unknown"
    }
}

fun TdApi.User.primaryUsername(): String? =
    usernames?.activeUsernames?.firstOrNull()
