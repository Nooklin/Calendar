package org.nooklin.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** A date or a time that was found in the typed title, with the place of its text. */
data class TitleHit(
    val isDate: Boolean,
    // The text of the hit is text.substring(start, end), including a word like "at" or "on" before it
    val start: Int,
    val end: Int,
    val date: LocalDate? = null,
    val time: LocalTime? = null
)

/**
 * Finds a date and a time in the title of a new event, in English and Dutch. Pure, so it can be tested.
 * A weekday means the next one after today. A day and month without a year that has already passed means next year.
 */
object TitleParser {
    private val options = setOf(RegexOption.IGNORE_CASE)
    private const val BEFORE_TIME = "(?:(?:at|@|om|rond|around)\\s+)?"
    private const val BEFORE_DATE = "(?:(?:on|op)\\s+)?"

    private val months = mapOf(
        "january" to 1, "januari" to 1, "jan" to 1,
        "february" to 2, "februari" to 2, "feb" to 2,
        "march" to 3, "maart" to 3, "mar" to 3, "mrt" to 3,
        "april" to 4, "apr" to 4,
        "may" to 5, "mei" to 5,
        "june" to 6, "juni" to 6, "jun" to 6,
        "july" to 7, "juli" to 7, "jul" to 7,
        "august" to 8, "augustus" to 8, "aug" to 8,
        "september" to 9, "sept" to 9, "sep" to 9,
        "october" to 10, "oktober" to 10, "oct" to 10, "okt" to 10,
        "november" to 11, "nov" to 11,
        "december" to 12, "dec" to 12
    )
    private val weekdays = mapOf(
        "monday" to DayOfWeek.MONDAY, "maandag" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "dinsdag" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "woensdag" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "donderdag" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "vrijdag" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "zaterdag" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "zondag" to DayOfWeek.SUNDAY
    )

    private fun alternatives(words: Set<String>) = words.sortedByDescending { it.length }.joinToString("|")

    private val monthWords = alternatives(months.keys)
    private val weekdayWords = alternatives(weekdays.keys)

    private val relativeDay = Regex("\\b(day after tomorrow|overmorgen|tomorrow|morgen|today|vandaag)(?!\\w)", options)
    private val inDays = Regex("\\b(?:in|over)\\s+(\\d{1,3})\\s+(days?|dagen|dag|weeks?|weken)(?!\\w)", options)
    private val weekday = Regex("\\b(?:(?:on|op|next|volgende|aanstaande|this|deze)\\s+)?($weekdayWords)(?!\\w)", options)
    private val monthFirst = Regex("\\b$BEFORE_DATE($monthWords)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?!\\w)(?:,?\\s+(\\d{4})(?!\\d))?", options)
    private val dayFirst = Regex("\\b$BEFORE_DATE(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?($monthWords)(?!\\w)(?:,?\\s+(\\d{4})(?!\\d))?", options)
    private val numeric = Regex("(?<![\\d/-])$BEFORE_DATE(\\d{1,2})([/-])(\\d{1,2})(?:\\2(\\d{4}|\\d{2}))?(?![\\d/-])", options)

    private val clock = Regex("\\b$BEFORE_TIME(\\d{1,2})[:.](\\d{2})\\s*(a\\.?m\\.?|p\\.?m\\.?)?(?!\\w)", options)
    private val meridiem = Regex("\\b$BEFORE_TIME(\\d{1,2})\\s*(a\\.?m\\.?|p\\.?m\\.?)(?!\\w)", options)
    private val hourWord = Regex("\\b$BEFORE_TIME(\\d{1,2})\\s?(?:uur|u|h)(?!\\w)", options)
    private val half = Regex("\\bhalf\\s+(\\d{1,2})(?!\\w)", options)
    private val noon = Regex("\\b$BEFORE_TIME(noon|midnight|middernacht)(?!\\w)", options)
    private val bareHour = Regex("\\b(?:at|@|om)\\s+(\\d{1,2})(?![\\d:.])", options)

    fun parse(text: String, today: LocalDate): List<TitleHit> {
        val dates = ArrayList<TitleHit>()
        fun addDate(m: MatchResult, date: LocalDate?) {
            if (date != null) addHit(dates, TitleHit(true, m.range.first, m.range.last + 1, date = date))
        }
        for (m in relativeDay.findAll(text)) {
            val days = when (m.groupValues[1].lowercase()) {
                "today", "vandaag" -> 0L
                "tomorrow", "morgen" -> 1L
                else -> 2L
            }
            addDate(m, today.plusDays(days))
        }
        for (m in inDays.findAll(text)) {
            val n = m.groupValues[1].toLong()
            val unit = if (m.groupValues[2].lowercase().startsWith("w")) 7L else 1L
            addDate(m, today.plusDays(n * unit))
        }
        for (m in weekday.findAll(text)) {
            val target = weekdays[m.groupValues[1].lowercase()] ?: continue
            val ahead = (target.value - today.dayOfWeek.value + 7) % 7
            addDate(m, today.plusDays(if (ahead == 0) 7L else ahead.toLong()))
        }
        for (m in monthFirst.findAll(text)) {
            addDate(m, monthDay(months[m.groupValues[1].lowercase()], m.groupValues[2].toInt(), m.groupValues[3], today))
        }
        for (m in dayFirst.findAll(text)) {
            addDate(m, monthDay(months[m.groupValues[2].lowercase()], m.groupValues[1].toInt(), m.groupValues[3], today))
        }
        for (m in numeric.findAll(text)) {
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[3].toInt()
            // 14/7 is a day first, 7/14 a month first; when both fit, "/" is month first and "-" is day first
            val dayFirstOrder = when {
                a > 12 -> true
                b > 12 -> false
                else -> m.groupValues[2] == "-"
            }
            val month = if (dayFirstOrder) b else a
            val day = if (dayFirstOrder) a else b
            var year = m.groupValues[4]
            if (year.length == 2) year = "20$year"
            addDate(m, monthDay(month, day, year, today))
        }

        val times = ArrayList<TitleHit>()
        fun addTime(m: MatchResult, time: LocalTime?) {
            if (time == null) return
            val hit = TitleHit(false, m.range.first, m.range.last + 1, time = time)
            if (dates.none { overlaps(it, hit) }) addHit(times, hit)
        }
        for (m in clock.findAll(text)) addTime(m, timeOf(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3]))
        for (m in meridiem.findAll(text)) addTime(m, timeOf(m.groupValues[1].toInt(), 0, m.groupValues[2]))
        for (m in hourWord.findAll(text)) addTime(m, timeOf(m.groupValues[1].toInt(), 0, ""))
        for (m in half.findAll(text)) {
            val hour = m.groupValues[1].toInt()
            if (hour in 1..12) addTime(m, LocalTime.of((hour + 11) % 12 + if (hour > 12) 12 else 0, 30))
        }
        for (m in noon.findAll(text)) {
            addTime(m, if (m.groupValues[1].lowercase() == "noon") LocalTime.NOON else LocalTime.MIDNIGHT)
        }
        for (m in bareHour.findAll(text)) addTime(m, timeOf(m.groupValues[1].toInt(), 0, ""))

        return dates.sortedBy { it.start } + times.sortedBy { it.start }
    }

    /** The title without the text of the hit, with the spaces tidied. */
    fun remove(text: String, hit: TitleHit): String {
        val start = hit.start.coerceIn(0, text.length)
        val end = hit.end.coerceIn(start, text.length)
        return (text.substring(0, start) + " " + text.substring(end))
            .replace(Regex("\\s{2,}"), " ")
            .trim(' ', ',', '-')
    }

    private fun overlaps(a: TitleHit, b: TitleHit) = a.start < b.end && b.start < a.end

    private fun addHit(list: MutableList<TitleHit>, hit: TitleHit) {
        if (list.none { overlaps(it, hit) }) list.add(hit)
    }

    private fun monthDay(month: Int?, day: Int, year: String, today: LocalDate): LocalDate? {
        if (month == null) return null
        return try {
            if (year.isNotEmpty()) {
                LocalDate.of(year.toInt(), month, day)
            } else {
                val thisYear = LocalDate.of(today.year, month, day)
                if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
            }
        } catch (e: java.time.DateTimeException) {
            null
        }
    }

    private fun timeOf(hour: Int, minute: Int, marker: String): LocalTime? {
        if (minute !in 0..59) return null
        val m = marker.lowercase().replace(".", "")
        return when {
            m == "am" -> if (hour in 1..12) LocalTime.of(hour % 12, minute) else null
            m == "pm" -> if (hour in 1..12) LocalTime.of(hour % 12 + 12, minute) else null
            hour in 0..23 -> LocalTime.of(hour, minute)
            else -> null
        }
    }
}
