package org.nooklin.calendar.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import org.nooklin.calendar.R
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.travelLabel
import org.nooklin.calendar.ui.calendar.timeFormatter

/**
 * The four widgets that look ahead: up next in two sizes, the day planner with today and tomorrow, and the list.
 * Only the events of the calendar go into them. They are drawn again when an event changes and at midnight, like the others.
 */
object UpcomingWidgets {
    private const val LIST_ROWS = 9
    private const val LIST_DAYS = 14
    private const val LOOK_AHEAD_DAYS = 8

    // The planner shows eight hours per day, each HOUR_DP high; the line of an hour sits LINE_DP below its top
    private const val HOURS = 8
    private const val HOUR_DP = 24f
    private const val LINE_DP = 6f
    private const val LABEL_DP = 26f
    private const val MIN_BLOCK_DP = 20f

    private fun Occurrence.isBanner(): Boolean = allDay || multiDay

    private fun tint(color: Int): Int = (0x38 shl 24) or (color and 0x00FFFFFF)

    private fun paint(views: RemoteViews, background: Int, color: Int) {
        views.setColorStateList(background, "setBackgroundTintList", ColorStateList.valueOf(tint(color)))
    }

    private fun title(o: Occurrence): String = o.title.ifEmpty { "(No title)" }

    private fun weekdayName(day: LocalDate): String =
        day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase()

    private fun clock(o: Occurrence): String =
        o.start.toLocalTime().format(timeFormatter) + " - " + o.end.toLocalTime().format(timeFormatter)

    /** Events that are not over yet, all day events first. */
    private fun remaining(all: List<Occurrence>, day: LocalDate, now: LocalDateTime): List<Occurrence> =
        all.filter { it.touches(day) && (it.isBanner() || it.end > now) }
            .sortedWith(compareByDescending<Occurrence> { it.isBanner() }.thenBy { it.start })

    // Section: up next, small

    suspend fun small(context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId): RemoteViews {
        val day = now.toLocalDate()
        val views = RemoteViews(context.packageName, R.layout.widget_upnext_small)
        views.setTextViewText(R.id.up_weekday, weekdayName(day))
        views.setTextViewText(R.id.up_day, day.dayOfMonth.toString())
        val list = remaining(source.load(day, day.plusDays(1), zone), day, now).take(2)
        views.removeAllViews(R.id.up_events)
        views.setViewVisibility(R.id.up_empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        list.forEach { views.addView(R.id.up_events, eventBlock(context, it, false, null)) }
        views.setOnClickPendingIntent(R.id.widget_root, WidgetUpdater.openDay(context, day))
        return views
    }

    // The second line of an event: the location and the travel time
    private fun subLine(o: Occurrence): String = listOfNotNull(
        o.location.ifEmpty { null },
        if (o.travelMinutes > 0 && !o.allDay) "Travel " + travelLabel(o.travelMinutes) else null
    ).joinToString(" \u00B7 ")

    // One event with the colour of its calendar. [prefix] is the day, for an event that is not today.
    private fun eventBlock(context: Context, o: Occurrence, withPlace: Boolean, prefix: String?): RemoteViews {
        val block = RemoteViews(context.packageName, R.layout.widget_event_block)
        paint(block, R.id.blk_root, o.color)
        block.setInt(R.id.blk_bar, "setBackgroundColor", o.color)
        block.setTextViewText(R.id.blk_title, title(o))
        block.setTextColor(R.id.blk_title, o.color)
        if (withPlace && subLine(o).isNotEmpty()) {
            block.setViewVisibility(R.id.blk_sub, View.VISIBLE)
            block.setTextViewText(R.id.blk_sub, subLine(o))
            block.setTextColor(R.id.blk_sub, o.color)
        } else {
            block.setViewVisibility(R.id.blk_sub, View.GONE)
        }
        val time = if (o.isBanner()) "All day" else clock(o)
        block.setTextViewText(R.id.blk_time, if (prefix == null) time else "$prefix, $time")
        block.setTextColor(R.id.blk_time, o.color)
        block.setOnClickPendingIntent(R.id.blk_root, WidgetUpdater.openDay(context, o.startDate))
        return block
    }

    // Section: up next, wide

    suspend fun medium(context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId): RemoteViews {
        val day = now.toLocalDate()
        val views = RemoteViews(context.packageName, R.layout.widget_upnext_medium)
        views.setTextViewText(R.id.md_weekday, weekdayName(day))
        views.setTextViewText(R.id.md_day, day.dayOfMonth.toString())
        val all = source.load(day, day.plusDays(LOOK_AHEAD_DAYS.toLong()), zone)
        val banners = all.filter { it.isBanner() && it.touches(day) }.sortedBy { it.start }
        views.removeAllViews(R.id.md_banners)
        banners.take(2).forEach { views.addView(R.id.md_banners, bannerPill(context, bannerText(it, null), it.color, day)) }
        if (banners.size > 2) views.addView(R.id.md_banners, bannerPill(context, "+${banners.size - 2} more", banners[2].color, day))
        val upcoming = all.filter { !it.isBanner() && it.end > now }.sortedBy { it.start }.take(2)
        views.removeAllViews(R.id.md_events)
        views.setViewVisibility(R.id.md_empty, if (upcoming.isEmpty()) View.VISIBLE else View.GONE)
        upcoming.forEach { views.addView(R.id.md_events, eventBlock(context, it, true, dayPrefix(it.startDate, day))) }
        views.setOnClickPendingIntent(R.id.widget_root, WidgetUpdater.openDay(context, day))
        return views
    }

    private fun dayPrefix(date: LocalDate, today: LocalDate): String? = when (date) {
        today -> null
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    private fun bannerText(o: Occurrence, extra: Int?): String = if (extra == null || extra <= 0) title(o) else title(o) + "  +" + extra

    private fun bannerPill(context: Context, text: String, color: Int, day: LocalDate): RemoteViews {
        val pill = RemoteViews(context.packageName, R.layout.widget_banner_pill)
        paint(pill, R.id.pill_root, color)
        pill.setTextViewText(R.id.pill_title, text)
        pill.setTextColor(R.id.pill_title, color)
        pill.setOnClickPendingIntent(R.id.pill_root, WidgetUpdater.openDay(context, day))
        return pill
    }

    // Section: day planner

    /** [widthDp] is the width of the widget on the home screen, to give events that overlap half of a column each. */
    suspend fun planner(
        context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId, widthDp: Int
    ): RemoteViews {
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val views = RemoteViews(context.packageName, R.layout.widget_plan)
        views.setTextViewText(R.id.pl_weekday, weekdayName(today))
        views.setTextViewText(R.id.pl_day, today.dayOfMonth.toString())
        val all = source.load(today, tomorrow.plusDays(1), zone)
        // Page padding 12dp each side, 12dp between the columns, and the label of the hours in front of the events
        val area = ((widthDp - 24 - 12) / 2f - LABEL_DP).coerceAtLeast(40f)
        val timedToday = all.filter { !it.isBanner() && it.touches(today) }
        val timedTomorrow = all.filter { !it.isBanner() && it.touches(tomorrow) }
        val firstTomorrow = timedTomorrow.minOfOrNull { minutesOfDay(it, tomorrow, true) }?.div(60)
        val startToday = (now.hour - 1).coerceIn(0, 24 - HOURS)
        val startTomorrow = when {
            firstTomorrow == null -> 9
            firstTomorrow > 24 - HOURS -> 24 - HOURS
            else -> minOf(firstTomorrow, 9)
        }
        banner(context, views, R.id.pl_banner_left, all.filter { it.isBanner() && it.touches(today) }, today)
        banner(context, views, R.id.pl_banner_right, all.filter { it.isBanner() && it.touches(tomorrow) }, tomorrow)
        column(context, views, R.id.pl_hours_left, R.id.pl_events_left, today, startToday, timedToday, now, area)
        column(context, views, R.id.pl_hours_right, R.id.pl_events_right, tomorrow, startTomorrow, timedTomorrow, null, area)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetUpdater.openDay(context, today))
        return views
    }

    // The first all day event of the day, with how many more there are
    private fun banner(context: Context, views: RemoteViews, container: Int, list: List<Occurrence>, day: LocalDate) {
        views.removeAllViews(container)
        val first = list.minByOrNull { it.start } ?: return
        views.addView(container, bannerPill(context, bannerText(first, list.size - 1), first.color, day))
    }

    private fun minutesOfDay(o: Occurrence, day: LocalDate, start: Boolean): Int {
        val moment = if (start) o.start else o.end
        val date = moment.toLocalDate()
        return when {
            date.isBefore(day) -> 0
            date.isAfter(day) -> 24 * 60
            else -> moment.hour * 60 + moment.minute
        }
    }

    private fun column(
        context: Context, views: RemoteViews, hours: Int, events: Int, day: LocalDate, startHour: Int,
        list: List<Occurrence>, now: LocalDateTime?, areaDp: Float
    ) {
        views.removeAllViews(hours)
        for (h in startHour until startHour + HOURS) {
            val row = RemoteViews(context.packageName, R.layout.widget_plan_hour)
            row.setTextViewText(R.id.ph_label, "%02d".format(h))
            views.addView(hours, row)
        }
        views.removeAllViews(events)
        val windowStart = startHour * 60
        val windowEnd = (startHour + HOURS) * 60
        val visible = list.sortedBy { it.start }.filter {
            minutesOfDay(it, day, false) > windowStart && minutesOfDay(it, day, true) < windowEnd
        }
        // Two lanes: an event that starts while the one before is still going goes to the second lane
        val laneEnd = intArrayOf(0, 0)
        val laneOf = visible.map { o ->
            val from = minutesOfDay(o, day, true)
            val lane = if (laneEnd[0] <= from) 0 else if (laneEnd[1] <= from) 1 else 1
            laneEnd[lane] = minutesOfDay(o, day, false)
            lane
        }
        val shared = laneOf.any { it == 1 }
        val width = if (shared) (areaDp - 2f) / 2f else areaDp
        visible.forEachIndexed { index, o ->
            val from = maxOf(minutesOfDay(o, day, true), windowStart)
            val to = minOf(minutesOfDay(o, day, false), windowEnd)
            val top = LINE_DP + (from - windowStart) / 60f * HOUR_DP
            val height = maxOf((to - from) / 60f * HOUR_DP - 1f, MIN_BLOCK_DP)
            val block = RemoteViews(context.packageName, R.layout.widget_plan_block)
            paint(block, R.id.pb_root, o.color)
            block.setInt(R.id.pb_bar, "setBackgroundColor", o.color)
            block.setTextViewText(R.id.pb_title, title(o))
            block.setTextColor(R.id.pb_title, o.color)
            // A block of one hour or more has room for the time under the title
            if (height >= 34f) {
                block.setViewVisibility(R.id.pb_time, View.VISIBLE)
                block.setTextViewText(R.id.pb_time, o.start.toLocalTime().format(timeFormatter))
                block.setTextColor(R.id.pb_time, o.color)
            } else {
                block.setViewVisibility(R.id.pb_time, View.GONE)
            }
            val left = LABEL_DP + if (laneOf[index] == 1 && shared) width + 2f else 0f
            block.setViewLayoutWidth(R.id.pb_root, width, TypedValue.COMPLEX_UNIT_DIP)
            block.setViewLayoutHeight(R.id.pb_root, height, TypedValue.COMPLEX_UNIT_DIP)
            block.setViewLayoutMargin(R.id.pb_root, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP)
            block.setViewLayoutMargin(R.id.pb_root, RemoteViews.MARGIN_START, left, TypedValue.COMPLEX_UNIT_DIP)
            block.setOnClickPendingIntent(R.id.pb_root, WidgetUpdater.openDay(context, day))
            views.addView(events, block)
        }
        // The line of the time now, in the column of today
        if (now != null) {
            val minutes = now.hour * 60 + now.minute
            if (minutes in windowStart until windowEnd) {
                val line = RemoteViews(context.packageName, R.layout.widget_plan_now)
                val top = LINE_DP + (minutes - windowStart) / 60f * HOUR_DP - 3f
                line.setViewLayoutWidth(R.id.pn_root, areaDp + LABEL_DP - 4f, TypedValue.COMPLEX_UNIT_DIP)
                line.setViewLayoutMargin(R.id.pn_root, RemoteViews.MARGIN_TOP, top, TypedValue.COMPLEX_UNIT_DIP)
                line.setViewLayoutMargin(R.id.pn_root, RemoteViews.MARGIN_START, 2f, TypedValue.COMPLEX_UNIT_DIP)
                views.addView(events, line)
            }
        }
    }

    // Section: today and tomorrow

    /**
     * The widget of today and tomorrow in one layout for every height, from one event to seven under each day. The
     * launcher picks the one that fits by itself when the widget is made bigger or smaller, so there is no empty
     * space left and no wait for a new drawing.
     */
    suspend fun daysResponsive(context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId): RemoteViews {
        val sizes = (1..7).associate { rows -> SizeF(100f, 44f + 34f * rows) to days(context, source, now, zone, (44 + 34 * rows)) }
        return RemoteViews(sizes)
    }

    /** [heightDp] is the height of the widget on the home screen: it decides how many events fit under each day. */
    suspend fun days(context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId, heightDp: Int): RemoteViews {
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val views = RemoteViews(context.packageName, R.layout.widget_days)
        val all = source.load(today, tomorrow.plusDays(1), zone)
        // Padding 12dp above and below, the heading of 20dp, and 34dp for an event
        val rows = ((heightDp - 24 - 20) / 34f).toInt().coerceIn(1, 7)
        val left = remaining(all, today, now)
        val right = remaining(all, tomorrow, tomorrow.atStartOfDay())
        // What does not fit is counted in the heading
        views.setTextViewText(R.id.dy_head_left, "TODAY  " + today.dayOfMonth + moreText(left.size - rows))
        views.setTextViewText(R.id.dy_head_right, "TOMORROW  " + tomorrow.dayOfMonth + moreText(right.size - rows))
        dayColumn(context, views, R.id.dy_events_left, R.id.dy_empty_left, today, left, rows)
        dayColumn(context, views, R.id.dy_events_right, R.id.dy_empty_right, tomorrow, right, rows)
        views.setOnClickPendingIntent(R.id.dy_head_left, WidgetUpdater.openDay(context, today))
        views.setOnClickPendingIntent(R.id.dy_head_right, WidgetUpdater.openDay(context, tomorrow))
        return views
    }

    private fun moreText(hidden: Int): String = if (hidden > 0) "  +$hidden" else ""

    private fun dayColumn(context: Context, views: RemoteViews, container: Int, empty: Int, day: LocalDate, list: List<Occurrence>, rows: Int) {
        views.removeAllViews(container)
        views.setViewVisibility(empty, if (list.isEmpty()) View.VISIBLE else View.GONE)
        list.take(rows).forEach { views.addView(container, dayRow(context, it, day)) }
    }

    private fun dayRow(context: Context, o: Occurrence, day: LocalDate): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_day_event)
        paint(row, R.id.dy_root, o.color)
        row.setInt(R.id.dy_bar, "setBackgroundColor", o.color)
        row.setTextViewText(R.id.dy_title, title(o))
        row.setTextColor(R.id.dy_title, o.color)
        row.setTextViewText(R.id.dy_time, if (o.isBanner()) "All day" else clock(o))
        row.setTextColor(R.id.dy_time, o.color)
        row.setOnClickPendingIntent(R.id.dy_root, WidgetUpdater.openDay(context, day))
        return row
    }

    // Section: list

    suspend fun list(context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId): RemoteViews {
        val today = now.toLocalDate()
        val views = RemoteViews(context.packageName, R.layout.widget_list)
        val all = source.load(today, today.plusDays(LIST_DAYS.toLong()), zone)
        val format = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault())
        views.removeAllViews(R.id.ls_events)
        var rows = 0
        var day = today
        while (day < today.plusDays(LIST_DAYS.toLong()) && rows < LIST_ROWS) {
            val events = remaining(all, day, now.takeIf { day == today } ?: day.atStartOfDay())
            if (events.isNotEmpty()) {
                val header = RemoteViews(context.packageName, R.layout.widget_list_header)
                header.setTextViewText(R.id.lh_text, day.format(format).uppercase())
                views.addView(R.id.ls_events, header)
                rows++
                for (event in events) {
                    if (rows >= LIST_ROWS) break
                    views.addView(R.id.ls_events, listRow(context, event, day))
                    rows++
                }
            }
            day = day.plusDays(1)
        }
        views.setViewVisibility(R.id.ls_empty, if (rows == 0) View.VISIBLE else View.GONE)
        views.setOnClickPendingIntent(R.id.widget_root, WidgetUpdater.openDay(context, today))
        return views
    }

    private fun listRow(context: Context, o: Occurrence, day: LocalDate): RemoteViews {
        val row = RemoteViews(context.packageName, R.layout.widget_list_event)
        paint(row, R.id.le_root, o.color)
        row.setInt(R.id.le_bar, "setBackgroundColor", o.color)
        row.setTextViewText(R.id.le_title, title(o))
        row.setTextColor(R.id.le_title, o.color)
        if (subLine(o).isNotEmpty()) {
            row.setViewVisibility(R.id.le_sub, View.VISIBLE)
            row.setTextViewText(R.id.le_sub, subLine(o))
            row.setTextColor(R.id.le_sub, o.color)
        } else {
            row.setViewVisibility(R.id.le_sub, View.GONE)
        }
        if (o.isBanner()) {
            row.setTextViewText(R.id.le_start, "all-day")
            row.setViewVisibility(R.id.le_end, View.GONE)
        } else {
            row.setTextViewText(R.id.le_start, o.start.toLocalTime().format(timeFormatter))
            row.setTextViewText(R.id.le_end, o.end.toLocalTime().format(timeFormatter))
            row.setViewVisibility(R.id.le_end, View.VISIBLE)
        }
        row.setTextColor(R.id.le_start, o.color)
        row.setTextColor(R.id.le_end, o.color)
        row.setOnClickPendingIntent(R.id.le_root, WidgetUpdater.openDay(context, day))
        return row
    }
}
