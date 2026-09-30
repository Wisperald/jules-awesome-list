package com.tvgram.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The one interactive primitive the whole app is built from.
 *
 * It deliberately uses plain Compose Foundation rather than `tv-material3`'s own
 * `Surface`: `Modifier.clickable` already reacts to the D-pad centre key, and doing the
 * focus visuals by hand keeps every control on screen consistent — a focused element is
 * always *lighter, outlined and slightly larger*, which is the only affordance a viewer
 * three metres away can reliably read.
 */
@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    focusedContainerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    scaleOnFocus: Float = 1.02f,
    onFocusChanged: (Boolean) -> Unit = {},
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val background by animateColorAsState(
        targetValue = if (focused) focusedContainerColor else containerColor,
        label = "surfaceBackground",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) scaleOnFocus else 1f,
        label = "surfaceScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .background(color = background, shape = shape)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) focusedBorderColor else Color.Transparent,
                shape = shape,
            ),
    ) {
        content(focused)
    }
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    TvSurface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minWidth = 140.dp, minHeight = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        containerColor = if (primary) colors.primaryContainer else colors.surfaceVariant,
        focusedContainerColor = if (primary) colors.primary else colors.surface,
    ) { focused ->
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    !enabled -> colors.onSurfaceVariant
                    focused && primary -> colors.onPrimary
                    else -> colors.onSurface
                },
            )
        }
    }
}
