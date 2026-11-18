package org.nooklin.calendar.data.dashboard

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import org.nooklin.calendar.data.model.Occurrence

/** The calculations behind the dashboard cards. Pure functions on a list of occurrences, so they can be tested. */
object DashboardLogic {
    data class Span(val start: LocalDateTime, val end: LocalDateTime) {
        val minutes: Long get() = Duration.between(start, end).toMinutes()
    }

    data class Overlap(val first: Occurrence, val second: Occurrence)

    data class Slot(val start: LocalDateTime, val minutes: Int)

    data class Evening(val free: Boolean, val busyFrom: LocalDateTime?, val events: List<Occurrence>)

    /** Only the calendars that were chosen for the dashboard. */
    fun counted(all: List<Occurrence>, settings: DashboardSettings): List<Occurrence> {
        val ids = settings.calendarIds ?: return all
        return all.filter { it.calendarId in ids }
    }

    /**
     * The busy time between [from] and [to], sorted and merged. A timed event is busy; an all day event only when the
     * settings say so. An event without length counts as one minute.
     */
    fun busy(all: List<Occurrence>, settings: DashboardSettings, from: LocalDateTime, to: LocalDateTime): List<Span> {
        val raw = ArrayList<Span>()
        for (o in all) {
            if (o.allDay && !settings.allDayBusy) continue
            val end = if (!o.allDay && !o.end.isAfter(o.start)) o.start.plusMinutes(1) else o.end
            if (!end.isAfter(from) || !o.start.isBefore(to)) continue
            raw.add(Span(maxOf(o.start, from), minOf(end, to)))
        }
        raw.sortBy { it.start }
        val merged = ArrayList<Span>()
        for (span in raw) {
            val last = merged.lastOrNull()
            if (last != null && !span.start.isAfter(last.end)) {
                merged[merged.size - 1] = Span(last.start, maxOf(last.end, span.end))
            } else {
                merged.add(span)
            }
        }
        return merged
    }

    /** The gaps between busy spans inside [from] and [to] that last at least [minMinutes]. */
    fun freeBlocks(busy: List<Span>, from: LocalDateTime, to: LocalDateTime, minMinutes: Int): List<Span> {
        val result = ArrayList<Span>()
        var cursor = from
        for (span in busy) {
            if (span.start.isAfter(cursor)) result.add(Span(cursor, span.start))
            if (span.end.isAfter(cursor)) cursor = span.end
        }
        if (to.isAfter(cursor)) result.add(Span(cursor, to))
        return result.filter { it.minutes >= minMinutes }
    }

    private fun window(date: LocalDate, startMinutes: Int, endMinutes: Int): Span {
        val start = date.atStartOfDay().plusMinutes(startMinutes.toLong())
        var end = date.atStartOfDay().plusMinutes(endMinutes.toLong())
        if (!end.isAfter(start)) end = end.plusDays(1)
        return Span(start, end)
    }

    fun eveningWindow(date: LocalDate, settings: DashboardSettings): Span = window(date, settings.eveningStart, settings.eveningEnd)

    fun dayWindow(date: LocalDate, settings: DashboardSettings): Span = window(date, settings.dayStart, settings.dayEnd)

    /** An evening is free when nothing that counts as busy overlaps the evening window. */
    fun evening(all: List<Occurrence>, settings: DashboardSettings, date: LocalDate): Evening {
        val w = eveningWindow(date, settings)
        val spans = busy(all, settings, w.start, w.end)
        val events = all.filter { (!it.allDay || settings.allDayBusy) && it.start.isBefore(w.end) && (it.end.isAfter(w.start) || (it.start == it.end && !it.start.isBefore(w.start))) }
            .sortedBy { it.start }
        return Evening(spans.isEmpty(), spans.firstOrNull()?.start, events)
    }

    /** The minutes of busy time on each of [days]. */
    fun weekLoad(all: List<Occurrence>, settings: DashboardSettings, days: List<LocalDate>): List<Long> =
        days.map { day -> busy(all, settings, day.atStartOfDay(), day.plusDays(1).atStartOfDay()).sumOf { it.minutes } }

    /** The timed events that have not ended yet at [now], in order of start. An event that is going on is first. */
    fun upcoming(all: List<Occurrence>, now: LocalDateTime): List<Occurrence> =
        all.filter { !it.allDay && it.end.isAfter(now) }.sortedBy { it.start }

    /** Timed events that overlap in time, between [from] and [to]. */
    fun overlaps(all: List<Occurrence>, from: LocalDateTime, to: LocalDateTime, limit: Int = 50): List<Overlap> {
        val timed = all.filter { !it.allDay && it.end.isAfter(it.start) && it.start.isBefore(to) && it.end.isAfter(from) }
            .sortedBy { it.start }
        val result = ArrayList<Overlap>()
        for (i in timed.indices) {
            var j = i + 1
            while (j < timed.size && timed[j].start.isBefore(timed[i].end)) {
                result.add(Overlap(timed[i], timed[j]))
                if (result.size >= limit) return result
                j++
            }
        }
        return result
    }

    /**
     * The first free slots of [minutes] in the daytime window, on each of the next days. One slot per free block,
     * starting at the start of the block. Today starts at [now] rounded up to a quarter of an hour.
     */
    fun slots(all: List<Occurrence>, settings: DashboardSettings, now: LocalDateTime, minutes: Int, maxCount: Int): List<Slot> {
        val result = ArrayList<Slot>()
        val rounded = roundUp(now, 15)
        for (offset in 0 until settings.lookAhead) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            val w = dayWindow(date, settings)
            val from = if (offset == 0) maxOf(w.start, rounded) else w.start
            if (!from.isBefore(w.end)) continue
            val spans = busy(all, settings, from, w.end)
            for (block in freeBlocks(spans, from, w.end, minutes)) {
                result.add(Slot(block.start, minutes))
                if (result.size >= maxCount) return result
            }
        }
        return result
    }

    fun roundUp(time: LocalDateTime, step: Int): LocalDateTime {
        val t = time.truncatedTo(ChronoUnit.MINUTES)
        val extra = (step - t.minute % step) % step
        return t.plusMinutes(extra.toLong())
    }

    /** The Saturday and Sunday of the weekend that is going on, or else the next one. */
    fun weekend(today: LocalDate): Pair<LocalDate, LocalDate> {
        val saturday = when (today.dayOfWeek.value) {
            7 -> today.minusDays(1)
            else -> today.plusDays((6 - today.dayOfWeek.value).toLong())
        }
        return saturday to saturday.plusDays(1)
    }
}
