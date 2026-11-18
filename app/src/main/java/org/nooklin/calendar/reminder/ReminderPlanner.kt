package org.nooklin.calendar.reminder

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.model.expandEvent

/** One reminder that has to show at [trigger]. [start] is the start of the event in the zone of the phone. */
data class DueReminder(
    val eventId: Long,
    val title: String,
    val location: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val minutes: Int,
    val trigger: Instant
) {
    /** The same reminder always gets the same number, so showing it twice replaces the first one. */
    val notificationId: Int get() = "$eventId@$start@$minutes".hashCode()
}

object ReminderPlanner {

    /**
     * Every reminder of [events] with a trigger after [after] up to and including [upTo], earliest first.
     * A timed event counts the minutes as elapsed time. An all day event counts them on the clock, so
     * "1 day before at 09:00" stays 09:00 over a clock change.
     */
    fun triggers(
        events: List<EventEntity>,
        calendars: List<CalendarEntity>,
        after: Instant,
        upTo: Instant,
        zone: ZoneId
    ): List<DueReminder> {
        val byId = calendars.associateBy { it.id }
        val result = ArrayList<DueReminder>()
        val windowFrom = after.atZone(zone).toLocalDateTime()
        val windowTo = upTo.atZone(zone).toLocalDateTime()
        for (event in events) {
            val minutes = Reminders.parse(event.reminders)
            if (minutes.isEmpty()) continue
            val calendar = byId[event.calendarId] ?: continue
            // start = trigger + minutes, so the starts to look at lie between these two readings
            // With travel time the reminders go off that much earlier, so you can leave on time (not for all day events)
            val travel = if (event.allDay) 0L else event.travelMinutes.toLong()
            val from = windowFrom.plusMinutes(minutes.min() + travel).minusMinutes(1)
            val to = windowTo.plusMinutes(minutes.max() + travel).plusMinutes(1)
            for (occurrence in expandEvent(event, calendar, from, to, zone)) {
                for (m in minutes) {
                    val trigger = if (occurrence.allDay) {
                        occurrence.start.minusMinutes(m.toLong()).atZone(zone).toInstant()
                    } else {
                        occurrence.start.atZone(zone).toInstant().minusSeconds((m + travel) * 60L)
                    }
                    if (trigger > after && trigger <= upTo) {
                        result.add(
                            DueReminder(event.id, event.title, event.location, event.allDay, occurrence.start, m, trigger)
                        )
                    }
                }
            }
        }
        return result.sortedBy { it.trigger }
    }
}
