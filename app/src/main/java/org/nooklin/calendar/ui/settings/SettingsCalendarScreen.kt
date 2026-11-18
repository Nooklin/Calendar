package org.nooklin.calendar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.nooklin.calendar.data.local.WeekStart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCalendarScreen(
    weekStart: WeekStart,
    onWeekStartChange: (WeekStart) -> Unit,
    showWeekNumbers: Boolean,
    onShowWeekNumbersChange: (Boolean) -> Unit,
    dutchHolidays: Boolean,
    onDutchHolidaysChange: (Boolean) -> Unit,
    askLocation: Boolean,
    onAskLocationChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", fontWeight = FontWeight.Bold) },
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
                "Week",
                listOf<@Composable () -> Unit>(
                    { SettingsChoiceRow("Week starts on", WeekStart.entries.map { it to it.label }, weekStart, onSelect = onWeekStartChange) },
                    { SettingsToggleRow("Show week numbers", showWeekNumbers, Icons.Filled.CalendarMonth, Tile.Red, onCheckedChange = onShowWeekNumbersChange) }
                )
            )
            SettingsNote("Week numbers show in the month view and in the week view.")
            SettingsGroup(
                "Holidays",
                listOf({
                    SettingsToggleRow("Show Dutch holidays", dutchHolidays, Icons.Filled.CalendarMonth, Tile.Orange, onCheckedChange = onDutchHolidaysChange)
                })
            )
            SettingsNote("Public holidays show as all-day events and cannot be changed.")
            SettingsGroup(
                "Saving an event",
                listOf({
                    SettingsToggleRow("Ask when there is no location", askLocation, Icons.Filled.CalendarMonth, Tile.Blue, onCheckedChange = onAskLocationChange)
                })
            )
            SettingsNote("Asks if that is right when you save an event without a location.")
        }
    }
}
