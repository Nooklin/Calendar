package org.nooklin.calendar.ui.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.travelLabel
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.common.ClockState
import org.nooklin.calendar.ui.common.LocalBottomOverlay

private val HOUR_HEIGHT = 52.dp
private val GUTTER_WIDTH = 48.dp
private val TODAY_RED = Color(0xFFFF3B30)
private const val MIN_LAYOUT_MINUTES = 30
private const val MAX_ALL_DAY_ROWS = 3
private val BASE_DATE: LocalDate = LocalDate.of(1900, 1, 1)
private const val WEEK_PAGES = 400 * 53
private const val DAY_PAGES = 400 * 366

/** An event cut to one day: the minutes of that day it covers. */
private data class Segment(val occurrence: Occurrence, val startMinute: Int, val endMinute: Int)

/**
 * The week view (seven days per page) and the day view (one day per page): a time axis with the events of each
 * day, an all day row above it, and swiping to the next page. The scroll position of the time axis is shared by
 * the pages, so the hour you were looking at stays when you swipe.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimeGridView(
    daysPerPage: Int,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    clock: ClockState,
    weekFields: WeekFields,
    showWeekNumbers: Boolean,
    viewModel: CalendarViewModel,
    onOpenEvent: (Occurrence) -> Unit
) {
    val weekly = daysPerPage == 7
    val baseWeek = remember(weekFields) { BASE_DATE.startOfWeek(weekFields) }
    fun pageStart(page: Int): LocalDate = if (weekly) baseWeek.plusWeeks(page.toLong()) else BASE_DATE.plusDays(page.toLong())
    fun pageOf(date: LocalDate): Int =
        if (weekly) ChronoUnit.WEEKS.between(baseWeek, date.startOfWeek(weekFields)).toInt()
        else ChronoUnit.DAYS.between(BASE_DATE, date).toInt()

    val pageCount = if (weekly) WEEK_PAGES else DAY_PAGES
    val pagerState = rememberPagerState(initialPage = pageOf(selectedDate)) { pageCount }
    val currentSelected by rememberUpdatedState(selectedDate)

    // A new selected date (Today button, a day tapped in the month) moves the pager
    LaunchedEffect(selectedDate) {
        val target = pageOf(selectedDate)
        if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
    }
    // Swiping to another page moves the selected date along, on the same weekday
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (pageOf(currentSelected) != page) {
                val offset = if (weekly) ChronoUnit.DAYS.between(currentSelected.startOfWeek(weekFields), currentSelected) else 0L
                onSelectDate(pageStart(page).plusDays(offset))
            }
        }
    }

    val density = LocalDensity.current
    // Opens at 07:00, just above the start of most days
    val scrollState = rememberScrollState(with(density) { (HOUR_HEIGHT * 7).roundToPx() })

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
        val start = pageStart(page)
        val days = (0 until daysPerPage).map { start.plusDays(it.toLong()) }
        val occurrences by remember(start, daysPerPage, clock.zone) {
            viewModel.occurrences(start, start.plusDays(daysPerPage.toLong()), clock.zone)
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        TimeGridPage(
            days = days,
            occurrences = occurrences,
            clock = clock,
            weekFields = weekFields,
            showWeekNumber = showWeekNumbers && weekly,
            scrollState = scrollState,
            onOpenEvent = onOpenEvent
        )
    }
}

@Composable
private fun TimeGridPage(
    days: List<LocalDate>,
    occurrences: List<Occurrence>,
    clock: ClockState,
    weekFields: WeekFields,
    showWeekNumber: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    onOpenEvent: (Occurrence) -> Unit
) {
    val today = clock.today
    val allDayByDay = days.associateWith { day -> occurrences.filter { it.allDay && it.touches(day) } }
    val rows = minOf(MAX_ALL_DAY_ROWS, allDayByDay.values.maxOfOrNull { it.size } ?: 0)
    val hasOverflow = allDayByDay.values.any { it.size > MAX_ALL_DAY_ROWS }

    Column(Modifier.fillMaxSize()) {
        // Day names and numbers
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Box(Modifier.width(GUTTER_WIDTH), contentAlignment = Alignment.Center) {
                if (showWeekNumber) {
                    Text(
                        "W" + days.first().weekNumber(weekFields),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.dayOfWeek.shortName(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val isToday = day == today
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).then(if (isToday) Modifier.background(TODAY_RED) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // All day events
        if (rows > 0) {
            val rowCount = rows + if (hasOverflow) 1 else 0
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Box(Modifier.width(GUTTER_WIDTH).height(20.dp * rowCount), contentAlignment = Alignment.CenterEnd) {
                    Text("all day", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                }
                days.forEach { day ->
                    val list = allDayByDay.getValue(day)
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp)) {
                        val shown = if (list.size > MAX_ALL_DAY_ROWS) list.take(MAX_ALL_DAY_ROWS - 1) else list
                        shown.forEach { AllDayChip(it, onOpenEvent) }
                        if (list.size > shown.size) {
                            Text("+" + (list.size - shown.size), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))

        // The time axis
        val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        val hourLabels = remember { (0..23).map { LocalTime.of(it, 0) } }
        val formatter = timeFormatter
        Column(Modifier.weight(1f).verticalScroll(scrollState)) {
            Row(Modifier.fillMaxWidth().height(HOUR_HEIGHT * 24)) {
                Box(Modifier.width(GUTTER_WIDTH).fillMaxHeight()) {
                    hourLabels.forEachIndexed { hour, time ->
                        if (hour > 0) {
                            Text(
                                time.format(formatter),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End,
                                modifier = Modifier.width(GUTTER_WIDTH - 4.dp).offset(y = HOUR_HEIGHT * hour - 7.dp)
                            )
                        }
                    }
                }
                days.forEach { day ->
                    DayColumn(
                        day = day,
                        occurrences = occurrences,
                        clock = clock,
                        lineColor = lineColor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onOpenEvent = onOpenEvent
                    )
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(LocalBottomOverlay.current))
        }
    }
}

@Composable
private fun AllDayChip(occurrence: Occurrence, onOpenEvent: (Occurrence) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .padding(bottom = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(occurrence.color))
            .clickable { onOpenEvent(occurrence) }
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(occurrence.title, fontSize = 10.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DayColumn(
    day: LocalDate,
    occurrences: List<Occurrence>,
    clock: ClockState,
    lineColor: Color,
    modifier: Modifier,
    onOpenEvent: (Occurrence) -> Unit
) {
    val segments = remember(day, occurrences) { segmentsFor(day, occurrences) }
    val placed = remember(segments) {
        layoutLanes(segments, { it.startMinute }, { maxOf(it.endMinute, it.startMinute + MIN_LAYOUT_MINUTES) })
    }
    val hourHeightPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }
    BoxWithConstraints(
        modifier.drawBehind {
            // A line at every hour and one on the left of the day
            for (hour in 0..23) {
                val y = hourHeightPx * hour
                drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            drawLine(lineColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1f)
        }
    ) {
        val columnWidth = maxWidth
        placed.forEach { slot ->
            val segment = slot.item
            val laneWidth = columnWidth / slot.lanes
            val top = HOUR_HEIGHT * (segment.startMinute / 60f)
            val height = maxOf(HOUR_HEIGHT * ((segment.endMinute - segment.startMinute) / 60f) - 1.dp, 18.dp)
            val travel = segment.occurrence.travelMinutes
            if (travel > 0 && segment.occurrence.startDate == day) {
                val travelStart = maxOf(0, segment.startMinute - travel)
                val travelHeight = HOUR_HEIGHT * ((segment.startMinute - travelStart) / 60f)
                if (travelHeight > 0.dp) {
                    Box(
                        Modifier
                            .offset(x = laneWidth * slot.lane, y = HOUR_HEIGHT * (travelStart / 60f))
                            .width(laneWidth - 2.dp)
                            .height(travelHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(segment.occurrence.color).copy(alpha = 0.10f))
                    ) {
                        if (travelHeight >= 18.dp) {
                            Text(
                                "Travel " + travelLabel(travel),
                                fontSize = 9.sp,
                                color = Color(segment.occurrence.color),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            EventBlock(
                occurrence = segment.occurrence,
                modifier = Modifier
                    .offset(x = laneWidth * slot.lane, y = top)
                    .width(laneWidth - 2.dp)
                    .height(height),
                tall = height >= 34.dp,
                onClick = { onOpenEvent(segment.occurrence) }
            )
        }
        if (day == clock.today) {
            val nowMinute = clock.now.toLocalTime().toSecondOfDay() / 60f
            Box(
                Modifier
                    .offset(y = HOUR_HEIGHT * (nowMinute / 60f) - 1.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TODAY_RED)
            )
        }
    }
}

@Composable
private fun EventBlock(occurrence: Occurrence, modifier: Modifier, tall: Boolean, onClick: () -> Unit) {
    val color = Color(occurrence.color)
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.22f))
            .drawBehind { drawRect(color, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 2.dp, top = 1.dp)
    ) {
        Column {
            Text(
                occurrence.title.ifEmpty { "(No title)" },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (tall) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tall) {
                Text(
                    occurrence.start.toLocalTime().format(timeFormatter),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

// The events of one day as the minutes they cover. An event over midnight is cut at the midnight.
private fun segmentsFor(day: LocalDate, occurrences: List<Occurrence>): List<Segment> =
    occurrences.filter { !it.allDay && it.touches(day) }.map { o ->
        val startMinute = if (o.start.toLocalDate().isBefore(day)) 0 else o.start.toLocalTime().toSecondOfDay() / 60
        val endMinute = if (o.end.toLocalDate().isAfter(day)) 24 * 60 else o.end.toLocalTime().toSecondOfDay() / 60
        Segment(o, startMinute, maxOf(endMinute, startMinute))
    }
