package org.nooklin.calendar.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.nooklin.calendar.MainActivity
import org.nooklin.calendar.data.local.AppPreferences
import org.nooklin.calendar.R

object ReminderNotifier {
    private const val CHANNEL_ID = "reminders"

    fun canShow(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun text(start: LocalDateTime, allDay: Boolean, location: String): String {
        val locale = Locale.getDefault()
        val when_ = if (allDay) {
            start.toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) + ", all day"
        } else {
            start.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
        }
        return if (location.isEmpty()) when_ else "$when_, $location"
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canShow(context)) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val snooze = action(context, ReminderReceiver.ACTION_SNOOZE, id, title, text)
        val dismiss = action(context, ReminderReceiver.ACTION_DISMISS, id, title, text)
        // Off: the lock screen only shows that there is a reminder, the title is for after unlocking
        val onLockScreen = runCatching {
            runBlocking { AppPreferences(context.applicationContext).lockScreenNotifications.first() }
        }.getOrDefault(false)
        val hidden = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Reminder")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifEmpty { "(No title)" })
            .setContentText(text)
            .setVisibility(if (onLockScreen) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Snooze 10 min", snooze)
            .addAction(0, "Dismiss", dismiss)
            .build()
        manager.notify(id, notification)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }

    private fun action(context: Context, action: String, id: Int, title: String, text: String): PendingIntent {
        val intent = ReminderReceiver.intent(context, action, id, title, text)
        // The action and the id make the request unique, so the two buttons of one notification stay apart
        return PendingIntent.getBroadcast(
            context, id * 31 + action.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
