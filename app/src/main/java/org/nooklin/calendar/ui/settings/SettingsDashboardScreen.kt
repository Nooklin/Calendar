package org.nooklin.calendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import java.util.Locale
import kotlin.math.roundToInt
import org.nooklin.calendar.data.dashboard.DashCard
import org.nooklin.calendar.data.dashboard.DashboardSettings
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.ui.common.GlassPill

private const val TIME_STEP = 30

private fun clockText(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

private fun lengthText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** The next or previous value in [options]; a value that is not in the list moves to the nearest one on that side. */
private fun step(options: List<Int>, value: Int, direction: Int): Int {
    val sorted = options.sorted()
    return if (direction > 0) sorted.firstOrNull { it > value } ?: sorted.last() else sorted.lastOrNull { it < value } ?: sorted.first()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDashboardScreen(
    settings: DashboardSettings,
    calendars: List<CalendarEntity>,
    onChange: (DashboardSettings) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "Dashboard",
                listOf({
                    SettingsToggleRow("Show the dashboard", settings.enabled, Icons.Filled.Dashboard, Tile.Green) {
                        onChange(settings.copy(enabled = it))
                    }
                })
            )
            SettingsNote(if (settings.enabled) "Off hides the dashboard and its button." else "The dashboard is off. Turn it on to set it up.")
            if (!settings.enabled) return@Column

            CardList(settings, onChange)
            SettingsNote("Drag the handle to change the order. Extended shows more details on a card.")

            SettingsGroup(
                "Evening",
                listOf(
                    {
                        SettingsStepperRow(
                            "Starts at", clockText(settings.eveningStart),
                            onMinus = { onChange(settings.copy(eveningStart = (settings.eveningStart - TIME_STEP).coerceAtLeast(0))) },
                            onPlus = {
                                if (settings.eveningStart + TIME_STEP < settings.eveningEnd) onChange(settings.copy(eveningStart = settings.eveningStart + TIME_STEP))
                            }
                        )
                    },
                    {
                        SettingsStepperRow(
                            "Ends at", clockText(settings.eveningEnd),
                            onMinus = {
                                if (settings.eveningEnd - TIME_STEP > settings.eveningStart) onChange(settings.copy(eveningEnd = settings.eveningEnd - TIME_STEP))
                            },
                            onPlus = { onChange(settings.copy(eveningEnd = (settings.eveningEnd + TIME_STEP).coerceAtMost(24 * 60))) }
                        )
                    }
                )
            )
            SettingsNote("An evening is free when no event falls in this time. For Tonight and Free evenings.")

            SettingsGroup(
                "Daytime",
                listOf(
                    {
                        SettingsStepperRow(
                            "Starts at", clockText(settings.dayStart),
                            onMinus = { onChange(settings.copy(dayStart = (settings.dayStart - TIME_STEP).coerceAtLeast(0))) },
                            onPlus = {
                                if (settings.dayStart + TIME_STEP < settings.dayEnd) onChange(settings.copy(dayStart = settings.dayStart + TIME_STEP))
                            }
                        )
                    },
                    {
                        SettingsStepperRow(
                            "Ends at", clockText(settings.dayEnd),
                            onMinus = {
                                if (settings.dayEnd - TIME_STEP > settings.dayStart) onChange(settings.copy(dayEnd = settings.dayEnd - TIME_STEP))
                            },
                            onPlus = { onChange(settings.copy(dayEnd = (settings.dayEnd + TIME_STEP).coerceAtMost(24 * 60))) }
                        )
                    }
                )
            )
            SettingsNote("Free blocks and Find time look only inside this time.")

            SettingsGroup(
                "Counting",
                listOf(
                    {
                        SettingsToggleRow("All-day events make a day busy", settings.allDayBusy, Icons.Filled.Today, Tile.Orange) {
                            onChange(settings.copy(allDayBusy = it))
                        }
                    },
                    {
                        SettingsStepperRow(
                            "Shortest free time", lengthText(settings.minFree),
                            onMinus = { onChange(settings.copy(minFree = step(DashboardSettings.MIN_FREE_OPTIONS, settings.minFree, -1))) },
                            onPlus = { onChange(settings.copy(minFree = step(DashboardSettings.MIN_FREE_OPTIONS, settings.minFree, 1))) }
                        )
                    }
                )
            )
            SettingsNote("A free block on the dashboard is at least this long.")

            SettingsGroup(
                "Look ahead",
                listOf(
                    {
                        SettingsStepperRow(
                            "Overlaps, Find time", "${settings.lookAhead} days",
                            onMinus = { onChange(settings.copy(lookAhead = step(DashboardSettings.LOOK_AHEAD_OPTIONS, settings.lookAhead, -1))) },
                            onPlus = { onChange(settings.copy(lookAhead = step(DashboardSettings.LOOK_AHEAD_OPTIONS, settings.lookAhead, 1))) }
                        )
                    },
                    {
                        SettingsStepperRow(
                            "All-day events", "${settings.allDayAhead} days",
                            onMinus = { onChange(settings.copy(allDayAhead = step(DashboardSettings.ALL_DAY_AHEAD_OPTIONS, settings.allDayAhead, -1))) },
                            onPlus = { onChange(settings.copy(allDayAhead = step(DashboardSettings.ALL_DAY_AHEAD_OPTIONS, settings.allDayAhead, 1))) }
                        )
                    }
                )
            )

            SettingsNote("How many days Overlaps, Find time and All-day events look ahead.")

            if (calendars.isNotEmpty()) {
                val allIds = calendars.map { it.id }.toSet()
                val chosen = settings.calendarIds ?: allIds
                SettingsGroup(
                    "Calendars that count",
                    calendars.map { calendar ->
                        {
                            SettingsToggleRow(calendar.name, calendar.id in chosen, Icons.Filled.Event, Tile.Blue) { on ->
                                val next = if (on) chosen + calendar.id else chosen - calendar.id
                                // At least one calendar stays on; all on is stored as "all"
                                if (next.isNotEmpty()) onChange(settings.copy(calendarIds = if (next.containsAll(allIds)) null else next))
                            }
                        }
                    }
                )
                SettingsNote("Hidden calendars never count.")
            }

            SettingsGroup(
                null,
                listOf({ SettingsActionRow("Reset the dashboard", Icons.Filled.Refresh, destructive = true) { onChange(DashboardSettings()) } })
            )
            SettingsNote("Everything is calculated on this phone.")
        }
    }
}

@Composable
private fun CardList(settings: DashboardSettings, onChange: (DashboardSettings) -> Unit) {
    val density = LocalDensity.current
    // A row is 48 dp and the divider under it 1 dp
    val rowHeight = 48.dp
    val stepPx = with(density) { (rowHeight + 1.dp).toPx() }
    var dragging by remember { mutableStateOf<DashCard?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val order = settings.order
    val fromIndex = dragging?.let { order.indexOf(it) } ?: -1
    val toIndex = if (fromIndex < 0) -1 else (fromIndex + (offset / stepPx).roundToInt()).coerceIn(0, order.lastIndex)

    SettingsGroup(
        "Cards",
        order.mapIndexed { index, card ->
            {
                val isDragged = card == dragging
                val shift = when {
                    isDragged -> offset
                    fromIndex in 0 until toIndex && index in (fromIndex + 1)..toIndex -> -stepPx
                    toIndex in 0 until fromIndex && index in toIndex until fromIndex -> stepPx
                    else -> 0f
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = shift }
                        .then(if (isDragged) Modifier.background(GlassPill.adaptivePopupBackground) else Modifier)
                        .padding(start = 4.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(rowHeight)
                            .pointerInput(card, order) {
                                detectDragGestures(
                                    onDragStart = { dragging = card; offset = 0f },
                                    onDrag = { change, amount -> change.consume(); offset += amount.y },
                                    onDragEnd = {
                                        val target = (order.indexOf(card) + (offset / stepPx).roundToInt()).coerceIn(0, order.lastIndex)
                                        val from = order.indexOf(card)
                                        if (target != from) {
                                            val moved = order.toMutableList()
                                            moved.removeAt(from)
                                            moved.add(target, card)
                                            onChange(settings.copy(order = moved))
                                        }
                                        dragging = null
                                        offset = 0f
                                    },
                                    onDragCancel = { dragging = null; offset = 0f }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.DragHandle, contentDescription = "Drag to change the order of ${card.title}")
                    }
                    Text(card.title, modifier = Modifier.weight(1f))
                    val extended = card in settings.extended
                    Text(
                        if (extended) "Extended" else "Compact",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .clickable(onClickLabel = "Switch between compact and extended") {
                                onChange(settings.copy(extended = if (extended) settings.extended - card else settings.extended + card))
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                    Switch(
                        checked = card !in settings.disabled,
                        onCheckedChange = { on ->
                            onChange(settings.copy(disabled = if (on) settings.disabled - card else settings.disabled + card))
                        }
                    )
                }
            }
        }
    )
}

/** A row with a value and a minus and a plus button. */
@Composable
private fun SettingsStepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Less: $label") }
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
            fontSize = 15.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "More: $label") }
    }
}
