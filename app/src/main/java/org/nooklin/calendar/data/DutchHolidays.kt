package org.nooklin.calendar.data

import java.time.LocalDate

/** The Dutch public holidays, worked out from the date of Easter, so no data or internet is needed. */
object DutchHolidays {
    /** The calendar id of the holidays; a real calendar never has a negative id. */
    const val CALENDAR_ID = -1L
    const val CALENDAR_NAME = "Dutch holidays"

    // Orange, as an ARGB number
    const val COLOR = 0xFFFF9F0A.toInt()

    data class Holiday(val date: LocalDate, val name: String)

    /** The holidays from [from] up to (not including) [toExclusive], oldest first. */
    fun between(from: LocalDate, toExclusive: LocalDate): List<Holiday> =
        (from.year..toExclusive.year)
            .flatMap { inYear(it) }
            .filter { !it.date.isBefore(from) && it.date.isBefore(toExclusive) }
            .sortedBy { it.date }

    fun inYear(year: Int): List<Holiday> {
        val easter = easter(year)
        // King's Day is on 27 April, or on the 26th when the 27th is a Sunday
        val kingsDay = LocalDate.of(year, 4, 27).let { if (it.dayOfWeek == java.time.DayOfWeek.SUNDAY) it.minusDays(1) else it }
        return listOf(
            Holiday(LocalDate.of(year, 1, 1), "Nieuwjaarsdag"),
            Holiday(easter.minusDays(2), "Goede Vrijdag"),
            Holiday(easter, "Eerste Paasdag"),
            Holiday(easter.plusDays(1), "Tweede Paasdag"),
            Holiday(kingsDay, "Koningsdag"),
            Holiday(LocalDate.of(year, 5, 5), "Bevrijdingsdag"),
            Holiday(easter.plusDays(39), "Hemelvaartsdag"),
            Holiday(easter.plusDays(49), "Eerste Pinksterdag"),
            Holiday(easter.plusDays(50), "Tweede Pinksterdag"),
            Holiday(LocalDate.of(year, 12, 25), "Eerste Kerstdag"),
            Holiday(LocalDate.of(year, 12, 26), "Tweede Kerstdag")
        )
    }

    /** Easter Sunday by the Gregorian computus (Meeus, Jones and Butcher). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }
}
