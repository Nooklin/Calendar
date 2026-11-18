package org.nooklin.calendar.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.nooklin.calendar.data.CalendarRepository
import org.nooklin.calendar.reminder.ReminderScheduler
import org.nooklin.calendar.widget.WidgetUpdater

/** Refreshes the followed calendars in the background, and keeps the widgets and the reminders up to date. */
object BackgroundSync {
    private const val SYNC_WORK = "background_sync"
    private const val WIDGET_WORK = "widget_refresh"

    /** The choices for how often, in minutes. Android does not run background work more often than every 15 minutes. */
    val INTERVALS: List<Int> = listOf(15, 30, 60, 120, 360, 1440)

    fun label(minutes: Int): String = when {
        minutes < 60 -> "Every $minutes minutes"
        minutes == 60 -> "Every hour"
        minutes < 1440 -> "Every ${minutes / 60} hours"
        else -> "Every day"
    }

    /** Sets the work for the followed calendars as the settings say, or removes it when it is off. */
    fun applySync(context: Context, on: Boolean, minutes: Int, wifiOnly: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!on) {
            manager.cancelUniqueWork(SYNC_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<SyncWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // UPDATE: a changed setting replaces the work that was planned before, without waiting for the old time
        manager.enqueueUniquePeriodicWork(SYNC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * The widgets are drawn again every 15 minutes while there is a widget, so an event that has started or ended
     * shows right, also when the app is not opened. Together with the alarm at midnight and the draw after every
     * change this keeps them up to date.
     */
    fun applyWidgets(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (!WidgetUpdater.hasWidgets(context)) {
            manager.cancelUniqueWork(WIDGET_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<WidgetWorker>(15, TimeUnit.MINUTES).build()
        manager.enqueueUniquePeriodicWork(WIDGET_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

/** One round of the background sync. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = CalendarRepository(applicationContext)
        // One calendar that fails is noted on that calendar by the refresh; the others still go on
        repository.subscriptions().forEach { calendar ->
            try {
                repository.refresh(calendar.id)
            } catch (e: Exception) {
                // The next round tries again
            }
        }
        WidgetUpdater.updateAll(applicationContext)
        ReminderScheduler.schedule(applicationContext)
        return Result.success()
    }
}

/** Draws the widgets again. */
class WidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.updateAll(applicationContext)
        WidgetUpdater.scheduleMidnight(applicationContext)
        return Result.success()
    }
}
