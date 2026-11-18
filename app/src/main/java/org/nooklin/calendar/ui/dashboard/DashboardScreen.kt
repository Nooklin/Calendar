package org.nooklin.calendar.ui.dashboard

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import org.nooklin.calendar.data.dashboard.DashCard
import org.nooklin.calendar.data.dashboard.DashboardLogic
import org.nooklin.calendar.data.dashboard.DashboardSettings
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.travelLabel
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.calendar.timeFormatter
import org.nooklin.calendar.ui.common.ClockState
import org.nooklin.calendar.ui.common.GlassPill
import org.nooklin.calendar.ui.common.LocalBottomOverlay
import org.nooklin.calendar.ui.settings.Tile

private val FreeColor = Tile.Green
private val BusyColor = Tile.Red

/** The dashboard: cards with what is free, what is next and what needs a look, all worked out on the phone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    clock: ClockState,
    weekFields: WeekFields,
    viewModel: CalendarViewModel,
    onOpenSettings: () -> Unit,
    onSearch: () -> Unit,
    onOpen: (Occurrence) -> Unit,
    onNewEventAt: (LocalDateTime, LocalDateTime) -> Unit
) {
    val settings by viewModel.dashboard.collectAsStateWithLifecycle()
    val calendars by viewModel.calendars.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val today = clock.today
    val now = clock.now
    val firstDay = today.with(weekFields.dayOfWeek(), 1)
    // The week of today, and yesterday for a weekend that is going on
    val from = minOf(firstDay, today.minusDays(1))
    val to = today.plusDays(maxOf(settings.lookAhead, settings.allDayAhead, 31).toLong() + 1)
    val flow = remember(from, to, clock.zone) { viewModel.occurrences(from, to, clock.zone) }
    val loaded by flow.collectAsStateWithLifecycle(emptyList())
    val occ = remember(loaded, settings) { DashboardLogic.counted(loaded, settings) }
    val week = remember(firstDay) { (0L..6L).map { firstDay.plusDays(it) } }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dashboard", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (settings.shown.isEmpty()) {
                Text(
                    "All cards are turned off. Turn them on in Settings > Dashboard.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
            settings.shown.forEach { card ->
                key(card) {
                    val extended = card in settings.extended
                    when (card) {
                        DashCard.TONIGHT -> TonightCard(occ, settings, now, extended, onOpen)
                        DashCard.FREE_EVENINGS -> FreeEveningsCard(occ, settings, now, week, extended)
                        DashCard.NEXT -> NextCard(occ, now, extended, onOpen) { openInNavigation(context, it) }
                        DashCard.TODAY -> TodayCard(occ, settings, today, extended)
                        DashCard.FREE_BLOCKS -> FreeBlocksCard(occ, settings, now, extended)
                        DashCard.WEEK_LOAD -> WeekLoadCard(occ, settings, week, extended)
                        DashCard.WEEKEND -> WeekendCard(occ, settings, today, extended, onOpen)
                        DashCard.OVERLAPS -> OverlapsCard(occ, settings, now, extended, onOpen)
                        DashCard.FIND_TIME -> FindTimeCard(occ, settings, now, extended, onNewEventAt)
                        DashCard.ALL_DAY -> AllDayCard(occ, settings, today, extended, onOpen)
                        DashCard.REMINDERS -> RemindersCard(occ, today, extended, onOpen)
                        DashCard.SYNC -> SyncCard(calendars, syncing, clock, extended, viewModel)
                    }
                }
            }
            Spacer(Modifier.height(LocalBottomOverlay.current + 16.dp))
        }
    }
}

// Shared pieces

@Composable
private fun DashCardFrame(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = GlassPill.adaptiveGlassBackground,
        shape = RoundedCornerShape(20.dp),
        border = GlassPill.adaptiveBorder,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun BigText(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun SubText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, fontSize = 14.sp, color = color)
}

@Composable
private fun Dot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
private fun StatusLine(free: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (free) FreeColor else BusyColor, 14)
        Spacer(Modifier.width(10.dp))
        BigText(text)
    }
}

/** One event in a list: its colour, title and a line of detail. A tap opens the event. */
@Composable
private fun EventLine(o: Occurrence, detail: String, onOpen: (Occurrence) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(o) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Dot(Color(o.color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title.ifBlank { "(No title)" }, fontSize = 16.sp, maxLines = 1)
            val shown = if (o.travelMinutes > 0 && !o.allDay) "$detail, travel ${travelLabel(o.travelMinutes)}" else detail
            Text(shown, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun time(value: LocalDateTime): String = value.toLocalTime().format(timeFormatter)

private fun span(start: LocalDateTime, end: LocalDateTime): String = "${time(start)} to ${time(end)}"

private fun minutesToText(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

private fun dayShort(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun dateShort(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))

private fun dayAndTime(value: LocalDateTime): String = "${dateShort(value.toLocalDate())}, ${time(value)}"

private fun openInNavigation(context: Context, location: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location)))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No navigation app found", Toast.LENGTH_SHORT).show()
    }
}

// The cards

@Composable
private fun TonightCard(occ: List<Occurrence>, s: DashboardSettings, now: LocalDateTime, extended: Boolean, onOpen: (Occurrence) -> Unit) {
    val today = now.toLocalDate()
    val window = DashboardLogic.eveningWindow(today, s)
    val evening = DashboardLogic.evening(occ, s, today)
    DashCardFrame("Tonight") {
        when {
            !now.isBefore(window.end) -> BigText("The evening is over")
            evening.free -> StatusLine(true, "Free tonight")
            else -> StatusLine(false, "Busy from ${time(evening.busyFrom ?: window.start)}")
        }
        SubText("Evening: ${span(window.start, window.end)}")
        if (extended && evening.events.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            evening.events.forEach { o ->
                EventLine(o, if (o.allDay) "All day" else span(o.start, o.end), onOpen)
            }
        }
    }
}

@Composable
private fun FreeEveningsCard(occ: List<Occurrence>, s: DashboardSettings, now: LocalDateTime, week: List<LocalDate>, extended: Boolean) {
    val today = now.toLocalDate()
    // null: this evening is past
    val states = week.map { day ->
        val window = DashboardLogic.eveningWindow(day, s)
        if (day.isBefore(today) || (day == today && !now.isBefore(window.end))) null
        else DashboardLogic.evening(occ, s, day).free
    }
    val freeDays = week.filterIndexed { i, _ -> states[i] == true }
    DashCardFrame("Free evenings") {
        val n = freeDays.size
        BigText(if (n == 1) "1 free evening this week" else "$n free evenings this week")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            week.forEachIndexed { i, day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayShort(day), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val color = when (states[i]) {
                        null -> Color.Gray.copy(alpha = 0.3f)
                        true -> FreeColor
                        false -> BusyColor
                    }
                    Dot(color, 16)
                }
            }
        }
        if (extended) {
            Spacer(Modifier.height(10.dp))
            SubText(if (freeDays.isEmpty()) "No free evenings left" else "Free: " + freeDays.joinToString(", ") { dateShort(it) })
        }
    }
}

private fun countdown(now: LocalDateTime, o: Occurrence): String {
    if (!o.start.isAfter(now)) return "Now, until ${time(o.end)}"
    val minutes = Duration.between(now, o.start).toMinutes()
    return when {
        minutes < 1 -> "Now"
        minutes < 60 -> "In $minutes min"
        o.startDate == now.toLocalDate() -> "In ${minutesToText(minutes)}"
        o.startDate == now.toLocalDate().plusDays(1) -> "Tomorrow at ${time(o.start)}"
        else -> dayAndTime(o.start)
    }
}

@Composable
private fun NextCard(occ: List<Occurrence>, now: LocalDateTime, extended: Boolean, onOpen: (Occurrence) -> Unit, onLocation: (String) -> Unit) {
    val list = DashboardLogic.upcoming(occ, now)
    DashCardFrame("Up next") {
        val first = list.firstOrNull()
        if (first == null) {
            BigText("Nothing coming up")
        } else {
            Column(Modifier.fillMaxWidth().clickable { onOpen(first) }) {
                BigText(first.title.ifBlank { "(No title)" })
                SubText(countdown(now, first))
                SubText(span(first.start, first.end))
                if (first.travelMinutes > 0 && !first.allDay) SubText("Travel " + travelLabel(first.travelMinutes))
            }
            if (first.location.isNotBlank()) {
                Text(
                    first.location,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.padding(top = 4.dp).clickable(onClickLabel = "Open in a navigation app") { onLocation(first.location) }
                )
            }
            if (extended) {
                list.drop(1).take(2).forEach { o ->
                    EventLine(o, "${countdown(now, o)}, ${span(o.start, o.end)}", onOpen)
                }
            }
        }
    }
}

@Composable
private fun TodayCard(occ: List<Occurrence>, s: DashboardSettings, today: LocalDate, extended: Boolean) {
    val start = today.atStartOfDay()
    val end = today.plusDays(1).atStartOfDay()
    val events = occ.filter { it.touches(today) }
    val timed = events.filter { !it.allDay }
    val busyMinutes = DashboardLogic.busy(occ, s, start, end).sumOf { it.minutes }
    DashCardFrame("Today in numbers") {
        if (events.isEmpty()) {
            BigText("No events today")
        } else {
            Row(Modifier.fillMaxWidth()) {
                Stat(events.size.toString(), if (events.size == 1) "event" else "events", Modifier.weight(1f))
                Stat(if (busyMinutes == 0L) "0 min" else minutesToText(busyMinutes), "busy", Modifier.weight(1f))
                if (extended) {
                    Stat(timed.minOfOrNull { maxOf(it.start, start) }?.let { time(it) } ?: "-", "first start", Modifier.weight(1f))
                    Stat(timed.maxOfOrNull { minOf(it.end, end) }?.let { time(it) } ?: "-", "last end", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FreeBlocksCard(occ: List<Occurrence>, s: DashboardSettings, now: LocalDateTime, extended: Boolean) {
    val window = DashboardLogic.dayWindow(now.toLocalDate(), s)
    val from = maxOf(window.start, now)
    DashCardFrame("Free blocks today") {
        if (!from.isBefore(window.end)) {
            BigText("The day is over")
            SubText("Daytime: ${span(window.start, window.end)}")
        } else {
            val blocks = DashboardLogic.freeBlocks(DashboardLogic.busy(occ, s, from, window.end), from, window.end, s.minFree)
            val longest = blocks.maxByOrNull { it.minutes }
            if (longest == null) {
                BigText("No free block")
                SubText("Nothing of ${minutesToText(s.minFree.toLong())} or more left in ${span(window.start, window.end)}")
            } else {
                BigText("Longest: ${minutesToText(longest.minutes)}")
                SubText(span(longest.start, longest.end))
                if (extended) {
                    Spacer(Modifier.height(6.dp))
                    blocks.forEach { b ->
                        Text("${span(b.start, b.end)}  (${minutesToText(b.minutes)})", fontSize = 15.sp, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekLoadCard(occ: List<Occurrence>, s: DashboardSettings, week: List<LocalDate>, extended: Boolean) {
    val loads = DashboardLogic.weekLoad(occ, s, week)
    val max = loads.maxOrNull() ?: 0L
    val busiest = if (max > 0) loads.indexOf(max) else -1
    // Four hours is a full bar, or more when a day is busier
    val scale = maxOf(max, 240L).toFloat()
    val accent = MaterialTheme.colorScheme.primary
    DashCardFrame("Week load") {
        if (busiest < 0) {
            BigText("Nothing planned this week")
        } else {
            BigText("Busiest: ${dayShort(week[busiest])}, ${minutesToText(max)}")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            loads.forEachIndexed { i, minutes ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
                    if (minutes > 0) {
                        Box(
                            Modifier
                                .width(18.dp)
                                .height((74f * minutes / scale).coerceAtLeast(4f).dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (i == busiest) accent else accent.copy(alpha = 0.35f))
                        )
                    } else {
                        Box(Modifier.width(18.dp).height(2.dp).background(Color.Gray.copy(alpha = 0.3f)))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { i, day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayShort(day), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (extended) {
                        Text(
                            String.format(Locale.getDefault(), "%.1f", loads[i] / 60f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        if (extended) SubText("Hours of busy time per day")
    }
}

@Composable
private fun WeekendCard(occ: List<Occurrence>, s: DashboardSettings, today: LocalDate, extended: Boolean, onOpen: (Occurrence) -> Unit) {
    val (saturday, sunday) = DashboardLogic.weekend(today)
    val busy = DashboardLogic.busy(occ, s, saturday.atStartOfDay(), sunday.plusDays(1).atStartOfDay())
    val events = occ.filter { it.touches(saturday) || it.touches(sunday) }.sortedBy { it.start }
    val title = if (today.dayOfWeek.value >= 6) "This weekend" else "Next weekend"
    DashCardFrame(title) {
        StatusLine(busy.isEmpty(), if (busy.isEmpty()) "Free weekend" else "Plans this weekend")
        SubText("${dateShort(saturday)} and ${dateShort(sunday)}")
        SubText(if (events.isEmpty()) "No events" else if (events.size == 1) "1 event" else "${events.size} events")
        if (extended) {
            Spacer(Modifier.height(6.dp))
            events.forEach { o ->
                EventLine(o, if (o.allDay) "${dateShort(o.startDate)}, all day" else dayAndTime(o.start), onOpen)
            }
        }
    }
}

@Composable
private fun OverlapsCard(occ: List<Occurrence>, s: DashboardSettings, now: LocalDateTime, extended: Boolean, onOpen: (Occurrence) -> Unit) {
    val list = DashboardLogic.overlaps(occ, now, now.toLocalDate().plusDays(s.lookAhead.toLong()).atStartOfDay())
    DashCardFrame("Overlaps") {
        if (list.isEmpty()) {
            BigText("No overlaps")
            SubText("In the next ${s.lookAhead} days")
        } else {
            BigText(if (list.size == 1) "1 overlap" else "${list.size} overlaps")
            Spacer(Modifier.height(4.dp))
            list.take(if (extended) 10 else 2).forEach { pair ->
                Column(Modifier.fillMaxWidth().clickable { onOpen(pair.first) }.padding(vertical = 6.dp)) {
                    Text("${pair.first.title.ifBlank { "(No title)" }} and ${pair.second.title.ifBlank { "(No title)" }}", fontSize = 16.sp, maxLines = 2)
                    Text(dayAndTime(pair.second.start), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun FindTimeCard(
    occ: List<Occurrence>,
    s: DashboardSettings,
    now: LocalDateTime,
    extended: Boolean,
    onNewEventAt: (LocalDateTime, LocalDateTime) -> Unit
) {
    var minutes by rememberSaveable { mutableStateOf(60) }
    val slots = DashboardLogic.slots(occ, s, now, minutes, if (extended) 6 else 3)
    DashCardFrame("Find time") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 120).forEach { option ->
                val selected = option == minutes
                Text(
                    minutesToText(option.toLong()),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.12f))
                        .clickable { minutes = option }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (slots.isEmpty()) {
            SubText("No free slot of ${minutesToText(minutes.toLong())} in the next ${s.lookAhead} days, between ${minutesLabel(s.dayStart)} and ${minutesLabel(s.dayEnd)}")
        } else {
            slots.forEach { slot ->
                val end = slot.start.plusMinutes(slot.minutes.toLong())
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "New event at this time") { onNewEventAt(slot.start, end) }.padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Dot(FreeColor)
                    Spacer(Modifier.width(10.dp))
                    Text("${dateShort(slot.start.toLocalDate())}, ${span(slot.start, end)}", fontSize = 16.sp)
                }
            }
            SubText("Tap a time to make an event")
        }
    }
}

private fun minutesLabel(minutes: Int): String = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)

@Composable
private fun AllDayCard(occ: List<Occurrence>, s: DashboardSettings, today: LocalDate, extended: Boolean, onOpen: (Occurrence) -> Unit) {
    val last = today.plusDays(s.allDayAhead.toLong())
    val list = occ.filter { it.allDay && !it.lastDate.isBefore(today) && !it.startDate.isAfter(last) }.sortedBy { it.start }
    DashCardFrame("All-day coming up") {
        if (list.isEmpty()) {
            BigText("Nothing in the next ${s.allDayAhead} days")
        } else {
            list.take(if (extended) 10 else 3).forEach { o ->
                val days = ChronoUnit.DAYS.between(today, o.startDate)
                val detail = when {
                    !o.startDate.isAfter(today) -> if (o.multiDay) "Today, until ${dateShort(o.lastDate)}" else "Today"
                    days == 1L -> "Tomorrow"
                    else -> "In $days days, ${dateShort(o.startDate)}"
                }
                EventLine(o, detail, onOpen)
            }
        }
    }
}

@Composable
private fun RemindersCard(occ: List<Occurrence>, today: LocalDate, extended: Boolean, onOpen: (Occurrence) -> Unit) {
    val list = occ.filter { it.startDate == today && it.reminders.isNotEmpty() }.sortedBy { it.start }
    DashCardFrame("Reminders today") {
        if (list.isEmpty()) {
            BigText("No reminders today")
        } else {
            BigText(if (list.size == 1) "1 event with a reminder" else "${list.size} events with a reminder")
            if (extended) {
                Spacer(Modifier.height(4.dp))
                list.forEach { o -> EventLine(o, if (o.allDay) "All day" else "Starts at ${time(o.start)}", onOpen) }
            }
        }
    }
}

private fun syncText(calendar: CalendarEntity, clock: ClockState): String {
    if (calendar.lastSync <= 0L) return "Not updated yet"
    val at = Instant.ofEpochSecond(calendar.lastSync).atZone(clock.zone).toLocalDateTime()
    return "Updated " + if (at.toLocalDate() == clock.today) time(at) else dayAndTime(at)
}

@Composable
private fun SyncCard(calendars: List<CalendarEntity>, syncing: Set<Long>, clock: ClockState, extended: Boolean, viewModel: CalendarViewModel) {
    // Only calendars that come from a link or a CalDAV account can be out of date
    val followed = calendars.filter { it.sourceUrl != null || it.accountId != null }
    if (followed.isEmpty()) return
    val errors = followed.filter { it.lastError != null }
    DashCardFrame("Sync") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                when {
                    followed.any { it.id in syncing } -> BigText("Updating...")
                    errors.isEmpty() -> StatusLine(true, "All up to date")
                    else -> StatusLine(false, if (errors.size == 1) "1 calendar with an error" else "${errors.size} calendars with an error")
                }
            }
            IconButton(onClick = { followed.forEach { viewModel.refresh(it.id) } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Update all")
            }
        }
        if (extended) {
            followed.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(c.color))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 16.sp, maxLines = 1)
                        val error = c.lastError
                        if (error != null) Text(error, fontSize = 13.sp, color = BusyColor)
                        else Text(syncText(c, clock), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.refresh(c.id) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Update ${c.name}")
                    }
                }
            }
        }
    }
}
