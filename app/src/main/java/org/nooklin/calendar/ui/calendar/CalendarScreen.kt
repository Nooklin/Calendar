package org.nooklin.calendar.ui.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.temporal.WeekFields
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.DutchHolidays
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.common.ClockState
import org.nooklin.calendar.ui.navigation.ViewMenu
import org.nooklin.calendar.ui.tour.tourTarget
import org.nooklin.calendar.ui.navigation.ViewMode

/** The main screen: a title with the month, one of the four views, and the sheets for event details and calendars. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    view: ViewMode,
    onSelectView: (ViewMode) -> Unit,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    clock: ClockState,
    weekFields: WeekFields,
    showWeekNumbers: Boolean,
    viewModel: CalendarViewModel,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    calendarsOpen: Boolean,
    onCloseCalendars: () -> Unit,
    onEdit: (Occurrence) -> Unit,
    onDuplicate: (Occurrence) -> Unit,
    onNewEvent: () -> Unit
) {
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val density by viewModel.monthDensity.collectAsStateWithLifecycle()
    var openEvent by remember { mutableStateOf<Occurrence?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        monthTitle(selectedDate),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    ViewMenu(view, onSelectView, density, viewModel::setMonthDensity)
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onOpenSettings, modifier = Modifier.tourTarget("settings")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (view) {
                ViewMode.MONTH -> MonthView(selectedDate, onSelectDate, onOpenDay, clock, weekFields, showWeekNumbers, density, viewModel, onNewEvent) { openEvent = it }
                ViewMode.WEEK -> TimeGridView(7, selectedDate, onSelectDate, clock, weekFields, showWeekNumbers, viewModel) { openEvent = it }
                ViewMode.DAY -> TimeGridView(1, selectedDate, onSelectDate, clock, weekFields, showWeekNumbers, viewModel) { openEvent = it }
                ViewMode.OVERVIEW -> OverviewListView(selectedDate, clock, viewModel, onNewEvent) { openEvent = it }
            }
        }
    }

    // A tap on a search result opens its details
    val pending = viewModel.pendingOpen
    LaunchedEffect(pending) {
        if (pending != null) {
            openEvent = pending
            viewModel.pendingOpen = null
        }
    }

    val event = openEvent
    if (event != null) {
        EventSheet(
            occurrence = event,
            zone = clock.zone,
            onEdit = { openEvent = null; onEdit(event) },
            onDuplicate = { openEvent = null; onDuplicate(event) },
            onDelete = { scope -> openEvent = null; viewModel.delete(event, scope) },
            onDismiss = { openEvent = null },
            readOnly = event.locked || event.calendarId == DutchHolidays.CALENDAR_ID || calendars.firstOrNull { it.id == event.calendarId }?.sourceUrl != null,
            readOnlyText = if (event.calendarId == DutchHolidays.CALENDAR_ID) {
                "This is a Dutch holiday. You can turn the holidays off in Settings > Calendar."
            } else if (event.locked) {
                "This repeating event has days that were changed on their own. This app cannot send that back to the " +
                    "CalDAV server, so it is shown here and can be changed in another app."
            } else {
                "This event is in a calendar that follows a link. It cannot be changed here."
            }
        )
    }
    if (calendarsOpen) {
        CalendarsSheet(
            calendars = calendars,
            onToggle = { viewModel.setCalendarVisible(it.id, !it.visible) },
            onAdd = { name, color -> viewModel.addCalendar(name, color) },
            onUpdate = { calendar, name, color -> viewModel.updateCalendar(calendar.id, name, color) },
            onDelete = { viewModel.deleteCalendar(it.id) },
            onDismiss = onCloseCalendars
        )
    }
}
