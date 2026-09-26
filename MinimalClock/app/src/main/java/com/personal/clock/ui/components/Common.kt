package com.personal.clock.ui.components

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.clock.R
import com.personal.clock.domain.DurationFormat
import java.util.Locale

/** Current UI locale, read through the configuration so a language change recomposes. */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Current window size in dp (correct in split-screen and freeform windows). */
@Composable
fun windowSizeDp(): DpSize {
    val size = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) { DpSize(size.width.toDp(), size.height.toDp()) }
}

/** Minimum touch target recommended by Material / accessibility guidelines. */
val MinTouchTarget = 48.dp

/** Large single-line numeric text that shrinks to fit (font scaling, narrow screens, landscape). */
@Composable
fun AutoSizeNumber(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxFontSize: TextUnit = 96.sp,
    minFontSize: TextUnit = 28.sp,
    textAlign: TextAlign = TextAlign.Center,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = textAlign),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = maxFontSize, stepSize = 2.sp),
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "−  value  +" control with large touch targets and a spoken value. */
@Composable
fun Stepper(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    canDecrement: Boolean,
    canIncrement: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = onDecrement,
            enabled = canDecrement,
            modifier = Modifier.semantics { contentDescription = "$label −" },
        ) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = null)
        }
        Box(Modifier.widthIn(min = 72.dp), contentAlignment = Alignment.Center) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { stateDescription = valueText },
            )
        }
        FilledTonalIconButton(
            onClick = onIncrement,
            enabled = canIncrement,
            modifier = Modifier.semantics { contentDescription = "$label +" },
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
        }
    }
}

/** "7 h 5 min" style text for a positive duration. */
fun durationText(resources: Resources, millis: Long): String {
    val (days, hours, minutes) = DurationFormat.splitDaysHoursMinutes(millis)
    return when {
        days > 0 -> resources.getString(R.string.duration_days_hours, days, hours)
        hours > 0 -> resources.getString(R.string.duration_hours_minutes, hours, minutes)
        else -> resources.getString(R.string.duration_minutes, minutes)
    }
}

@Composable
fun durationUntilText(millis: Long): String = durationText(LocalResources.current, millis)
