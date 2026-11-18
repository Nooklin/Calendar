package org.nooklin.calendar.data.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.recurrence.Recurrence

/**
 * One event on the calendar as it is shown: a single event, or one repeat of a series. [start] and [end] are clock
 * readings in the time zone of the phone. For an all day event they are midnights and [end] is the start of the day
 * after the last day.
 */
data class Occurrence(
    val eventId: Long,
    val calendarId: Long,
    val calendarName: String,
    val color: Int,
    val title: String,
    val location: String,
    val notes: String,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime,
    // The zone the event was made in, null for all day and floating events
    val zoneId: String?,
    val repeats: Boolean,
    // The rule of the series as stored, null for a single event
    val rrule: String?,
    // The same start and end as clock readings in the zone of the event: what the editor works with
    val sourceStart: LocalDateTime,
    val sourceEnd: LocalDateTime,
    // Minutes before the start, see [Reminders]
    val reminders: List<Int>,
    // Part of a CalDAV resource that this app cannot write back, so it is shown but not changed
    val locked: Boolean = false,
    // Minutes of travel before the start, 0 for none
    val travelMinutes: Int = 0
) {
    val startDate: LocalDate get() = start.toLocalDate()

    // An event that ends exactly at midnight does not belong to the day that starts there
    val lastDate: LocalDate
        get() {
            if (!end.isAfter(start)) return startDate
            val endDate = end.toLocalDate()
            return if (allDay || end.toLocalTime() == LocalTime.MIDNIGHT) endDate.minusDays(1) else endDate
        }

    val multiDay: Boolean get() = lastDate.isAfter(startDate)

    fun touches(date: LocalDate): Boolean = !date.isBefore(startDate) && !date.isAfter(lastDate)

    /** Identifies this occurrence in a list: the same series repeats with another start. */
    val key: String get() = "$eventId@$start"
}

/** The stored number of seconds is a clock reading, see [EventEntity]. */
fun Long.toReading(): LocalDateTime = LocalDateTime.ofEpochSecond(this, 0, ZoneOffset.UTC)

fun LocalDateTime.toStored(): Long = toEpochSecond(ZoneOffset.UTC)

/**
 * The occurrences of [event] that touch the window from [windowStart] up to [windowEnd] (clock readings in the zone
 * of the phone, [zone]). A repeat keeps its clock time in the zone of the event, which then may show at another
 * time on a phone in another zone.
 */
fun expandEvent(
    event: EventEntity,
    calendar: CalendarEntity,
    windowStart: LocalDateTime,
    windowEnd: LocalDateTime,
    zone: ZoneId
): List<Occurrence> {
    val firstStart = event.startLocal.toReading()
    val firstEnd = event.endLocal.toReading()
    val recurrence = event.rrule?.let { Recurrence.parse(it) }
    val exdates = Recurrence.parseExdates(event.exdates)
    // Null: all day and floating events have no zone of their own, their reading is taken in the zone of the phone
    val eventZone: ZoneId? = if (event.allDay || event.zoneId == null) null else {
        runCatching { ZoneId.of(event.zoneId) }.getOrNull() ?: zone
    }

    val reminders = Reminders.parse(event.reminders)

    fun build(start: LocalDateTime, end: LocalDateTime, sourceStart: LocalDateTime, sourceEnd: LocalDateTime): Occurrence? {
        val touches = start < windowEnd && (end > windowStart || (start == end && start >= windowStart))
        if (!touches) return null
        return Occurrence(
            eventId = event.id,
            calendarId = calendar.id,
            calendarName = calendar.name,
            color = calendar.color,
            title = event.title,
            location = event.location,
            notes = event.notes,
            allDay = event.allDay,
            start = start,
            end = end,
            zoneId = event.zoneId,
            repeats = recurrence != null,
            rrule = event.rrule,
            sourceStart = sourceStart,
            sourceEnd = sourceEnd,
            reminders = reminders,
            locked = event.locked,
            travelMinutes = event.travelMinutes
        )
    }

    if (eventZone == null) {
        val length = Duration.between(firstStart, firstEnd).let { if (it.isNegative) Duration.ZERO else it }
        val starts = if (recurrence == null) listOf(firstStart) else {
            recurrence.expand(firstStart, windowStart.minus(length).minusDays(1), windowEnd, exdates)
        }
        return starts.mapNotNull { build(it, it.plus(length), it, it.plus(length)) }
    }

    val firstStartZoned = firstStart.atZone(eventZone)
    val length = Duration.between(firstStartZoned, firstEnd.atZone(eventZone)).let { if (it.isNegative) Duration.ZERO else it }
    // Two days of margin on both sides, because the zone of the event can be a day away from the zone of the phone
    val starts = if (recurrence == null) listOf(firstStart) else {
        recurrence.expand(firstStart, windowStart.minus(length).minusDays(2), windowEnd.plusDays(2), exdates)
    }
    return starts.mapNotNull { reading ->
        val startInstant = reading.atZone(eventZone)
        val endInstant = startInstant.plus(length)
        build(
            startInstant.withZoneSameInstant(zone).toLocalDateTime(),
            endInstant.withZoneSameInstant(zone).toLocalDateTime(),
            reading,
            endInstant.toLocalDateTime()
        )
    }
}
