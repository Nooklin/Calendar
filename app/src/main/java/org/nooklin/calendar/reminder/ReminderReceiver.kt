package org.nooklin.calendar.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the alarms of the reminders, the buttons of the notification and the system broadcasts that need a new schedule. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val id = intent.getIntExtra(EXTRA_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_DISMISS -> ReminderNotifier.cancel(app, id)
                    ACTION_SNOOZE -> {
                        ReminderNotifier.cancel(app, id)
                        ReminderScheduler.snooze(app, id, title, text)
                    }
                    ACTION_SNOOZE_FIRE -> {
                        ReminderScheduler.snoozeDone(app, id)
                        ReminderNotifier.show(app, id, title, text)
                    }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        ReminderScheduler.restoreSnoozes(app)
                        ReminderScheduler.process(app)
                        ReminderScheduler.schedule(app)
                    }
                    ACTION_ALARM, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                        ReminderScheduler.process(app)
                        ReminderScheduler.schedule(app)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ALARM = "org.nooklin.calendar.REMINDER_ALARM"
        const val ACTION_SNOOZE = "org.nooklin.calendar.REMINDER_SNOOZE"
        const val ACTION_DISMISS = "org.nooklin.calendar.REMINDER_DISMISS"
        const val ACTION_SNOOZE_FIRE = "org.nooklin.calendar.REMINDER_SNOOZE_FIRE"
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"

        fun intent(context: Context, action: String, id: Int, title: String, text: String): Intent =
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                // The data makes every action of every notification a different intent for the system
                .setData(Uri.parse("nooklin-calendar://reminder/$action/$id"))
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TEXT, text)
    }
}
