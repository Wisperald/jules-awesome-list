package com.tvgram.td

import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-friendly facade over the raw TDLib [Client].
 *
 * TDLib delivers every update on its own JNI thread, and that thread must never be
 * blocked. Updates are therefore pushed into an unbounded channel and re-emitted on
 * [updates] from [scope]; a slow collector delays the flow, never TDLib itself.
 */
class TelegramClient(
    private val scope: CoroutineScope,
) {

    private companion object {
        const val TAG = "TelegramClient"
    }

    private val clientRef = AtomicReference<Client?>(null)
    private val incoming = Channel<TdApi.Object>(Channel.UNLIMITED)
    private val _updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 64)

    /** Every update TDLib produces, including `UpdateAuthorizationState`. */
    val updates: SharedFlow<TdApi.Object> = _updates.asSharedFlow()

    val isRunning: Boolean get() = clientRef.get() != null

    init {
        scope.launch {
            for (update in incoming) {
                _updates.emit(update)
            }
        }
    }

    /**
     * Creates the native client. Safe to call repeatedly — TDLib closes itself after
     * a log-out and the session has to be re-created from scratch.
     */
    @Synchronized
    fun start(): Boolean {
        if (clientRef.get() != null) return true
        if (!TdLibLoader.load()) return false

        val client = Client.create(
            { update -> incoming.trySend(update) },
            { error -> Log.e(TAG, "Exception in the update handler", error) },
            { error -> Log.e(TAG, "Exception in a result handler", error) },
        )
        clientRef.set(client)
        return true
    }

    /** Drops the reference to a client TDLib has already closed. */
    @Synchronized
    fun onClosed() {
        clientRef.set(null)
    }

    /**
     * Sends [query] and suspends until TDLib answers.
     * @throws TdException when TDLib replies with `TdApi.Error`.
     * @throws IllegalStateException when the client has not been started.
     */
    suspend fun <R : TdApi.Object> send(query: TdApi.Function<R>): R =
        suspendCancellableCoroutine { continuation ->
            val client = clientRef.get()
            if (client == null) {
                continuation.resumeWithException(
                    IllegalStateException("TDLib client is not running (query: ${query.simpleName()})"),
                )
                return@suspendCancellableCoroutine
            }
            client.send(query) { result -> continuation.deliver(query, result) }
        }

    /** [send], but `null` instead of an exception. Handy for best-effort lookups. */
    suspend fun <R : TdApi.Object> sendOrNull(query: TdApi.Function<R>): R? = try {
        send(query)
    } catch (e: TdException) {
        Log.w(TAG, "${query.simpleName()} failed: ${e.description}")
        null
    } catch (e: IllegalStateException) {
        Log.w(TAG, "${query.simpleName()} dropped: ${e.message}")
        null
    }

    /** Fire-and-forget: used for `ViewMessages`, `Close` and other void requests. */
    fun sendAndForget(query: TdApi.Function<*>) {
        val client = clientRef.get() ?: return
        client.send(query) { result ->
            if (result is TdApi.Error) {
                Log.w(TAG, "${query.simpleName()} failed: ${result.code} ${result.message}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R : TdApi.Object> CancellableContinuation<R>.deliver(
        query: TdApi.Function<R>,
        result: TdApi.Object,
    ) {
        if (!isActive) return
        if (result is TdApi.Error) {
            resumeWithException(TdException.from(result, query.simpleName()))
        } else {
            // TDLib guarantees the response type for a given request; the cast is erased
            // and a mismatch would mean the bindings and the native library disagree.
            resume(result as R)
        }
    }

    private fun TdApi.Function<*>.simpleName(): String = javaClass.simpleName
}
