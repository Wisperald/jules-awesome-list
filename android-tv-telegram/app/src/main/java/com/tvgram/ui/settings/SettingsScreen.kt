package com.tvgram.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.BuildConfig
import com.tvgram.di.AppContainer
import com.tvgram.di.containerViewModel
import com.tvgram.ui.components.Avatar
import com.tvgram.ui.components.TvButton
import com.tvgram.ui.components.TvSurface
import com.tvgram.util.formatFileSize

/** Account information, the two preferences worth having, cache control and sign-out. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel =
        containerViewModel { container: AppContainer -> SettingsViewModel(container) }

    val account by viewModel.account.collectAsStateWithLifecycle()
    val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
    val clearingCache by viewModel.clearingCache.collectAsStateWithLifecycle()
    val largeText by viewModel.largeText.collectAsStateWithLifecycle()
    val markAsRead by viewModel.markAsRead.collectAsStateWithLifecycle()

    var confirmingLogOut by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 96.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "account") {
            AccountCard(account)
            Spacer(Modifier.height(16.dp))
        }

        item(key = "section-appearance") { SectionTitle("Appearance") }

        item(key = "large-text") {
            ToggleRow(
                title = "Larger text",
                subtitle = "Increases every font size by 15% for easier reading from the sofa",
                checked = largeText,
                onToggle = { viewModel.setLargeText(!largeText) },
            )
        }

        item(key = "section-privacy") { SectionTitle("Privacy") }

        item(key = "read-receipts") {
            ToggleRow(
                title = "Send read receipts",
                subtitle = "Mark messages as read when you open a chat on this TV",
                checked = markAsRead,
                onToggle = { viewModel.setMarkAsRead(!markAsRead) },
            )
        }

        item(key = "section-storage") { SectionTitle("Storage") }

        item(key = "cache") {
            ActionRow(
                title = if (clearingCache) "Clearing…" else "Clear downloaded media",
                subtitle = when {
                    cacheBytes < 0 -> "Calculating…"
                    cacheBytes == 0L -> "Nothing is cached"
                    else -> "${formatFileSize(cacheBytes)} of photos and videos on this device"
                },
                enabled = !clearingCache && cacheBytes > 0,
                onClick = viewModel::clearCache,
            )
        }

        item(key = "section-account") { SectionTitle("Account") }

        item(key = "logout") {
            if (confirmingLogOut) {
                LogOutConfirmation(
                    onConfirm = {
                        confirmingLogOut = false
                        viewModel.logOut()
                    },
                    onCancel = { confirmingLogOut = false },
                )
            } else {
                ActionRow(
                    title = "Log out",
                    subtitle = "Removes this device from your Telegram sessions",
                    destructive = true,
                    onClick = { confirmingLogOut = true },
                )
            }
        }

        item(key = "about") {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "TVGram ${BuildConfig.VERSION_NAME} — an unofficial Telegram client " +
                    "for Android TV, built on TDLib.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item(key = "back") {
            Spacer(Modifier.height(20.dp))
            TvButton(text = "Back to chats", onClick = onBack)
        }
    }
}

@Composable
private fun AccountCard(account: AccountInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Avatar(
            id = account.userId,
            title = account.name.ifBlank { "?" },
            photoFileId = account.photoFileId,
            size = 84.dp,
        )
        Column {
            Text(
                text = account.name.ifBlank { "Loading…" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val secondary = listOfNotNull(account.username, account.phoneNumber.ifBlank { null })
            if (secondary.isNotEmpty()) {
                Text(
                    text = secondary.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    SettingRow(title = title, subtitle = subtitle, onClick = onToggle) {
        Box(
            modifier = Modifier
                .width(64.dp)
                .height(32.dp)
                .clip(CircleShape)
                .background(
                    if (checked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        enabled = enabled,
        titleColor = if (destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
) {
    TvSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) { _ ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) titleColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun LogOutConfirmation(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Log out of Telegram?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "The local message cache on this TV will be deleted and you will have to " +
                "sign in again to use the app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TvButton(text = "Cancel", onClick = onCancel, primary = true)
            TvButton(text = "Log out", onClick = onConfirm)
        }
    }
}
