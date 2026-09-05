package com.tvgram.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.tvgram.util.avatarColorFor
import com.tvgram.util.initialsOf

/** Chat photo when there is one, coloured initials when there is not. */
@Composable
fun Avatar(
    id: Long,
    title: String,
    photoFileId: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val background = avatarColorFor(id)

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(title),
            color = Color.White,
            fontSize = (size.value * 0.36f).sp,
            style = MaterialTheme.typography.titleMedium,
        )
        if (photoFileId != null && photoFileId != 0) {
            TdImage(
                fileId = photoFileId,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
