package com.tvgram.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.data.chats.ChatKind
import com.tvgram.di.AppContainer
import com.tvgram.di.containerViewModel
import com.tvgram.ui.components.Avatar
import com.tvgram.ui.components.EmptyState
import com.tvgram.ui.components.LoadingState
import com.tvgram.ui.components.TvButton
import com.tvgram.ui.components.TvSpinner
import com.tvgram.ui.components.TvTextField
import com.tvgram.util.sameDay
import com.tvgram.util.formatDayHeader
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A single conversation: history above, composer below.
 *
 * The list is reversed so that new messages appear at the bottom without any scroll
 * gymnastics, and "up" on the D-pad naturally walks backwards through history — which is
 * also where the next page gets requested.
 */
@Composable
fun ChatScreen(
    chatId: Long,
    onOpenPhoto: (Int) -> Unit,
    onOpenVideo: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatViewModel = containerViewModel(key = "chat-$chatId") { container: AppContainer ->
        ChatViewModel(container, chatId)
    }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val header by viewModel.header.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val loadingOlder by viewModel.loadingOlder.collectAsStateWithLifecycle()
    val reachedBeginning by viewModel.reachedBeginning.collectAsStateWithLifecycle()
    val sendError by viewModel.sendError.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    LaunchedEffect(listState, reachedBeginning) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!reachedBeginning && lastVisible >= messages.lastIndex - 5) viewModel.loadOlder()
            }
    }

    // A message that arrives while the user is at the bottom should stay in view.
    LaunchedEffect(messages.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 2) listState.animateScrollToItem(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        ChatHeaderBar(header = header)

        Box(modifier = Modifier.weight(1f)) {
            when {
                loading -> LoadingState(text = "Loading messages…")

                messages.isEmpty() -> EmptyState(
                    title = "No messages yet",
                    detail = "Say something to start the conversation.",
                )

                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 56.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = messages,
                        key = { _, item -> item.id },
                    ) { index, item ->
                        // `messages` is newest-first, so the *next* index is the older message.
                        val older = messages.getOrNull(index + 1)

                        Column {
                            // Reverse layout only reverses item order; inside an item the
                            // day header still has to come first to sit above the message.
                            if (older == null || !sameDay(older.date, item.date)) {
                                DayHeader(text = formatDayHeader(item.date))
                            }
                            MessageRow(
                                message = item,
                                showSenderName = header.kind != ChatKind.PRIVATE &&
                                    header.kind != ChatKind.SECRET &&
                                    older?.senderId != item.senderId,
                                onAction = { action ->
                                    when (action) {
                                        is MessageAction.OpenPhoto -> onOpenPhoto(action.fileId)
                                        is MessageAction.OpenVideo ->
                                            onOpenVideo(action.fileId, action.title)

                                        is MessageAction.OpenAudio ->
                                            onOpenVideo(action.fileId, action.title)
                                    }
                                },
                            )
                        }
                    }

                    if (loadingOlder) {
                        item(key = "older-spinner") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                TvSpinner(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        if (sendError != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 56.dp, vertical = 8.dp),
            ) {
                Text(
                    text = sendError.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onError,
                )
            }
        }

        if (header.canSendMessages) {
            Composer(
                draft = viewModel.draft,
                onDraftChange = { viewModel.draft = it },
                onSend = viewModel::send,
            )
        }
    }
}

@Composable
private fun ChatHeaderBar(header: ChatHeader) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 56.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Avatar(
            id = header.id,
            title = header.title,
            photoFileId = header.photoFileId,
            size = 48.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = header.kind.label(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "BACK to return",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 56.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            TvTextField(
                value = draft,
                onValueChange = onDraftChange,
                placeholder = "Write a message",
                imeAction = ImeAction.Send,
                onImeAction = onSend,
            )
        }
        TvButton(
            text = "Send",
            onClick = onSend,
            primary = true,
            enabled = draft.isNotBlank(),
        )
    }
}

private fun ChatKind.label(): String = when (this) {
    ChatKind.PRIVATE -> "Private chat"
    ChatKind.SECRET -> "Secret chat"
    ChatKind.GROUP -> "Group"
    ChatKind.SUPERGROUP -> "Group"
    ChatKind.CHANNEL -> "Channel"
}
