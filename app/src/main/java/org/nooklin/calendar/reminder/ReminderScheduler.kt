package org.nooklin.calendar.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import org.nooklin.calendar.data.db.CalendarDatabase

/**
 * Keeps one exact alarm set for the next reminder. When it goes off, [process] shows everything that is due and
 * [schedule] sets the next one. The point in time up to which reminders were handled is kept in [PREFS].
 */
object ReminderScheduler {
    private const val PREFS = "reminders"
    private const val KEY_CHECKED = "checkedUntil"
    private const val KEY_SNOOZES = "snoozes"
    private const val ALARM_REQUEST = 1
    // A reminder that is later than this when the phone wakes up (switched off, for example) is not shown any more
    private val MAX_LATE: Duration = Duration.ofHours(6)
    private val SNOOZE: Duration = Duration.ofMinutes(10)

    private fun zone(): ZoneId {
        // The default zone is cached in a running process, and the phone may have moved to another one
        TimeZone.setDefault(null)
        return ZoneId.systemDefault()
    }

    private fun checkedUntil(context: Context, now: Instant): Instant {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_CHECKED, now.toEpochMilli())
        // The clock was set back: reminders after the new time have to fire again
        return minOf(Instant.ofEpochMilli(stored), now)
    }

    private fun setChecked(context: Context, at: Instant) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(KEY_CHECKED, at.toEpochMilli()).apply()
    }

    /** Shows the reminders that became due since the last time. */
    suspend fun process(context: Context) {
        val now = Instant.now()
        val after = maxOf(checkedUntil(context, now), now.minus(MAX_LATE))
        val db = CalendarDatabase.get(context)
        val due = ReminderPlanner.triggers(db.eventDao().withReminders(), db.calendarDao().getAll(), after, now, zone())
        for (reminder in due) {
            ReminderNotifier.show(
                context,
                reminder.notificationId,
                reminder.title,
                ReminderNotifier.text(reminder.start, reminder.allDay, reminder.location)
            )
        }
        setChecked(context, now)
    }

    /** Marks everything up to now as handled, without showing it. Used after the user changed an event. */
    fun skipPast(context: Context) {
        setChecked(context, Instant.now())
    }

    /** Sets the alarm for the next reminder, or removes it when there is none in the coming year. */
    suspend fun schedule(context: Context) {
        val now = Instant.now()
        val after = checkedUntil(context, now)
        val db = CalendarDatabase.get(context)
        val events = db.eventDao().withReminders()
        val calendars = db.calendarDao().getAll()
        val zone = zone()
        var next: DueReminder? = null
        // Look two days ahead first, then a month, then more than a year: most of the time the next one is close
        for (days in listOf(2L, 32L, 400L)) {
            next = ReminderPlanner.triggers(events, calendars, after, now.plus(Duration.ofDays(days)), zone).firstOrNull()
            if (next != null) break
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, ALARM_REQUEST,
            ReminderReceiver.intent(context, ReminderReceiver.ACTION_ALARM, 0, "", ""),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (next == null) {
            alarms.cancel(pending)
        } else {
            setExact(alarms, next.trigger.toEpochMilli(), pending)
        }
    }

    /** Shows the notification again in ten minutes. The snooze is kept, so it survives a restart of the phone. */
    fun snooze(context: Context, id: Int, title: String, text: String) {
        val at = Instant.now().plus(SNOOZE).toEpochMilli()
        val list = readSnoozes(context).filter { it.getInt("id") != id }.toMutableList()
        list.add(JSONObject().put("id", id).put("at", at).put("title", title).put("text", text))
        writeSnoozes(context, list)
        setSnoozeAlarm(context, id, at, title, text)
    }

    /** Called when a snoozed reminder shows again. */
    fun snoozeDone(context: Context, id: Int) {
        writeSnoozes(context, readSnoozes(context).filter { it.getInt("id") != id })
    }

    /** After a restart or an update of the app the alarms are gone: set the snoozes again. */
    fun restoreSnoozes(context: Context) {
        readSnoozes(context).forEach {
            setSnoozeAlarm(context, it.getInt("id"), it.getLong("at"), it.getString("title"), it.getString("text"))
        }
    }

    private fun setSnoozeAlarm(context: Context, id: Int, at: Long, title: String, text: String) {
        val pending = PendingIntent.getBroadcast(
            context, id,
            ReminderReceiver.intent(context, ReminderReceiver.ACTION_SNOOZE_FIRE, id, title, text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        setExact(context.getSystemService(AlarmManager::class.java), at, pending)
    }

    private fun setExact(alarms: AlarmManager, at: Long, pending: PendingIntent) {
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (e: SecurityException) {
            // Exact alarms were switched off for this app: an inexact one is better than none
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun readSnoozes(context: Context): List<JSONObject> {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SNOOZES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).map { array.getJSONObject(it) }
        }.getOrDefault(emptyList())
    }

    private fun writeSnoozes(context: Context, list: List<JSONObject>) {
        val array = JSONArray()
        list.forEach { array.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SNOOZES, array.toString()).apply()
    }
}
