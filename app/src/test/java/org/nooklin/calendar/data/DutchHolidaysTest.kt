package org.nooklin.calendar.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DutchHolidaysTest {
    @Test
    fun easterDates() {
        assertEquals(LocalDate.of(2024, 3, 31), DutchHolidays.easter(2024))
        assertEquals(LocalDate.of(2025, 4, 20), DutchHolidays.easter(2025))
        assertEquals(LocalDate.of(2026, 4, 5), DutchHolidays.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), DutchHolidays.easter(2027))
    }

    @Test
    fun holidaysOf2026() {
        val byName = DutchHolidays.inYear(2026).associate { it.name to it.date }
        assertEquals(LocalDate.of(2026, 4, 3), byName["Goede Vrijdag"])
        assertEquals(LocalDate.of(2026, 4, 27), byName["Koningsdag"])
        assertEquals(LocalDate.of(2026, 5, 14), byName["Hemelvaartsdag"])
        assertEquals(LocalDate.of(2026, 5, 24), byName["Eerste Pinksterdag"])
        assertEquals(LocalDate.of(2026, 5, 25), byName["Tweede Pinksterdag"])
    }

    @Test
    fun kingsDayMovesWhenTheDayIsASunday() {
        // 27 April 2025 is a Sunday
        assertEquals(LocalDate.of(2025, 4, 26), DutchHolidays.inYear(2025).first { it.name == "Koningsdag" }.date)
    }

    @Test
    fun windowIsHalfOpen() {
        val found = DutchHolidays.between(LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 26))
        assertEquals(listOf("Eerste Kerstdag"), found.map { it.name })
    }
}
