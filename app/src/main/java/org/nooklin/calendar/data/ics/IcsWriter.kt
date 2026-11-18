package org.nooklin.calendar.data.ics

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.model.toReading
import org.nooklin.calendar.data.recurrence.Recurrence

/** Writes events as iCalendar (RFC 5545) text, with a VTIMEZONE block for every time zone that is used. */
object IcsWriter {
    private val READING = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    private const val CRLF = "\r\n"

    // How far past the last event the time zone blocks reach: later changes are not described
    private const val YEARS_AHEAD = 30

    fun write(calendarName: String, events: List<EventEntity>, now: Instant, currentYear: Int): String {
        val lines = ArrayList<String>()
        lines.add("BEGIN:VCALENDAR")
        lines.add("VERSION:2.0")
        lines.add("PRODID:-//Nooklin//Calendar//EN")
        lines.add("CALSCALE:GREGORIAN")
        lines.add("X-WR-CALNAME:" + escape(calendarName))

        val zones = events.mapNotNull { if (it.allDay) null else it.zoneId }
            .filter { it != "UTC" && runCatching { ZoneId.of(it) }.isSuccess }.distinct().sorted()
        val firstYear = events.minOfOrNull { it.startLocal.toReading().year }?.minus(1)?.coerceAtLeast(1971) ?: currentYear
        val lastYear = maxOf(events.maxOfOrNull { it.endLocal.toReading().year } ?: currentYear, currentYear) + YEARS_AHEAD
        zones.forEach { lines.addAll(timeZoneBlock(it, firstYear, lastYear)) }

        events.forEach { lines.addAll(eventLines(it, now)) }
        lines.add("END:VCALENDAR")
        return lines.joinToString(CRLF) { fold(it) } + CRLF
    }

    private fun eventLines(e: EventEntity, now: Instant): List<String> {
        val out = ArrayList<String>()
        val zone = if (e.allDay) null else e.zoneId?.takeIf { it == "UTC" || runCatching { ZoneId.of(it) }.isSuccess }
        val start = e.startLocal.toReading()
        val end = e.endLocal.toReading()
        out.add("BEGIN:VEVENT")
        out.add("UID:" + e.uid)
        out.add("DTSTAMP:" + now.atOffset(ZoneOffset.UTC).format(READING) + "Z")
        out.add(dateProperty("DTSTART", start, zone, e.allDay))
        out.add(dateProperty("DTEND", end, zone, e.allDay))
        out.add("SUMMARY:" + escape(e.title))
        if (e.location.isNotEmpty()) out.add("LOCATION:" + escape(e.location))
        if (e.notes.isNotEmpty()) out.add("DESCRIPTION:" + escape(e.notes))
        val rule = e.rrule?.let { Recurrence.parse(it) }
        if (rule != null) {
            out.add("RRULE:" + ruleText(rule, zone, e.allDay))
            val exdates = Recurrence.parseExdates(e.exdates).sorted()
            if (exdates.isNotEmpty()) out.add(exdateProperty(exdates, zone, e.allDay))
        }
        if (e.travelMinutes > 0 && !e.allDay) out.add("X-NOOKLIN-TRAVEL:" + e.travelMinutes)
        Reminders.parse(e.reminders).forEach { minutes ->
            out.add("BEGIN:VALARM")
            out.add("ACTION:DISPLAY")
            out.add("DESCRIPTION:" + escape(e.title.ifEmpty { "Reminder" }))
            out.add("TRIGGER;RELATED=START:" + triggerText(minutes))
            out.add("END:VALARM")
        }
        out.add("END:VEVENT")
        return out
    }

    private fun dateProperty(name: String, reading: LocalDateTime, zone: String?, allDay: Boolean): String = when {
        allDay -> "$name;VALUE=DATE:" + reading.toLocalDate().format(DATE)
        zone == null -> "$name:" + reading.format(READING)
        zone == "UTC" -> "$name:" + reading.format(READING) + "Z"
        else -> "$name;TZID=$zone:" + reading.format(READING)
    }

    private fun exdateProperty(exdates: List<LocalDateTime>, zone: String?, allDay: Boolean): String = when {
        allDay -> "EXDATE;VALUE=DATE:" + exdates.joinToString(",") { it.toLocalDate().format(DATE) }
        zone == null -> "EXDATE:" + exdates.joinToString(",") { it.format(READING) }
        zone == "UTC" -> "EXDATE:" + exdates.joinToString(",") { it.format(READING) + "Z" }
        else -> "EXDATE;TZID=$zone:" + exdates.joinToString(",") { it.format(READING) }
    }

    // A zone with a TZID wants UNTIL in UTC, floating time wants it as it is, all day wants a date
    private fun ruleText(rule: Recurrence, zone: String?, allDay: Boolean): String {
        val until = rule.until ?: return rule.toRule()
        val base = rule.copy(until = null).toRule()
        val text = when {
            allDay -> until.toLocalDate().format(DATE)
            zone == null -> until.format(READING)
            zone == "UTC" -> until.format(READING) + "Z"
            else -> until.atZone(ZoneId.of(zone)).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime().format(READING) + "Z"
        }
        return "$base;UNTIL=$text"
    }

    // Minutes before the start as a trigger: 10 is -PT10M, -540 (after the start) is PT9H
    private fun triggerText(minutes: Int): String {
        val sign = if (minutes > 0) "-" else ""
        val total = Math.abs(minutes)
        val hours = total / 60
        val rest = total % 60
        val time = (if (hours > 0) "${hours}H" else "") + (if (rest > 0 || hours == 0) "${rest}M" else "")
        return "${sign}PT$time"
    }

    /** One STANDARD or DAYLIGHT block for the offset at the start, and one for every switch after it up to [lastYear]. */
    private fun timeZoneBlock(zoneId: String, firstYear: Int, lastYear: Int): List<String> {
        val zone = ZoneId.of(zoneId)
        val rules = zone.rules
        val out = ArrayList<String>()
        out.add("BEGIN:VTIMEZONE")
        out.add("TZID:$zoneId")
        val startInstant = LocalDateTime.of(firstYear, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)
        val firstOffset = rules.getOffset(startInstant)
        val firstKind = if (rules.isDaylightSavings(startInstant)) "DAYLIGHT" else "STANDARD"
        out.add("BEGIN:$firstKind")
        out.add("DTSTART:" + LocalDateTime.of(firstYear, 1, 1, 0, 0).format(READING))
        out.add("TZOFFSETFROM:" + offsetText(firstOffset))
        out.add("TZOFFSETTO:" + offsetText(firstOffset))
        out.add("END:$firstKind")
        val stop = LocalDateTime.of(lastYear, 1, 1, 0, 0).toInstant(ZoneOffset.UTC)
        var next = rules.nextTransition(startInstant)
        var guard = 0
        while (next != null && next.instant.isBefore(stop) && guard < 400) {
            val kind = if (rules.isDaylightSavings(next.instant)) "DAYLIGHT" else "STANDARD"
            out.add("BEGIN:$kind")
            out.add("DTSTART:" + next.dateTimeBefore.format(READING))
            out.add("TZOFFSETFROM:" + offsetText(next.offsetBefore))
            out.add("TZOFFSETTO:" + offsetText(next.offsetAfter))
            out.add("END:$kind")
            next = rules.nextTransition(next.instant)
            guard++
        }
        out.add("END:VTIMEZONE")
        return out
    }

    private fun offsetText(offset: ZoneOffset): String {
        val total = offset.totalSeconds
        val sign = if (total < 0) "-" else "+"
        val abs = Math.abs(total)
        return "%s%02d%02d".format(sign, abs / 3600, (abs % 3600) / 60)
    }

    private fun escape(text: String): String =
        text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    // Lines are at most 75 octets: the rest goes on the next line after a space. A character is never cut in two.
    private fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= 75) return line
        val builder = StringBuilder()
        var octets = 0
        var limit = 75
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val chars = Character.charCount(cp)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (octets + size > limit) {
                builder.append(CRLF).append(' ')
                octets = 0
                limit = 74
            }
            builder.appendCodePoint(cp)
            octets += size
            i += chars
        }
        return builder.toString()
    }
}
