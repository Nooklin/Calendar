package org.nooklin.calendar.ui.calendar

import org.nooklin.calendar.ui.common.EmptyState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.flow.filterNotNull
import org.nooklin.calendar.data.local.MonthDensity
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.common.ClockState
import org.nooklin.calendar.ui.common.LocalBottomOverlay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val BASE_YEAR = 1900
private const val MONTH_COUNT = 400 * 12
private val HEADER_HEIGHT = 26.dp
private val WEEK_NUMBER_WIDTH = 24.dp
private val WEEKDAY_HEIGHT = 28.dp
private const val OUT_MILLIS = 120
private const val IN_MILLIS = 220
private val SLIDE = 24.dp
private val TodayRed = Color(0xFFEE6A66)

// How far a finger has to travel sideways on the day list to change the day
private val SWIPE_DISTANCE = 56.dp

private fun monthIndex(date: LocalDate): Int = (date.year - BASE_YEAR) * 12 + date.monthValue - 1

private fun indexMonth(index: Int): YearMonth = YearMonth.of(BASE_YEAR + index / 12, index % 12 + 1)

// The header, the lines and six weeks of the List layout: every month section fills this height in List
private fun listGridHeight(): Dp = HEADER_HEIGHT + 1.dp + rowHeight(MonthDensity.LIST) * 6 + 5.dp

private fun rowHeight(density: MonthDensity): Dp = when (density) {
    MonthDensity.COMPACT -> 52.dp
    MonthDensity.STACKED -> 100.dp
    MonthDensity.DETAILS -> 120.dp
    MonthDensity.LIST -> 46.dp
}

/** The month that fills most of the top of the list: the one whose title and selected day follow the scrolling. */
private fun visibleMonthIndex(state: LazyListState): Int {
    val first = state.layoutInfo.visibleItemsInfo.firstOrNull() ?: return state.firstVisibleItemIndex
    val index = if (-first.offset > first.size / 2) first.index + 1 else first.index
    return index.coerceAtMost(MONTH_COUNT - 1)
}

/**
 * The month view: the months under each other in one list that scrolls on, with a line between the weeks.
 * [density] decides how much of a day shows. With List the events of the selected day show under the grid.
 */
@Composable
fun MonthView(
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    clock: ClockState,
    weekFields: WeekFields,
    showWeekNumbers: Boolean,
    density: MonthDensity,
    viewModel: CalendarViewModel,
    onNewEvent: () -> Unit,
    onOpenEvent: (Occurrence) -> Unit
) {
    val today = clock.today
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = monthIndex(selectedDate))
    val currentSelected by rememberUpdatedState(selectedDate)
    val currentToday by rememberUpdatedState(today)
    val measurer = rememberTextMeasurer(cacheSize = 512)

    // A new selected date (Today button, a month opened from the year view) moves the list
    LaunchedEffect(selectedDate) {
        val target = monthIndex(selectedDate)
        if (target != visibleMonthIndex(listState)) listState.animateScrollToItem(target)
    }
    // When the scrolling has stopped: today is selected when it is in that month, otherwise the first day
    LaunchedEffect(listState) {
        snapshotFlow { if (listState.isScrollInProgress) null else visibleMonthIndex(listState) }
            .filterNotNull()
            .collect { index ->
                if (monthIndex(currentSelected) != index) {
                    val month = indexMonth(index)
                    onSelectDate(if (YearMonth.from(currentToday) == month) currentToday else month.atDay(1))
                }
            }
    }

    val onDayClick: (LocalDate) -> Unit = { day ->
        if (density == MonthDensity.LIST) onSelectDate(day) else onOpenDay(day)
    }

    // The layout that is on screen. A switch slides the old one out, builds the new one while nothing shows,
    // and slides it in. Only alpha and offset animate, and those run in the draw step without a new layout.
    var shown by remember { mutableStateOf(density) }
    val fade = remember { Animatable(1f) }
    val offset = remember { Animatable(0f) }
    val slidePx = with(LocalDensity.current) { SLIDE.toPx() }
    LaunchedEffect(density) {
        if (shown != density) {
            coroutineScope {
                launch { offset.animateTo(-slidePx, tween(OUT_MILLIS)) }
                fade.animateTo(0f, tween(OUT_MILLIS))
            }
            shown = density
            if (density == MonthDensity.LIST) listState.scrollToItem(monthIndex(currentSelected))
            withFrameNanos { }
            withFrameNanos { }
            offset.snapTo(slidePx)
        }
        coroutineScope {
            launch { offset.animateTo(0f, tween(IN_MILLIS)) }
            fade.animateTo(1f, tween(IN_MILLIS))
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // In List the grid is exactly one month high and the day list fills the rest; otherwise the grid fills everything
        val gridHeight = if (shown == MonthDensity.LIST) listGridHeight() else maxHeight - (WEEKDAY_HEIGHT + 1.dp)
        val snap = rememberSnapFlingBehavior(listState)
        val normal = ScrollableDefaults.flingBehavior()
        Column(Modifier.fillMaxSize()) {
            // Weekday letters: they stay while the months scroll
            Row(Modifier.fillMaxWidth().height(WEEKDAY_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                if (showWeekNumbers) Spacer(Modifier.width(WEEK_NUMBER_WIDTH))
                weekdayOrder(weekFields).forEach { day ->
                    Text(
                        day.shortName().take(1).uppercase(),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        alpha = fade.value
                        translationY = offset.value
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(gridHeight),
                    flingBehavior = if (shown == MonthDensity.LIST) snap else normal
                ) {
                    items(count = MONTH_COUNT, key = { it }) { index ->
                        MonthSection(
                            month = indexMonth(index),
                            selectedDate = selectedDate,
                            onDayClick = onDayClick,
                            clock = clock,
                            weekFields = weekFields,
                            showWeekNumbers = showWeekNumbers,
                            density = shown,
                            measurer = measurer,
                            viewModel = viewModel
                        )
                    }
                    if (shown != MonthDensity.LIST) {
                        item(key = "overlay") { Spacer(Modifier.height(LocalBottomOverlay.current)) }
                    }
                }
                if (shown == MonthDensity.LIST) {
                    SelectedDayList(
                        date = selectedDate,
                        onSelectDate = onSelectDate,
                        clock = clock,
                        viewModel = viewModel,
                        onNewEvent = onNewEvent,
                        onOpenEvent = onOpenEvent,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** One month: its name above the column of its first day, then its weeks. Days of other months stay empty. */
@Composable
private fun MonthSection(
    month: YearMonth,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit,
    clock: ClockState,
    weekFields: WeekFields,
    showWeekNumbers: Boolean,
    density: MonthDensity,
    measurer: TextMeasurer,
    viewModel: CalendarViewModel
) {
    val first = month.atDay(1)
    val gridStart = first.startOfWeek(weekFields)
    val leading = (first.dayOfWeek.value - weekFields.firstDayOfWeek.value + 7) % 7
    val weeks = (leading + month.lengthOfMonth() + 6) / 7
    val occurrences by remember(month, clock.zone) {
        viewModel.occurrences(first, month.plusMonths(1).atDay(1), clock.zone)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val byDay = remember(occurrences, month) {
        (1..month.lengthOfMonth()).associate { dayOfMonth ->
            val day = month.atDay(dayOfMonth)
            day to occurrences.filter { it.touches(day) }
                .sortedWith(compareByDescending<Occurrence> { it.allDay || it.multiDay }.thenBy { it.start })
        }
    }
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    // In List every month is six weeks high, so the list can snap to a month
    val pad = if (density == MonthDensity.LIST) (rowHeight(MonthDensity.LIST) + 1.dp) * (6 - weeks) else 0.dp
    val monthName = month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(HEADER_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            if (showWeekNumbers) Spacer(Modifier.width(WEEK_NUMBER_WIDTH))
            Box(Modifier.fillMaxWidth()) {
                Text(
                    monthName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        HorizontalDivider(color = lineColor)
        MonthGrid(
            month = month,
            weeks = weeks,
            gridStart = gridStart,
            byDay = byDay,
            selectedDate = selectedDate,
            today = clock.today,
            density = density,
            showWeekNumbers = showWeekNumbers,
            weekFields = weekFields,
            measurer = measurer,
            lineColor = lineColor,
            onDayClick = onDayClick
        )
        Spacer(Modifier.height(pad))
    }
}

private class GridPalette(val text: Color, val subtle: Color, val primary: Color, val onPrimary: Color, val line: Color)

/**
 * The weeks of one month, drawn in one piece instead of a composable per day. That keeps a switch between layouts
 * and the scrolling light. A tap is turned into a day from its position, and TalkBack gets one action per day.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    weeks: Int,
    gridStart: LocalDate,
    byDay: Map<LocalDate, List<Occurrence>>,
    selectedDate: LocalDate,
    today: LocalDate,
    density: MonthDensity,
    showWeekNumbers: Boolean,
    weekFields: WeekFields,
    measurer: TextMeasurer,
    lineColor: Color,
    onDayClick: (LocalDate) -> Unit
) {
    val rowH = rowHeight(density)
    val totalHeight = rowH * weeks + 1.dp * (weeks - 1)
    val colors = MaterialTheme.colorScheme
    val palette = GridPalette(colors.onSurface, colors.onSurfaceVariant, colors.primary, colors.onPrimary, lineColor)
    val base = LocalTextStyle.current
    val currentClick by rememberUpdatedState(onDayClick)
    val actions = remember(month) {
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
        (1..month.lengthOfMonth()).map { number ->
            val day = month.atDay(number)
            CustomAccessibilityAction(day.format(format)) {
                currentClick(day)
                true
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(totalHeight)
            .semantics { customActions = actions }
            .pointerInput(month, weeks, density, showWeekNumbers) {
                detectTapGestures { point ->
                    val numberWidth = if (showWeekNumbers) WEEK_NUMBER_WIDTH.toPx() else 0f
                    val stride = (rowHeight(density) + 1.dp).toPx()
                    val column = ((point.x - numberWidth) / ((size.width - numberWidth) / 7f)).toInt()
                    val row = (point.y / stride).toInt()
                    if (point.x >= numberWidth && column in 0..6 && row in 0 until weeks) {
                        val day = gridStart.plusDays(row * 7L + column)
                        if (day.year == month.year && day.monthValue == month.monthValue) currentClick(day)
                    }
                }
            }
            .drawBehind {
                drawMonth(month, weeks, gridStart, byDay, selectedDate, today, density, showWeekNumbers, weekFields, measurer, base, palette)
            }
    )
}

private fun DrawScope.drawMonth(
    month: YearMonth,
    weeks: Int,
    gridStart: LocalDate,
    byDay: Map<LocalDate, List<Occurrence>>,
    selectedDate: LocalDate,
    today: LocalDate,
    mode: MonthDensity,
    showWeekNumbers: Boolean,
    weekFields: WeekFields,
    measurer: TextMeasurer,
    base: ComposeTextStyle,
    palette: GridPalette
) {
    val numberWidth = if (showWeekNumbers) WEEK_NUMBER_WIDTH.toPx() else 0f
    val cellWidth = (size.width - numberWidth) / 7f
    val rowHeightPx = rowHeight(mode).toPx()
    val lineWidth = 1.dp.toPx()
    val stride = rowHeightPx + lineWidth
    val circle = (if (mode == MonthDensity.COMPACT || mode == MonthDensity.LIST) 32.dp else 30.dp).toPx()
    val topPad = 2.dp.toPx()
    val contentOffset = topPad + circle + 3.dp.toPx()
    for (row in 0 until weeks) {
        val rowTop = row * stride
        val rowStart = gridStart.plusDays(row * 7L)
        if (showWeekNumbers) {
            val hasToday = today >= rowStart && today <= rowStart.plusDays(6)
            val number = measurer.measure(
                rowStart.weekNumber(weekFields).toString(),
                base.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (hasToday) TodayRed else palette.subtle)
            )
            drawText(number, topLeft = Offset((numberWidth - number.size.width) / 2f, rowTop + rowHeightPx - number.size.height - 2.dp.toPx()))
        }
        for (column in 0 until 7) {
            val day = rowStart.plusDays(column.toLong())
            if (day.year != month.year || day.monthValue != month.monthValue) continue
            val left = numberWidth + column * cellWidth
            val centerX = left + cellWidth / 2f
            val isToday = day == today
            val circleCenter = Offset(centerX, rowTop + topPad + circle / 2f)
            if (isToday) {
                drawCircle(palette.primary, circle / 2f, circleCenter)
            } else if (mode == MonthDensity.LIST && day == selectedDate) {
                // In dark mode the text is light, and a light circle needs more weight to show against the dark background
                drawCircle(palette.text.copy(alpha = if (palette.text.luminance() > 0.5f) 0.35f else 0.15f), circle / 2f, circleCenter)
            }
            val weekend = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY
            val numberColor = when {
                isToday -> palette.onPrimary
                weekend -> palette.text.copy(alpha = 0.55f)
                else -> palette.text
            }
            val dayNumber = measurer.measure(
                day.dayOfMonth.toString(),
                base.copy(fontSize = 17.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium, color = numberColor)
            )
            drawText(dayNumber, topLeft = Offset(centerX - dayNumber.size.width / 2f, circleCenter.y - dayNumber.size.height / 2f))
            val items = byDay[day].orEmpty()
            if (items.isEmpty()) continue
            val top = rowTop + contentOffset
            when (mode) {
                MonthDensity.COMPACT, MonthDensity.LIST -> drawMarks(items, centerX, top)
                MonthDensity.STACKED -> drawLines(items, left, cellWidth, top, false, measurer, base, palette)
                MonthDensity.DETAILS -> drawLines(items, left, cellWidth, top, true, measurer, base, palette)
            }
        }
        if (row < weeks - 1) drawRect(palette.line, Offset(0f, rowTop + rowHeightPx), Size(size.width, lineWidth))
    }
}

/** One event: a dot. More: a small bar, split in the colours of the calendars that have an event that day (three at most). */
private fun DrawScope.drawMarks(items: List<Occurrence>, centerX: Float, top: Float) {
    val height = 6.dp.toPx()
    if (items.size == 1) {
        drawCircle(Color(items[0].color), height / 2f, Offset(centerX, top + height / 2f))
        return
    }
    val colors = items.map { it.color }.distinct().take(3)
    val width = 26.dp.toPx()
    val left = centerX - width / 2f
    val path = Path().apply { addRoundRect(RoundRect(left, top, left + width, top + height, CornerRadius(height / 2f))) }
    clipPath(path) {
        val part = width / colors.size
        colors.forEachIndexed { index, color -> drawRect(Color(color), Offset(left + index * part, top), Size(part, height)) }
    }
}

/** Up to three events under each other, as a bar or with the title. With more, two show and "+N" counts the rest. */
private fun DrawScope.drawLines(
    items: List<Occurrence>,
    left: Float,
    cellWidth: Float,
    top: Float,
    withTitles: Boolean,
    measurer: TextMeasurer,
    base: ComposeTextStyle,
    palette: GridPalette
) {
    val shown = if (items.size <= 3) items.size else 2
    val inset = 2.dp.toPx()
    val gap = 3.dp.toPx()
    val barWidth = cellWidth - 2 * inset
    val titleStyle = base.copy(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        color = palette.text
    )
    var y = top
    items.take(shown).forEach { item ->
        val color = Color(item.color)
        if (withTitles) {
            val height = 18.dp.toPx()
            drawRoundRect(color.copy(alpha = 0.30f), Offset(left + inset, y), Size(barWidth, height), CornerRadius(5.dp.toPx()))
            val title = measurer.measure(item.title.ifEmpty { "(No title)" }, titleStyle, maxLines = 1, softWrap = false)
            clipRect(left + inset, y, left + inset + barWidth, y + height) {
                drawText(title, topLeft = Offset(left + inset + 4.dp.toPx(), y + (height - title.size.height) / 2f))
            }
            y += height + gap
        } else {
            val height = 8.dp.toPx()
            drawRoundRect(color, Offset(left + inset, y), Size(barWidth, height), CornerRadius(4.dp.toPx()))
            y += height + gap
        }
    }
    if (items.size > shown) {
        val more = measurer.measure("+${items.size - shown}", base.copy(fontSize = 11.sp, color = palette.subtle))
        drawText(more, topLeft = Offset(left + (cellWidth - more.size.width) / 2f, y))
    }
}

@Composable
private fun SelectedDayList(
    date: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    clock: ClockState,
    viewModel: CalendarViewModel,
    onNewEvent: () -> Unit,
    onOpenEvent: (Occurrence) -> Unit,
    modifier: Modifier
) {
    val occurrences by remember(date, clock.zone) {
        viewModel.occurrences(date, date.plusDays(1), clock.zone)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val sorted = remember(occurrences) {
        occurrences.sortedWith(compareByDescending<Occurrence> { it.allDay || it.multiDay }.thenBy { it.start })
    }
    // A swipe to the left goes to the next day, to the right to the day before. The month grid follows the selected day,
    // so on the last or first day of a month the month changes with it.
    val select by rememberUpdatedState(onSelectDate)
    val threshold = with(LocalDensity.current) { SWIPE_DISTANCE.toPx() }
    val swipe = Modifier.pointerInput(date) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragCancel = { total = 0f },
            onDragEnd = {
                if (total <= -threshold) select(date.plusDays(1)) else if (total >= threshold) select(date.minusDays(1))
            },
            onHorizontalDrag = { _, amount -> total += amount }
        )
    }
    LazyColumn(modifier.then(swipe)) {
        if (sorted.isEmpty()) {
            item(key = "empty") {
                Box(Modifier.fillParentMaxHeight(0.7f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EmptyState("No events", "New event", onNewEvent)
                }
            }
        } else {
            item(key = "title") {
                Text(
                    dayTitle(date),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                )
            }
        }
        items(sorted, key = { it.key }) { occurrence ->
            EventRow(occurrence, date) { onOpenEvent(occurrence) }
        }
        item(key = "overlay") { Spacer(Modifier.height(LocalBottomOverlay.current)) }
    }
}
