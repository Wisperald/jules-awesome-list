package com.personal.clock.ui.alarms

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.personal.clock.R
import com.personal.clock.domain.DaysOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** "Once", "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri" in locale week order. */
@Composable
fun repeatText(daysMask: Int, locale: Locale): String = when (daysMask) {
    DaysOfWeek.NONE -> stringResource(R.string.repeat_once)
    DaysOfWeek.EVERY_DAY -> stringResource(R.string.repeat_every_day)
    DaysOfWeek.WEEKDAYS -> stringResource(R.string.repeat_weekdays)
    DaysOfWeek.WEEKEND -> stringResource(R.string.repeat_weekend)
    else -> DaysOfWeek.week(WeekFields.of(locale).firstDayOfWeek)
        .filter { daysMask and DaysOfWeek.bit(it) != 0 }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
}
