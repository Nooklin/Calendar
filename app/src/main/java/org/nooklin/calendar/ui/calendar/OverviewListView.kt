package org.nooklin.calendar.ui.calendar

import org.nooklin.calendar.ui.common.EmptyState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.common.ClockState
import org.nooklin.calendar.ui.common.LocalBottomOverlay

private val TODAY_RED = Color(0xFFFF3B30)
private const val MONTHS_PER_STEP = 6
private const val MAX_MONTHS = 60

/**
 * The overview:  one list of what is coming, from the selected day on, with a heading per day. Days without
 * events are left out. The list grows by six months when its end is reached, up to five years.
 */
@Composable
fun OverviewListView(
    selectedDate: LocalDate,
    clock: ClockState,
    viewModel: CalendarViewModel,
    onNewEvent: () -> Unit,
    onOpenEvent: (Occurrence) -> Unit
) {
    var monthsLoaded by remember(selectedDate) { mutableIntStateOf(MONTHS_PER_STEP) }
    val end = selectedDate.plusMonths(monthsLoaded.toLong())
    val occurrences by remember(selectedDate, monthsLoaded, clock.zone) {
        viewModel.occurrences(selectedDate, end, clock.zone)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Every event on every day it touches, days in order
    val days: List<Pair<LocalDate, List<Occurrence>>> = remember(occurrences, selectedDate, end) {
        val map = sortedMapOf<LocalDate, MutableList<Occurrence>>()
        for (occurrence in occurrences) {
            var day = maxOf(occurrence.startDate, selectedDate)
            val last = minOf(occurrence.lastDate, end.minusDays(1))
            while (!day.isAfter(last)) {
                map.getOrPut(day) { mutableListOf() }.add(occurrence)
                day = day.plusDays(1)
            }
        }
        map.entries.map { (day, list) ->
            day to list.sortedWith(compareByDescending<Occurrence> { it.allDay || it.multiDay }.thenBy { it.start })
        }
    }

    LazyColumn(Modifier.fillMaxSize()) {
        if (days.isEmpty()) {
            item(key = "empty") {
                EmptyState("No events", "New event", onNewEvent, Modifier.fillMaxWidth())
            }
        }
        days.forEach { (day, list) ->
            item(key = "day-$day") {
                val isToday = day == clock.today
                Text(
                    dayTitle(day),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isToday) TODAY_RED else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
                )
            }
            items(list, key = { "$day-${it.key}" }) { occurrence ->
                EventRow(occurrence, day) { onOpenEvent(occurrence) }
            }
        }
        item(key = "end") {
            // Reaching the end loads the next six months, up to five years from the selected day
            LaunchedEffect(monthsLoaded) { if (monthsLoaded < MAX_MONTHS) monthsLoaded += MONTHS_PER_STEP }
            Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}
