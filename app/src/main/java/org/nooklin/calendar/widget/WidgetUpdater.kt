package org.nooklin.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.TimeZone
import kotlinx.coroutines.flow.first
import org.nooklin.calendar.LaunchRequest
import org.nooklin.calendar.MainActivity
import org.nooklin.calendar.R
import org.nooklin.calendar.data.CalendarRepository
import org.nooklin.calendar.data.local.AppPreferences
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.ui.calendar.dayTitle
import org.nooklin.calendar.ui.calendar.monthTitle
import org.nooklin.calendar.ui.calendar.shortName
import org.nooklin.calendar.ui.calendar.startOfWeek
import org.nooklin.calendar.ui.calendar.timeFormatter
import org.nooklin.calendar.ui.calendar.weekFieldsFor
import org.nooklin.calendar.ui.calendar.weekdayOrder

/** Where a widget gets its events: the calendar, or the example events of the previews. */
fun interface WidgetSource {
    suspend fun load(from: LocalDate, to: LocalDate, zone: ZoneId): List<Occurrence>
}

enum class WidgetType(val provider: Class<*>) {
    TODAY(TodayWidgetProvider::class.java),
    WEEK(WeekWidgetProvider::class.java),
    MONTH(MonthWidgetProvider::class.java),
    DATE(DateWidgetProvider::class.java),
    UPNEXT_SMALL(UpNextSmallWidgetProvider::class.java),
    UPNEXT_MEDIUM(UpNextMediumWidgetProvider::class.java),
    PLANNER(PlannerWidgetProvider::class.java),
    DAYS(DaysWidgetProvider::class.java),
    LIST(ListWidgetProvider::class.java)
}

/**
 * Draws the nine widgets from the database. Only the data of the calendar goes into a widget, and nothing is sent anywhere.
 * The widgets are drawn again when an event or a calendar changes, and at midnight.
 */
object WidgetUpdater {
    private const val MIDNIGHT_REQUEST = 2
    private const val TODAY_ROWS = 6

    private val MONTH_CELLS = intArrayOf(
        R.id.month_cell_0, R.id.month_cell_1, R.id.month_cell_2, R.id.month_cell_3, R.id.month_cell_4, R.id.month_cell_5, R.id.month_cell_6, R.id.month_cell_7, R.id.month_cell_8, R.id.month_cell_9, R.id.month_cell_10, R.id.month_cell_11, R.id.month_cell_12, R.id.month_cell_13, R.id.month_cell_14, R.id.month_cell_15, R.id.month_cell_16, R.id.month_cell_17, R.id.month_cell_18, R.id.month_cell_19, R.id.month_cell_20, R.id.month_cell_21, R.id.month_cell_22, R.id.month_cell_23, R.id.month_cell_24, R.id.month_cell_25, R.id.month_cell_26, R.id.month_cell_27, R.id.month_cell_28, R.id.month_cell_29, R.id.month_cell_30, R.id.month_cell_31, R.id.month_cell_32, R.id.month_cell_33, R.id.month_cell_34, R.id.month_cell_35, R.id.month_cell_36, R.id.month_cell_37, R.id.month_cell_38, R.id.month_cell_39, R.id.month_cell_40, R.id.month_cell_41
    )
    private val MONTH_DAYS = intArrayOf(
        R.id.month_day_0, R.id.month_day_1, R.id.month_day_2, R.id.month_day_3, R.id.month_day_4, R.id.month_day_5, R.id.month_day_6, R.id.month_day_7, R.id.month_day_8, R.id.month_day_9, R.id.month_day_10, R.id.month_day_11, R.id.month_day_12, R.id.month_day_13, R.id.month_day_14, R.id.month_day_15, R.id.month_day_16, R.id.month_day_17, R.id.month_day_18, R.id.month_day_19, R.id.month_day_20, R.id.month_day_21, R.id.month_day_22, R.id.month_day_23, R.id.month_day_24, R.id.month_day_25, R.id.month_day_26, R.id.month_day_27, R.id.month_day_28, R.id.month_day_29, R.id.month_day_30, R.id.month_day_31, R.id.month_day_32, R.id.month_day_33, R.id.month_day_34, R.id.month_day_35, R.id.month_day_36, R.id.month_day_37, R.id.month_day_38, R.id.month_day_39, R.id.month_day_40, R.id.month_day_41
    )
    private val MONTH_DOTS = intArrayOf(
        R.id.month_dots_0, R.id.month_dots_1, R.id.month_dots_2, R.id.month_dots_3, R.id.month_dots_4, R.id.month_dots_5, R.id.month_dots_6, R.id.month_dots_7, R.id.month_dots_8, R.id.month_dots_9, R.id.month_dots_10, R.id.month_dots_11, R.id.month_dots_12, R.id.month_dots_13, R.id.month_dots_14, R.id.month_dots_15, R.id.month_dots_16, R.id.month_dots_17, R.id.month_dots_18, R.id.month_dots_19, R.id.month_dots_20, R.id.month_dots_21, R.id.month_dots_22, R.id.month_dots_23, R.id.month_dots_24, R.id.month_dots_25, R.id.month_dots_26, R.id.month_dots_27, R.id.month_dots_28, R.id.month_dots_29, R.id.month_dots_30, R.id.month_dots_31, R.id.month_dots_32, R.id.month_dots_33, R.id.month_dots_34, R.id.month_dots_35, R.id.month_dots_36, R.id.month_dots_37, R.id.month_dots_38, R.id.month_dots_39, R.id.month_dots_40, R.id.month_dots_41
    )

    suspend fun updateAll(context: Context) {
        WidgetType.entries.forEach { update(context, it, null) }
    }

    fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return WidgetType.entries.any { manager.getAppWidgetIds(ComponentName(context, it.provider)).isNotEmpty() }
    }

    suspend fun update(context: Context, type: WidgetType, ids: IntArray?) {
        val manager = AppWidgetManager.getInstance(context)
        val targets = ids ?: manager.getAppWidgetIds(ComponentName(context, type.provider))
        if (targets.isEmpty()) return
        // The default zone is cached in a running process, and the phone may have moved to another one
        TimeZone.setDefault(null)
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val weekFields = weekFieldsFor(AppPreferences(context).weekStart.first())
        val repository = CalendarRepository(context)
        val source = WidgetSource { from, to, z -> repository.occurrences(from, to, z).first() }
        if (type == WidgetType.PLANNER) {
            // The planner depends on its width, so each one is drawn for its own size
            targets.forEach { id ->
                val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                manager.updateAppWidget(id, build(context, type, source, now, zone, weekFields, if (width > 0) width else 300))
            }
            return
        }
        manager.updateAppWidget(targets, build(context, type, source, now, zone, weekFields, 300))
    }

    private suspend fun build(
        context: Context, type: WidgetType, source: WidgetSource, now: LocalDateTime, zone: ZoneId, weekFields: WeekFields, widthDp: Int
    ): RemoteViews = when (type) {
        WidgetType.TODAY -> today(context, source, now, zone)
        WidgetType.WEEK -> week(context, source, now.toLocalDate(), weekFields, zone)
        WidgetType.MONTH -> month(context, source, now.toLocalDate(), weekFields, zone)
        WidgetType.DATE -> date(context, now.toLocalDate())
        WidgetType.UPNEXT_SMALL -> UpcomingWidgets.small(context, source, now, zone)
        WidgetType.UPNEXT_MEDIUM -> UpcomingWidgets.medium(context, source, now, zone)
        WidgetType.PLANNER -> UpcomingWidgets.planner(context, source, now, zone, widthDp)
        WidgetType.DAYS -> UpcomingWidgets.daysResponsive(context, source, now, zone)
        WidgetType.LIST -> UpcomingWidgets.list(context, source, now, zone)
    }

    /**
     * The list where a widget is chosen shows the fixed preview layouts with example events (res/layout/widget_preview_*).
     * An older version of this app set previews itself, which the system allows only a few times an hour; those are
     * removed here (Android 15 and newer), so every widget shows its fixed preview.
     */
    fun clearPreviews(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val manager = AppWidgetManager.getInstance(context)
        WidgetType.entries.forEach { type ->
            try {
                manager.removeWidgetPreview(ComponentName(context, type.provider), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            } catch (e: Exception) {
                // A preview that cannot be removed is not a reason to stop: the widget itself still works
            }
        }
    }

    // Section: today

    private suspend fun today(context: Context, source: WidgetSource, now: LocalDateTime, zone: ZoneId): RemoteViews {
        val day = now.toLocalDate()
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.today_header, dayTitle(day))
        // What is over is left out; all day events stay
        val list = source.load(day, day.plusDays(1), zone)
            .filter { it.allDay || it.multiDay || it.end > now }
            .sortedWith(compareByDescending<Occurrence> { it.allDay || it.multiDay }.thenBy { it.start })
        val rows = intArrayOf(R.id.today_row_0, R.id.today_row_1, R.id.today_row_2, R.id.today_row_3, R.id.today_row_4, R.id.today_row_5)
        val shown = if (list.size <= TODAY_ROWS) list else list.take(TODAY_ROWS - 1)
        rows.forEachIndexed { index, id ->
            val text: CharSequence? = when {
                list.isEmpty() && index == 0 -> "No events today"
                index < shown.size -> eventLine(shown[index])
                index == shown.size && list.size > shown.size -> "+${list.size - shown.size} more"
                else -> null
            }
            if (text == null) {
                views.setViewVisibility(id, View.GONE)
            } else {
                views.setViewVisibility(id, View.VISIBLE)
                views.setTextViewText(id, text)
            }
        }
        views.setOnClickPendingIntent(R.id.widget_root, openDay(context, day))
        return views
    }

    private fun eventLine(o: Occurrence): CharSequence {
        val builder = SpannableStringBuilder()
        builder.append("● ")
        builder.setSpan(ForegroundColorSpan(o.color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val label = if (o.allDay || o.multiDay) "All day" else o.start.toLocalTime().format(timeFormatter)
        builder.append(label).append("  ").append(o.title.ifEmpty { "(No title)" })
        return builder
    }

    // Section: week

    private suspend fun week(
        context: Context, source: WidgetSource, today: LocalDate, weekFields: WeekFields, zone: ZoneId
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_week)
        val start = today.startOfWeek(weekFields)
        views.setTextViewText(R.id.week_header, monthTitle(today))
        val occurrences = source.load(start, start.plusDays(7), zone)
        val cells = intArrayOf(R.id.week_cell_0, R.id.week_cell_1, R.id.week_cell_2, R.id.week_cell_3, R.id.week_cell_4, R.id.week_cell_5, R.id.week_cell_6)
        val dows = intArrayOf(R.id.week_dow_0, R.id.week_dow_1, R.id.week_dow_2, R.id.week_dow_3, R.id.week_dow_4, R.id.week_dow_5, R.id.week_dow_6)
        val numbers = intArrayOf(R.id.week_day_0, R.id.week_day_1, R.id.week_day_2, R.id.week_day_3, R.id.week_day_4, R.id.week_day_5, R.id.week_day_6)
        val dots = intArrayOf(R.id.week_dots_0, R.id.week_dots_1, R.id.week_dots_2, R.id.week_dots_3, R.id.week_dots_4, R.id.week_dots_5, R.id.week_dots_6)
        for (i in 0 until 7) {
            val day = start.plusDays(i.toLong())
            views.setTextViewText(dows[i], day.dayOfWeek.shortName().take(1).uppercase())
            dayNumber(context, views, numbers[i], day, day == today, false)
            views.setTextViewText(dots[i], dotsFor(occurrences.filter { it.touches(day) }))
            views.setOnClickPendingIntent(cells[i], openDay(context, day))
        }
        views.setOnClickPendingIntent(R.id.week_header, openDay(context, today))
        return views
    }

    // Section: month

    private suspend fun month(
        context: Context, source: WidgetSource, today: LocalDate, weekFields: WeekFields, zone: ZoneId
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_month)
        val month = YearMonth.from(today)
        val start = month.atDay(1).startOfWeek(weekFields)
        views.setTextViewText(R.id.month_header, monthTitle(today))
        val dows = intArrayOf(R.id.month_dow_0, R.id.month_dow_1, R.id.month_dow_2, R.id.month_dow_3, R.id.month_dow_4, R.id.month_dow_5, R.id.month_dow_6)
        weekdayOrder(weekFields).forEachIndexed { i, d -> views.setTextViewText(dows[i], d.shortName().take(1).uppercase()) }
        val occurrences = source.load(start, start.plusDays(42), zone)
        for (k in 0 until 42) {
            val day = start.plusDays(k.toLong())
            val cell = MONTH_CELLS[k]
            val number = MONTH_DAYS[k]
            val dotsId = MONTH_DOTS[k]
            if (YearMonth.from(day) != month) {
                // Days of the month before and after stay empty
                views.setTextViewText(number, "")
                views.setInt(number, "setBackgroundResource", 0)
                views.setTextViewText(dotsId, "")
                continue
            }
            dayNumber(context, views, number, day, day == today, true)
            views.setTextViewText(dotsId, dotsFor(occurrences.filter { it.touches(day) }))
            views.setOnClickPendingIntent(cell, openDay(context, day))
        }
        views.setOnClickPendingIntent(R.id.month_header, openDay(context, today))
        return views
    }

    // Section: date

    private fun date(context: Context, day: LocalDate): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_date)
        views.setTextViewText(R.id.date_weekday, day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()).uppercase())
        views.setTextViewText(R.id.date_day, day.dayOfMonth.toString())
        views.setTextViewText(R.id.date_month, day.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()))
        views.setOnClickPendingIntent(R.id.widget_root, openDay(context, day))
        return views
    }

    // Section: shared

    // Today gets a filled circle with the colours of the background, the other days plain text
    private fun dayNumber(context: Context, views: RemoteViews, id: Int, day: LocalDate, isToday: Boolean, dimWeekend: Boolean) {
        views.setTextViewText(id, day.dayOfMonth.toString())
        if (isToday) {
            views.setInt(id, "setBackgroundResource", R.drawable.widget_today_circle)
            views.setTextColor(id, context.getColor(R.color.widget_bg))
        } else {
            views.setInt(id, "setBackgroundResource", 0)
            val weekend = day.dayOfWeek.value >= 6
            views.setTextColor(id, context.getColor(if (weekend && dimWeekend) R.color.widget_dim else R.color.widget_text))
        }
    }

    // One coloured dot per calendar that has an event that day, four at most; nothing when there is none
    private fun dotsFor(items: List<Occurrence>): CharSequence {
        val colors = items.map { it.color }.distinct().take(4)
        if (colors.isEmpty()) return " "
        val builder = SpannableStringBuilder()
        colors.forEach { color ->
            val at = builder.length
            builder.append("●")
            builder.setSpan(ForegroundColorSpan(color), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return builder
    }

    internal fun openDay(context: Context, day: LocalDate): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("nooklin-calendar://day/${day.toEpochDay()}"))
            .putExtra(LaunchRequest.EXTRA_EPOCH_DAY, day.toEpochDay())
        return PendingIntent.getActivity(
            context, day.toEpochDay().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // Section: midnight

    /** Sets an exact alarm for the next midnight, so the widgets change over to the new day, also in Doze. */
    fun scheduleMidnight(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = midnightIntent(context)
        if (!hasWidgets(context)) {
            alarms.cancel(pending)
            return
        }
        TimeZone.setDefault(null)
        val zone = ZoneId.systemDefault()
        val at = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 1000L
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun midnightIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, MIDNIGHT_REQUEST,
            Intent(context, WidgetReceiver::class.java).setAction(WidgetReceiver.ACTION_MIDNIGHT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
