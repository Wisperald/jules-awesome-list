package com.tvgram.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.data.chats.ChatKind
import com.tvgram.data.chats.ChatSummary
import com.tvgram.data.users.displayName
import com.tvgram.di.AppContainer
import com.tvgram.di.containerViewModel
import com.tvgram.ui.components.Avatar
import com.tvgram.ui.components.EmptyState
import com.tvgram.ui.components.LoadingState
import com.tvgram.ui.components.TvSpinner
import com.tvgram.ui.components.TvSurface
import com.tvgram.ui.components.TvTextField
import com.tvgram.util.formatBadge
import com.tvgram.util.formatMessageTimestamp
import kotlinx.coroutines.flow.distinctUntilChanged

/** The home screen: the main chat list, plus search and a way into Settings. */
@Composable
fun ChatListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatListViewModel =
        containerViewModel { container: AppContainer -> ChatListViewModel(container) }

    val chats by viewModel.chats.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val endReached by viewModel.endReached.collectAsStateWithLifecycle()
    val me by viewModel.me.collectAsStateWithLifecycle()

    val isSearching = viewModel.query.isNotBlank()
    val visible = if (isSearching) searchResults else chats
    val listState = rememberLazyListState()

    // Page in more chats a screenful before the user reaches the bottom.
    LaunchedEffect(listState, isSearching, endReached) {
        if (isSearching) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { lastVisible ->
                if (!endReached && lastVisible >= visible.lastIndex - 5) viewModel.loadMore()
            }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Header(
            userName = me?.displayName() ?: "Telegram",
            query = viewModel.query,
            onQueryChange = viewModel::onQueryChange,
            onOpenSettings = onOpenSettings,
        )

        when {
            visible.isEmpty() && (loading || searching) -> LoadingState(text = "Loading chats…")

            visible.isEmpty() && isSearching -> EmptyState(
                title = "Nothing found",
                detail = "No chat matches \"${viewModel.query}\"",
            )

            visible.isEmpty() -> EmptyState(
                title = "No chats yet",
                detail = "Chats you have on your phone will appear here.",
            )

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 48.dp,
                    end = 48.dp,
                    top = 8.dp,
                    bottom = 40.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = visible, key = { it.id }) { chat ->
                    ChatRow(
                        chat = chat,
                        onClick = {
                            viewModel.rememberOpened(chat.id)
                            onOpenChat(chat.id)
                        },
                    )
                }

                if (!endReached && !isSearching) {
                    item(key = "loading-more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            TvSpinner(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    userName: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = userName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Box(modifier = Modifier.width(420.dp)) {
            TvTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "Search chats",
            )
        }

        TvSurface(
            onClick = onOpenSettings,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            scaleOnFocus = 1.1f,
        ) { focused ->
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = if (focused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        }
    }
}

@Composable
private fun ChatRow(
    chat: ChatSummary,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    TvSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = colors.surface,
        focusedContainerColor = colors.surfaceVariant,
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Avatar(
                id = chat.id,
                title = chat.title,
                photoFileId = chat.photoFileId,
                size = 60.dp,
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val badge = chat.kind.badge()
                    if (badge.isNotEmpty()) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                        )
                    }
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (focused) colors.primary else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (chat.isMuted) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = "Muted",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = chat.previewLine(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMessageTimestamp(chat.date),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (chat.isMuted) colors.surfaceVariant else colors.primary)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = formatBadge(chat.unreadCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (chat.isMuted) colors.onSurfaceVariant else colors.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

private fun ChatSummary.previewLine(): String =
    if (previewSender.isBlank()) preview else "$previewSender: $preview"

private fun ChatKind.badge(): String = when (this) {
    ChatKind.CHANNEL -> "📢"
    ChatKind.GROUP, ChatKind.SUPERGROUP -> "👥"
    ChatKind.SECRET -> "🔒"
    ChatKind.PRIVATE -> ""
}
