package org.nooklin.calendar.data.ics

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.nooklin.calendar.data.recurrence.Recurrence

/** One event of an .ics file, in the notation of this app: clock readings in [zoneId], null for floating and all day. */
data class IcsEvent(
    val uid: String,
    val title: String,
    val location: String,
    val notes: String,
    val allDay: Boolean,
    val zoneId: String?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val rrule: String?,
    val exdates: List<LocalDateTime>,
    // Minutes before the start, see data/model/Reminders.kt
    val reminders: List<Int>,
    // Minutes of travel before the start, from the X-NOOKLIN-TRAVEL line; 0 for none
    val travelMinutes: Int = 0
)

/**
 * What was read from a file. [skipped] events were left out (cancelled, no start, unreadable). [simplified] events
 * were taken in but lost something: a repeat rule or time zone this app cannot show.
 */
data class IcsFile(val name: String?, val events: List<IcsEvent>, val skipped: Int, val simplified: Int)

/** Reads iCalendar (RFC 5545) text. Unknown properties and components are ignored. */
object IcsParser {
    private val READING = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DURATION = Regex("^([+-])?P(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?)?$")

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    private class Alarm(val props: List<Prop>)

    private class Raw(val props: MutableList<Prop> = ArrayList(), val alarms: MutableList<Alarm> = ArrayList())

    private class Moment(val reading: LocalDateTime, val zoneId: String?, val allDay: Boolean, val unknownZone: Boolean)

    fun parse(text: String): IcsFile {
        val lines = unfold(text)
        var name: String? = null
        val raws = ArrayList<Raw>()
        var current: Raw? = null
        var alarm: MutableList<Prop>? = null
        var skipped = 0
        for (line in lines) {
            val prop = parseLine(line) ?: continue
            when {
                prop.name == "BEGIN" && prop.value.uppercase() == "VEVENT" -> current = Raw()
                prop.name == "END" && prop.value.uppercase() == "VEVENT" -> {
                    current?.let { raws.add(it) }
                    current = null
                    alarm = null
                }
                prop.name == "BEGIN" && prop.value.uppercase() == "VALARM" && current != null -> alarm = ArrayList()
                prop.name == "END" && prop.value.uppercase() == "VALARM" -> {
                    alarm?.let { current?.alarms?.add(Alarm(it)) }
                    alarm = null
                }
                current == null -> if (prop.name == "X-WR-CALNAME" && name == null) name = unescape(prop.value)
                alarm != null -> alarm.add(prop)
                else -> current.props.add(prop)
            }
        }
        var simplified = 0
        val masters = ArrayList<IcsEvent>()
        val overrides = ArrayList<Pair<IcsEvent, LocalDateTime>>()
        for (raw in raws) {
            val converted = convert(raw)
            if (converted == null) {
                skipped++
                continue
            }
            if (converted.simplified) simplified++
            val recurrenceId = converted.recurrenceId
            if (recurrenceId != null) overrides.add(converted.event to recurrenceId) else masters.add(converted.event)
        }
        // A changed repeat of a series: the series leaves that day out, the change becomes an event of its own
        val result = ArrayList<IcsEvent>()
        val byUid = masters.associateBy { it.uid }.toMutableMap()
        var counter = 0
        for ((override, recurrenceId) in overrides) {
            val master = byUid[override.uid]
            if (master != null) {
                byUid[override.uid] = master.copy(exdates = master.exdates + recurrenceId)
                counter++
                result.add(override.copy(uid = override.uid + "-r" + counter, rrule = null, exdates = emptyList()))
            } else {
                result.add(override.copy(rrule = null, exdates = emptyList()))
            }
        }
        result.addAll(0, masters.map { byUid.getValue(it.uid) })
        return IcsFile(name, result, skipped, simplified)
    }

    private class Converted(val event: IcsEvent, val recurrenceId: LocalDateTime?, val simplified: Boolean)

    private fun convert(raw: Raw): Converted? {
        fun first(name: String): Prop? = raw.props.firstOrNull { it.name == name }
        if (first("STATUS")?.value?.uppercase() == "CANCELLED") return null
        val startProp = first("DTSTART") ?: return null
        val startMoment = moment(startProp) ?: return null
        var simplified = startMoment.unknownZone
        val zoneId = startMoment.zoneId
        val allDay = startMoment.allDay
        val start = startMoment.reading

        var end: LocalDateTime? = null
        val endProp = first("DTEND")
        val durationProp = first("DURATION")
        if (endProp != null) {
            moment(endProp)?.let { end = intoZone(it, zoneId) }
        } else if (durationProp != null) {
            end = addDuration(start, durationProp.value)
        }
        val finalEnd = when {
            allDay -> end?.takeIf { it > start } ?: start.plusDays(1)
            else -> end?.takeIf { it >= start } ?: start
        }

        var rrule: String? = null
        val ruleProp = first("RRULE")
        if (ruleProp != null) {
            rrule = normaliseRule(ruleProp.value, zoneId, allDay)
            if (rrule == null) simplified = true
        }
        val exdates = ArrayList<LocalDateTime>()
        if (rrule != null) {
            raw.props.filter { it.name == "EXDATE" }.forEach { prop ->
                prop.value.split(",").forEach { part ->
                    moment(Prop("EXDATE", prop.params, part.trim()))?.let { exdates.add(intoZone(it, zoneId)) }
                }
            }
        }
        val reminders = ArrayList<Int>()
        for (alarm in raw.alarms) {
            val trigger = alarm.props.firstOrNull { it.name == "TRIGGER" } ?: continue
            // Only a time before or after the start. A time of day, or relative to the end, is left out.
            if (trigger.params["VALUE"]?.uppercase() == "DATE-TIME") continue
            if (trigger.params["RELATED"]?.uppercase() == "END") continue
            val minutes = triggerMinutes(trigger.value) ?: continue
            if (!allDay && minutes < 0) continue
            reminders.add(minutes)
        }
        val recurrenceId = first("RECURRENCE-ID")?.let { prop -> moment(prop)?.let { intoZone(it, zoneId) } }
        val uid = first("UID")?.value?.trim().takeUnless { it.isNullOrEmpty() }
            ?: ("import-" + (first("SUMMARY")?.value.orEmpty() + start).hashCode())
        val event = IcsEvent(
            uid = uid,
            title = unescape(first("SUMMARY")?.value.orEmpty()),
            location = unescape(first("LOCATION")?.value.orEmpty()),
            notes = unescape(first("DESCRIPTION")?.value.orEmpty()),
            allDay = allDay,
            zoneId = if (allDay) null else zoneId,
            start = start,
            end = finalEnd,
            rrule = rrule,
            exdates = exdates,
            reminders = reminders.distinct(),
            travelMinutes = first("X-NOOKLIN-TRAVEL")?.value?.trim()?.toIntOrNull()?.takeIf { it in 1..1440 && !allDay } ?: 0
        )
        return Converted(event, recurrenceId, simplified)
    }

    // Unfolds lines: a line that starts with a space or a tab continues the line before it
    private fun unfold(text: String): List<String> {
        val result = ArrayList<String>()
        for (line in text.split("\r\n", "\n", "\r")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.substring(1)
            } else {
                result.add(line)
            }
        }
        return result
    }

    private fun parseLine(line: String): Prop? {
        if (line.isBlank()) return null
        // The first colon that is not inside quotes ends the name and the parameters
        var quoted = false
        var colon = -1
        for (i in line.indices) {
            val c = line[i]
            if (c == '"') quoted = !quoted
            if (c == ':' && !quoted) {
                colon = i
                break
            }
        }
        if (colon < 0) return null
        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = splitOutsideQuotes(head, ';')
        val params = HashMap<String, String>()
        parts.drop(1).forEach { part ->
            val eq = part.indexOf('=')
            if (eq > 0) params[part.substring(0, eq).uppercase()] = part.substring(eq + 1).trim('"')
        }
        return Prop(parts[0].trim().uppercase(), params, value)
    }

    private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
        val result = ArrayList<String>()
        val builder = StringBuilder()
        var quoted = false
        for (c in text) {
            if (c == '"') quoted = !quoted
            if (c == separator && !quoted) {
                result.add(builder.toString())
                builder.setLength(0)
            } else {
                builder.append(c)
            }
        }
        result.add(builder.toString())
        return result
    }

    private fun moment(prop: Prop): Moment? {
        val value = prop.value.trim()
        val dateOnly = prop.params["VALUE"]?.uppercase() == "DATE" || value.length == 8
        if (dateOnly) {
            val date = runCatching { LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() ?: return null
            return Moment(date.atStartOfDay(), null, true, false)
        }
        val utc = value.endsWith("Z") || value.endsWith("z")
        val reading = runCatching { LocalDateTime.parse(value.trimEnd('Z', 'z'), READING) }.getOrNull() ?: return null
        if (utc) return Moment(reading, "UTC", false, false)
        val tzid = prop.params["TZID"] ?: return Moment(reading, null, false, false)
        val zone = zoneFor(tzid)
        // A zone this app does not know (a Windows name, for example) makes it floating time
        return if (zone != null) Moment(reading, zone, false, false) else Moment(reading, null, false, true)
    }

    private fun zoneFor(tzid: String): String? {
        val parts = tzid.trim('"').split("/")
        // Some programs write a prefix before the name, such as /mozilla.org/20050126_1/Europe/Berlin
        for (take in listOf(parts.size, 2, 1)) {
            if (take > parts.size || take < 1) continue
            val candidate = parts.takeLast(take).joinToString("/")
            val id = runCatching { ZoneId.of(candidate) }.getOrNull()
            if (id != null && candidate.contains("/") || candidate == "UTC") return id?.id
        }
        return null
    }

    // The reading of [moment] as a clock reading in zone [zoneId] of the event
    private fun intoZone(moment: Moment, zoneId: String?): LocalDateTime {
        val from = moment.zoneId
        if (moment.allDay || from == null || zoneId == null || from == zoneId) return moment.reading
        return moment.reading.atZone(ZoneId.of(from)).withZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime()
    }

    private fun addDuration(start: LocalDateTime, text: String): LocalDateTime? {
        val match = DURATION.matchEntire(text.trim()) ?: return null
        val sign = if (match.groupValues[1] == "-") -1L else 1L
        fun n(i: Int) = match.groupValues[i].toLongOrNull() ?: 0L
        return start.plusWeeks(sign * n(2)).plusDays(sign * n(3))
            .plusHours(sign * n(4)).plusMinutes(sign * n(5)).plusSeconds(sign * n(6))
    }

    // The minutes before the start. A trigger of -PT15M is 15, PT9H (after the start) is -540.
    private fun triggerMinutes(text: String): Int? {
        val match = DURATION.matchEntire(text.trim()) ?: return null
        val sign = if (match.groupValues[1] == "-") -1L else 1L
        fun n(i: Int) = match.groupValues[i].toLongOrNull() ?: 0L
        val seconds = ((n(2) * 7 + n(3)) * 24 + n(4)) * 3600 + n(5) * 60 + n(6)
        val after = sign * seconds
        return (-after / 60).toInt()
    }

    /**
     * The repeat rule in the form the app stores. A UNTIL in UTC becomes a reading in the zone of the event, a
     * WKST is dropped. Returns null when the app cannot show the rule.
     */
    private fun normaliseRule(text: String, zoneId: String?, allDay: Boolean): String? {
        val parts = text.split(";").filter { it.isNotBlank() }.mapNotNull { part ->
            val key = part.substringBefore('=').trim().uppercase()
            val value = part.substringAfter('=', "").trim()
            when (key) {
                "WKST" -> null
                "UNTIL" -> "UNTIL=" + (untilReading(value, zoneId, allDay) ?: return null)
                else -> "$key=$value"
            }
        }
        val rule = parts.joinToString(";")
        return if (Recurrence.parse(rule) != null) rule else null
    }

    private fun untilReading(value: String, zoneId: String?, allDay: Boolean): String? {
        if (value.length == 8) return value
        val utc = value.endsWith("Z") || value.endsWith("z")
        val reading = runCatching { LocalDateTime.parse(value.trimEnd('Z', 'z'), READING) }.getOrNull() ?: return null
        if (!utc || allDay || zoneId == null) return reading.format(READING)
        val inZone = reading.atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.of(zoneId)).toLocalDateTime()
        return inZone.format(READING)
    }

    private fun unescape(text: String): String {
        if (!text.contains('\\')) return text
        val builder = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n', 'N' -> builder.append('\n')
                    else -> builder.append(next)
                }
                i += 2
            } else {
                builder.append(c)
                i++
            }
        }
        return builder.toString()
    }
}
