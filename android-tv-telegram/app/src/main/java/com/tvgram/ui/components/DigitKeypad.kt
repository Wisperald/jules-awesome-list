package com.tvgram.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * A D-pad friendly number pad.
 *
 * Entering a phone number through a TV's on-screen IME means walking a QWERTY grid one
 * character at a time. A dedicated 3×4 pad is four times fewer key presses, and it works
 * identically on remotes that have no number keys at all.
 */
@Composable
fun DigitKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    submitEnabled: Boolean,
    modifier: Modifier = Modifier,
    submitLabel: String = "OK",
    firstKeyFocusRequester: FocusRequester? = null,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEachIndexed { columnIndex, label ->
                    KeypadKey(
                        label = label,
                        onClick = { onDigit(label.first()) },
                        modifier = if (rowIndex == 0 && columnIndex == 0 && firstKeyFocusRequester != null) {
                            Modifier.focusRequester(firstKeyFocusRequester)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeypadKey(label = "⌫", onClick = onBackspace)
            KeypadKey(label = "0", onClick = { onDigit('0') })
            KeypadKey(
                label = submitLabel,
                onClick = onSubmit,
                enabled = submitEnabled,
                primary = true,
            )
        }
    }
}

@Composable
private fun KeypadKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    TvSurface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(width = 88.dp, height = 64.dp),
        shape = RoundedCornerShape(14.dp),
        containerColor = if (primary) colors.primaryContainer else colors.surfaceVariant,
        focusedContainerColor = if (primary) colors.primary else colors.surface,
        scaleOnFocus = 1.08f,
    ) { focused ->
        Box(modifier = Modifier.align(Alignment.Center)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    !enabled -> colors.onSurfaceVariant
                    focused && primary -> colors.onPrimary
                    else -> colors.onSurface
                },
            )
        }
    }
}
