package org.nooklin.calendar.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The widgets differ only in what [WidgetUpdater] draws. */
abstract class CalendarWidgetProvider(private val type: WidgetType) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.update(app, type, ids)
                WidgetUpdater.scheduleMidnight(app)
            } finally {
                pending.finish()
            }
        }
    }

    // A widget that is made bigger or smaller is drawn again for its new size
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onEnabled(context: Context) {
        // The first widget of this kind: the refresh every 15 minutes starts
        org.nooklin.calendar.sync.BackgroundSync.applyWidgets(context.applicationContext)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        // The last widget is gone: the alarm for midnight and the refresh are not needed any more
        WidgetUpdater.scheduleMidnight(context.applicationContext)
        org.nooklin.calendar.sync.BackgroundSync.applyWidgets(context.applicationContext)
    }
}

class TodayWidgetProvider : CalendarWidgetProvider(WidgetType.TODAY)
class WeekWidgetProvider : CalendarWidgetProvider(WidgetType.WEEK)
class MonthWidgetProvider : CalendarWidgetProvider(WidgetType.MONTH)
class DateWidgetProvider : CalendarWidgetProvider(WidgetType.DATE)
class UpNextSmallWidgetProvider : CalendarWidgetProvider(WidgetType.UPNEXT_SMALL)
class UpNextMediumWidgetProvider : CalendarWidgetProvider(WidgetType.UPNEXT_MEDIUM)
class PlannerWidgetProvider : CalendarWidgetProvider(WidgetType.PLANNER)
class ListWidgetProvider : CalendarWidgetProvider(WidgetType.LIST)
class DaysWidgetProvider : CalendarWidgetProvider(WidgetType.DAYS)
