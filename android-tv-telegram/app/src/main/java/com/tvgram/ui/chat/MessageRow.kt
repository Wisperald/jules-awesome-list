package com.tvgram.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.data.messages.MessageBody
import com.tvgram.data.messages.MessageItem
import com.tvgram.ui.components.TdImage
import com.tvgram.ui.components.TvSurface
import com.tvgram.ui.theme.BubbleIncoming
import com.tvgram.ui.theme.BubbleOutgoing
import com.tvgram.util.formatClockTime
import com.tvgram.util.formatDuration
import com.tvgram.util.formatFileSize

/** What tapping a message should do, if anything. */
sealed interface MessageAction {
    data class OpenPhoto(val fileId: Int) : MessageAction
    data class OpenVideo(val fileId: Int, val title: String) : MessageAction
    data class OpenAudio(val fileId: Int, val title: String) : MessageAction
}

@Composable
fun MessageRow(
    message: MessageItem,
    showSenderName: Boolean,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.body is MessageBody.Service) {
        ServiceMessage(text = message.body.text, modifier = modifier)
        return
    }

    val action = message.body.actionOrNull()
    val alignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (message.isOutgoing) BubbleOutgoing else BubbleIncoming

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = alignment) {
        TvSurface(
            onClick = { action?.let(onAction) },
            modifier = Modifier.fillMaxWidth(0.62f),
            shape = RoundedCornerShape(18.dp),
            containerColor = bubbleColor,
            focusedContainerColor = bubbleColor.lighten(),
            scaleOnFocus = 1.01f,
        ) { _ ->
            Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                if (showSenderName && !message.isOutgoing && message.senderName.isNotBlank()) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                MessageContent(body = message.body)

                Spacer(Modifier.height(6.dp))
                Footer(message = message)
            }
        }
    }
}

@Composable
private fun MessageContent(body: MessageBody) {
    val colors = MaterialTheme.colorScheme

    when (body) {
        is MessageBody.Text -> Text(
            text = body.text,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
        )

        is MessageBody.Photo -> Column {
            MediaPreview(
                fileId = body.thumbnailFileId ?: body.fileId,
                overlayIcon = null,
                label = null,
            )
            Caption(body.caption)
        }

        is MessageBody.Video -> Column {
            MediaPreview(
                fileId = body.thumbnailFileId,
                overlayIcon = Icons.Default.PlayArrow,
                label = formatDuration(body.durationSeconds),
            )
            Caption(body.caption)
        }

        is MessageBody.Animation -> Column {
            MediaPreview(
                fileId = body.thumbnailFileId,
                overlayIcon = Icons.Default.PlayArrow,
                label = "GIF",
            )
            Caption(body.caption)
        }

        is MessageBody.Sticker -> Text(
            text = body.emoji.ifBlank { "Sticker" },
            style = MaterialTheme.typography.displayLarge,
            color = colors.onSurface,
        )

        is MessageBody.Voice -> IconLine(
            icon = Icons.Default.Mic,
            title = "Voice message",
            subtitle = formatDuration(body.durationSeconds),
        )

        is MessageBody.Audio -> IconLine(
            icon = Icons.Default.MusicNote,
            title = listOf(body.performer, body.title).filter { it.isNotBlank() }
                .joinToString(" — ").ifBlank { "Audio" },
            subtitle = formatDuration(body.durationSeconds),
        )

        is MessageBody.Document -> Column {
            IconLine(
                icon = Icons.Default.Description,
                title = body.fileName,
                subtitle = formatFileSize(body.sizeBytes),
            )
            Caption(body.caption)
        }

        is MessageBody.Location -> IconLine(
            icon = Icons.Default.Place,
            title = body.title.ifBlank { "Location" },
            subtitle = "%.5f, %.5f".format(body.latitude, body.longitude),
        )

        is MessageBody.Contact -> IconLine(
            icon = Icons.Default.Person,
            title = body.displayName,
            subtitle = body.phoneNumber,
        )

        is MessageBody.Call -> IconLine(
            icon = Icons.Default.Phone,
            title = body.preview,
            subtitle = if (body.durationSeconds > 0) formatDuration(body.durationSeconds) else "",
        )

        is MessageBody.Service -> Text(
            text = body.text,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        is MessageBody.Unsupported -> Text(
            text = "${body.label} — open this message on your phone",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaPreview(
    fileId: Int?,
    overlayIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        TdImage(
            fileId = fileId,
            contentDescription = label,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        if (overlayIcon != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = overlayIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        if (label != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun Caption(caption: String) {
    if (caption.isBlank()) return
    Spacer(Modifier.height(8.dp))
    Text(
        text = caption,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun IconLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Footer(message: MessageItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (message.isEdited) {
            Text(
                text = "edited",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = formatClockTime(message.date),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message.isSending || message.failedToSend) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (message.failedToSend) {
                    Icons.Default.ErrorOutline
                } else {
                    Icons.Default.Schedule
                },
                contentDescription = if (message.failedToSend) "Failed to send" else "Sending",
                tint = if (message.failedToSend) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ServiceMessage(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun MessageBody.actionOrNull(): MessageAction? = when (this) {
    is MessageBody.Photo -> MessageAction.OpenPhoto(fileId)
    is MessageBody.Video -> MessageAction.OpenVideo(fileId, fileName)
    is MessageBody.Animation -> MessageAction.OpenVideo(fileId, "GIF")
    is MessageBody.Audio -> MessageAction.OpenAudio(fileId, title.ifBlank { "Audio" })
    is MessageBody.Voice -> MessageAction.OpenAudio(fileId, "Voice message")
    else -> null
}

/** Focused bubbles get a touch lighter so the D-pad position is never in doubt. */
private fun Color.lighten(amount: Float = 0.12f): Color = Color(
    red = (red + amount).coerceAtMost(1f),
    green = (green + amount).coerceAtMost(1f),
    blue = (blue + amount).coerceAtMost(1f),
    alpha = alpha,
)
