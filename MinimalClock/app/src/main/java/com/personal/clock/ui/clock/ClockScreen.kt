@file:OptIn(ExperimentalMaterial3Api::class)

package com.personal.clock.ui.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.clock.R
import com.personal.clock.data.AppSettings
import com.personal.clock.domain.City
import com.personal.clock.domain.CityCatalog
import com.personal.clock.domain.CityOrdering
import com.personal.clock.domain.CitySort
import com.personal.clock.domain.DurationFormat
import com.personal.clock.domain.TimeZoneMath
import com.personal.clock.ui.AppViewModels
import com.personal.clock.ui.components.AutoSizeNumber
import com.personal.clock.ui.components.EmptyState
import com.personal.clock.ui.components.MinTouchTarget
import com.personal.clock.ui.components.SectionHeader
import com.personal.clock.ui.components.rememberWallClock
import com.personal.clock.util.TimeText
import com.personal.clock.ui.components.currentLocale
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun ClockScreen(settings: AppSettings, viewModel: ClockViewModel = viewModel(factory = AppViewModels.Factory)) {
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    val now by rememberWallClock()
    val context = LocalContext.current
    val locale = currentLocale()
    val is24Hour = TimeText.is24Hour(context, settings.timeFormat)
    val localZone = ZoneId.systemDefault()
    var showPicker by rememberSaveable { mutableStateOf(false) }

    val collator = remember(locale) { Collator.getInstance(locale) }
    val names = cityNameResolver()
    val sorted = remember(cities, settings.citySort, now.epochSecond / 60, locale) {
        CityOrdering.sort(cities.orEmpty(), settings.citySort, now, names, collator::compare)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "local") {
                LocalClock(now, localZone, locale, is24Hour, settings.showSeconds)
            }
            item(key = "header") {
                SectionHeader(
                    text = stringResource(R.string.world_clock),
                    trailing = { SortMenu(settings.citySort, viewModel::setSort) },
                )
            }
            if (cities != null && sorted.isEmpty()) {
                item(key = "empty") { EmptyState(R.drawable.ic_public, stringResource(R.string.world_clock_empty)) }
            }
            items(sorted, key = { it.id }) { city ->
                CityRow(
                    city = city,
                    name = names(city),
                    now = now,
                    localZone = localZone,
                    locale = locale,
                    is24Hour = is24Hour,
                    manualOrder = settings.citySort == CitySort.MANUAL,
                    isFirst = sorted.firstOrNull() == city,
                    isLast = sorted.lastOrNull() == city,
                    onMove = { delta -> viewModel.move(city, delta) },
                    onRemove = { viewModel.remove(city) },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { showPicker = true },
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text(stringResource(R.string.add_city)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (showPicker) {
        CityPickerDialog(
            excluded = cities.orEmpty().map { it.id }.toSet(),
            now = now,
            onPick = {
                viewModel.add(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun cityNameResolver(): (City) -> String {
    val resources = LocalResources.current
    return remember(resources) { { city: City -> resources.getString(cityNameRes(city.id)) } }
}

@Composable
private fun LocalClock(now: Instant, zone: ZoneId, locale: Locale, is24Hour: Boolean, showSeconds: Boolean) {
    val time = now.atZone(zone)
    val mainPattern = when {
        is24Hour && showSeconds -> "HH:mm:ss"
        is24Hour -> "HH:mm"
        showSeconds -> "h:mm:ss"
        else -> "h:mm"
    }
    val main = DateTimeFormatter.ofPattern(mainPattern, locale).format(time)
    val amPm = if (is24Hour) null else DateTimeFormatter.ofPattern("a", locale).format(time)
    val date = TimeText.datePattern(locale, "EEEEdMMMMy").format(time)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    val tz = TimeZone.getTimeZone(zone)
    val zoneName = tz.getDisplayName(tz.inDaylightTime(Date.from(now)), TimeZone.LONG, locale)
    val offset = DurationFormat.utcOffset(TimeZoneMath.offsetSeconds(now, zone))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            AutoSizeNumber(
                text = main,
                maxFontSize = 88.sp,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (amPm != null) {
                Text(
                    text = amPm,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
        }
        Text(date, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = "$zoneName · $offset",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CityRow(
    city: City,
    name: String,
    now: Instant,
    localZone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
    manualOrder: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val zone = remember(city.zoneId) { ZoneId.of(city.zoneId) }
    val diffMinutes = TimeZoneMath.offsetDifferenceMinutes(now, localZone, zone)
    val dayDiff = TimeZoneMath.dayDifference(now, localZone, zone)
    val dayText = stringResource(
        when {
            dayDiff < 0 -> R.string.day_yesterday
            dayDiff > 0 -> R.string.day_tomorrow
            else -> R.string.day_today
        },
    )
    val timeText = TimeText.format(now.atZone(zone), locale, is24Hour)
    var menu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "$dayText, ${offsetText(diffMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.city_actions, name))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (manualOrder && !isFirst) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_up)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = null) },
                            onClick = { menu = false; onMove(-1) },
                        )
                    }
                    if (manualOrder && !isLast) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_down)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = null) },
                            onClick = { menu = false; onMove(1) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.remove)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                        onClick = { menu = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable
private fun offsetText(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.offset_same)
    val parts = DurationFormat.offsetParts(minutes)
    val sign = if (parts.sign > 0) "+" else "−"
    return if (parts.minutes == 0) {
        stringResource(R.string.offset_hours, sign, parts.hours)
    } else {
        stringResource(R.string.offset_hours_minutes, sign, parts.hours, parts.minutes)
    }
}

@Composable
private fun SortMenu(current: CitySort, onSelect: (CitySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_sort), contentDescription = stringResource(R.string.sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CitySort.entries.forEach { sort ->
                val label = stringResource(
                    when (sort) {
                        CitySort.MANUAL -> R.string.sort_manual
                        CitySort.NAME -> R.string.sort_name
                        CitySort.OFFSET -> R.string.sort_offset
                    },
                )
                DropdownMenuItem(
                    text = { Text(label) },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = {
                        open = false
                        onSelect(sort)
                    },
                )
            }
        }
    }
}

@Composable
private fun CityPickerDialog(
    excluded: Set<String>,
    now: Instant,
    onPick: (City) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val locale = currentLocale()
    val names = cityNameResolver()
    val collator = remember(locale) { Collator.getInstance(locale) }
    val available = remember(excluded, locale) {
        CityCatalog.available().filter { it.id !in excluded }.sortedWith(compareBy(collator) { names(it) })
    }
    val filtered = available.filter { CityOrdering.matches(it, names(it), query) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.imePadding()) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.close))
                    }
                    Text(stringResource(R.string.add_city), style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.search_city)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
                if (filtered.isEmpty()) {
                    EmptyState(R.drawable.ic_search, stringResource(R.string.search_no_results))
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(filtered, key = { it.id }) { city ->
                        val offset = DurationFormat.utcOffset(TimeZoneMath.offsetSeconds(now, ZoneId.of(city.zoneId)))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouchTarget + 8.dp)
                                .clickable(role = Role.Button) { onPick(city) }
                                .padding(horizontal = 24.dp, vertical = 12.dp)
                                .semantics { stateDescription = offset },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(names(city), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text(offset, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}
