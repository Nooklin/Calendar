package org.nooklin.calendar.data.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** A weekday with an optional position: 2 with TUESDAY is the second Tuesday of the month, -1 with FRIDAY the last Friday. */
data class ByDay(val day: DayOfWeek, val position: Int = 0)

/**
 * A repeat rule in the notation of RFC 5545 (the iCalendar standard), limited to what this app can show:
 * FREQ, INTERVAL, COUNT, UNTIL, BYDAY, BYMONTHDAY and BYMONTH. Weeks start on Monday (WKST=MO).
 *
 * All clock readings here are wall clock times in the zone of the event, so a rule that says 09:00 keeps saying
 * 09:00 when the clocks change.
 */
data class Recurrence(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<ByDay> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val count: Int? = null,
    // The last reading that may still be an occurrence, inclusive
    val until: LocalDateTime? = null
) {

    /**
     * The starts of the occurrences that begin in [from] up to (not including) [to]. [start] is the first
     * occurrence and always counts as occurrence number 1, also when it does not match the rule.
     * [exdates] are left out, but still count towards COUNT, as the standard says.
     */
    fun expand(
        start: LocalDateTime,
        from: LocalDateTime,
        to: LocalDateTime,
        exdates: Set<LocalDateTime> = emptySet()
    ): List<LocalDateTime> {
        val result = ArrayList<LocalDateTime>()
        val limit = count
        val last = until
        if (last != null && start > last) return result
        if (start >= from && start < to && start !in exdates) result.add(start)

        val time = start.toLocalTime()
        val startDate = start.toLocalDate()
        val toDate = to.toLocalDate()
        var counted = 1
        var period = firstPeriod(startDate, from.toLocalDate())
        var guard = 0
        while (guard < MAX_PERIODS) {
            guard++
            if (periodStart(startDate, period) > toDate) break
            for (date in datesInPeriod(startDate, period)) {
                val occurrence = date.atTime(time)
                // The start itself is handled above, readings before it are not part of the series
                if (occurrence <= start) continue
                if (last != null && occurrence > last) return result
                counted++
                if (limit != null && counted > limit) return result
                if (occurrence >= to) return result
                if (occurrence >= from && occurrence !in exdates) result.add(occurrence)
            }
            period++
        }
        return result
    }

    /** The rule as text, for example "FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20271231T235959". */
    fun toRule(): String {
        val parts = ArrayList<String>()
        parts.add("FREQ=$freq")
        if (interval != 1) parts.add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) {
            parts.add("BYDAY=" + byDay.joinToString(",") { (if (it.position != 0) it.position.toString() else "") + dayCode(it.day) })
        }
        if (byMonthDay.isNotEmpty()) parts.add("BYMONTHDAY=" + byMonthDay.joinToString(","))
        if (byMonth.isNotEmpty()) parts.add("BYMONTH=" + byMonth.joinToString(","))
        if (count != null) parts.add("COUNT=$count")
        if (until != null) parts.add("UNTIL=" + until.format(READING))
        return parts.joinToString(";")
    }

    // The first period that can hold something in the window. One period of margin, and from the start when
    // COUNT is set, because the count has to be followed from the first occurrence.
    private fun firstPeriod(startDate: LocalDate, fromDate: LocalDate): Long {
        if (count != null || !fromDate.isAfter(startDate)) return 0L
        val between = when (freq) {
            Freq.DAILY -> ChronoUnit.DAYS.between(startDate, fromDate)
            Freq.WEEKLY -> ChronoUnit.WEEKS.between(mondayOf(startDate), mondayOf(fromDate))
            Freq.MONTHLY -> ChronoUnit.MONTHS.between(startDate.withDayOfMonth(1), fromDate.withDayOfMonth(1))
            Freq.YEARLY -> ChronoUnit.YEARS.between(startDate.withDayOfYear(1), fromDate.withDayOfYear(1))
        }
        return maxOf(0L, between / interval - 1L)
    }

    // The first day that period [k] can contain
    private fun periodStart(startDate: LocalDate, k: Long): LocalDate = when (freq) {
        Freq.DAILY -> startDate.plusDays(k * interval)
        Freq.WEEKLY -> mondayOf(startDate).plusWeeks(k * interval)
        Freq.MONTHLY -> startDate.withDayOfMonth(1).plusMonths(k * interval)
        Freq.YEARLY -> startDate.withDayOfYear(1).plusYears(k * interval)
    }

    // The days of period [k] that match the rule, oldest first
    private fun datesInPeriod(startDate: LocalDate, k: Long): List<LocalDate> = when (freq) {
        Freq.DAILY -> {
            val day = startDate.plusDays(k * interval)
            if (matchesDayFilters(day)) listOf(day) else emptyList()
        }
        Freq.WEEKLY -> {
            val monday = mondayOf(startDate).plusWeeks(k * interval)
            val days = if (byDay.isEmpty()) setOf(startDate.dayOfWeek) else byDay.map { it.day }.toSet()
            (0L..6L).map { monday.plusDays(it) }
                .filter { it.dayOfWeek in days && (byMonth.isEmpty() || it.monthValue in byMonth) }
        }
        Freq.MONTHLY -> {
            val month = YearMonth.from(startDate).plusMonths(k * interval)
            if (byMonth.isNotEmpty() && month.monthValue !in byMonth) emptyList() else datesInMonth(month, startDate)
        }
        Freq.YEARLY -> {
            val year = (startDate.year + k * interval).toInt()
            val months = if (byMonth.isEmpty()) listOf(startDate.monthValue) else byMonth.sorted()
            months.flatMap { datesInMonth(YearMonth.of(year, it), startDate) }
        }
    }

    private fun matchesDayFilters(day: LocalDate): Boolean =
        (byDay.isEmpty() || byDay.any { it.day == day.dayOfWeek }) &&
            (byMonthDay.isEmpty() || byMonthDay.any { it == day.dayOfMonth || (it < 0 && day.lengthOfMonth() + it + 1 == day.dayOfMonth) }) &&
            (byMonth.isEmpty() || day.monthValue in byMonth)

    // The matching days inside one month. A day number that the month does not have (the 31st in April) is skipped.
    private fun datesInMonth(month: YearMonth, startDate: LocalDate): List<LocalDate> {
        val length = month.lengthOfMonth()
        if (byMonthDay.isNotEmpty()) {
            val days = byMonthDay.map { if (it > 0) it else length + it + 1 }
                .filter { it in 1..length }
                .distinct()
                .sorted()
                .map { month.atDay(it) }
            return if (byDay.isEmpty()) days else days.filter { day -> byDay.any { it.day == day.dayOfWeek } }
        }
        if (byDay.isNotEmpty()) {
            return byDay.flatMap { weekdaysInMonth(month, it) }.distinct().sorted()
        }
        return if (startDate.dayOfMonth <= length) listOf(month.atDay(startDate.dayOfMonth)) else emptyList()
    }

    private fun weekdaysInMonth(month: YearMonth, rule: ByDay): List<LocalDate> {
        val all = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { it.dayOfWeek == rule.day }
        return when {
            rule.position == 0 -> all
            rule.position > 0 -> listOfNotNull(all.getOrNull(rule.position - 1))
            else -> listOfNotNull(all.getOrNull(all.size + rule.position))
        }
    }

    companion object {
        private const val MAX_PERIODS = 1_000_000

        private val READING: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
        private val BY_DAY_TOKEN = Regex("^([+-]?[0-9]{1,2})?(MO|TU|WE|TH|FR|SA|SU)$")
        private val DAY_CODES = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY,
            "TH" to DayOfWeek.THURSDAY, "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY,
            "SU" to DayOfWeek.SUNDAY
        )

        private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        private fun dayCode(day: DayOfWeek): String = DAY_CODES.entries.first { it.value == day }.key

        /**
         * Reads a rule. Returns null for anything this app cannot show (an unknown part, a time zone suffix on
         * UNTIL, COUNT together with UNTIL, ...), so that such an event is shown once instead of wrongly.
         */
        fun parse(text: String): Recurrence? {
            var freq: Freq? = null
            var interval = 1
            var count: Int? = null
            var until: LocalDateTime? = null
            val byDay = ArrayList<ByDay>()
            val byMonthDay = ArrayList<Int>()
            val byMonth = ArrayList<Int>()
            for (part in text.split(";")) {
                if (part.isBlank()) continue
                val key = part.substringBefore('=').trim().uppercase()
                val value = part.substringAfter('=', "").trim()
                when (key) {
                    "FREQ" -> freq = runCatching { Freq.valueOf(value.uppercase()) }.getOrNull() ?: return null
                    "INTERVAL" -> interval = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null
                    "COUNT" -> count = value.toIntOrNull()?.takeIf { it >= 1 } ?: return null
                    "UNTIL" -> until = parseReading(value, endOfDay = true) ?: return null
                    "BYDAY" -> for (token in value.split(",")) byDay.add(parseByDay(token) ?: return null)
                    "BYMONTHDAY" -> for (token in value.split(",")) {
                        val number = token.trim().toIntOrNull() ?: return null
                        if (number == 0 || number !in -31..31) return null
                        byMonthDay.add(number)
                    }
                    "BYMONTH" -> for (token in value.split(",")) {
                        val number = token.trim().toIntOrNull() ?: return null
                        if (number !in 1..12) return null
                        byMonth.add(number)
                    }
                    "WKST" -> if (value.uppercase() != "MO") return null
                    else -> return null
                }
            }
            val frequency = freq ?: return null
            if (count != null && until != null) return null
            if (frequency != Freq.MONTHLY && frequency != Freq.YEARLY && byDay.any { it.position != 0 }) return null
            if (frequency == Freq.WEEKLY && byMonthDay.isNotEmpty()) return null
            if (frequency == Freq.YEARLY && byDay.isNotEmpty() && byMonth.isEmpty()) return null
            if (byMonthDay.isNotEmpty() && byDay.any { it.position != 0 }) return null
            return Recurrence(frequency, interval, byDay, byMonthDay, byMonth, count, until)
        }

        private fun parseByDay(token: String): ByDay? {
            val match = BY_DAY_TOKEN.matchEntire(token.trim().uppercase()) ?: return null
            val position = match.groupValues[1].removePrefix("+").ifEmpty { "0" }.toInt()
            val day = DAY_CODES[match.groupValues[2]] ?: return null
            return ByDay(day, position)
        }

        // "yyyyMMdd'T'HHmmss", or just "yyyyMMdd" which becomes the start (or with [endOfDay] the end) of that day
        private fun parseReading(text: String, endOfDay: Boolean): LocalDateTime? {
            val trimmed = text.trim()
            if (trimmed.length == 8) {
                val date = runCatching { LocalDate.parse(trimmed, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() ?: return null
                return if (endOfDay) date.atTime(23, 59, 59) else date.atStartOfDay()
            }
            return runCatching { LocalDateTime.parse(trimmed, READING) }.getOrNull()
        }

        /** Reads the list of left out readings of an event. Entries that cannot be read are skipped. */
        fun parseExdates(text: String): Set<LocalDateTime> =
            text.split(",").mapNotNull { if (it.isBlank()) null else parseReading(it, endOfDay = false) }.toSet()

        /** The notation of one left out reading: a date for an all day event, otherwise date and time. */
        fun formatExdate(reading: LocalDateTime, allDay: Boolean): String =
            if (allDay) reading.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE) else reading.format(READING)
    }
}
