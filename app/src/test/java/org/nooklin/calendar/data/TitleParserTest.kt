package org.nooklin.calendar.data

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleParserTest {
    // A Monday
    private val today = LocalDate.of(2026, 11, 9)

    private fun date(text: String) = TitleParser.parse(text, today).firstOrNull { it.isDate }?.date
    private fun time(text: String) = TitleParser.parse(text, today).firstOrNull { !it.isDate }?.time

    @Test
    fun englishSentence() {
        val text = "Meeting with Eric at 2 PM on July 14"
        assertEquals(LocalDate.of(2027, 7, 14), date(text))
        assertEquals(LocalTime.of(14, 0), time(text))
    }

    @Test
    fun removingBothLeavesTheTitle() {
        var text = "Meeting with Eric at 2 PM on July 14"
        text = TitleParser.remove(text, TitleParser.parse(text, today).first { !it.isDate })
        assertEquals("Meeting with Eric on July 14", text)
        text = TitleParser.remove(text, TitleParser.parse(text, today).first { it.isDate })
        assertEquals("Meeting with Eric", text)
    }

    @Test
    fun dutchSentence() {
        val text = "Overleg met Eric om 14:30 op 14 juli"
        assertEquals(LocalDate.of(2027, 7, 14), date(text))
        assertEquals(LocalTime.of(14, 30), time(text))
    }

    @Test
    fun relativeDays() {
        assertEquals(today, date("lunch today"))
        assertEquals(today.plusDays(1), date("tandarts morgen"))
        assertEquals(today.plusDays(2), date("lunch day after tomorrow"))
        assertEquals(today.plusDays(14), date("review in 2 weeks"))
        assertEquals(today.plusDays(3), date("borrel over 3 dagen"))
    }

    @Test
    fun weekdayMeansNextOne() {
        assertEquals(LocalDate.of(2026, 11, 13), date("Dinner on Friday"))
        assertEquals(LocalDate.of(2026, 11, 16), date("Gym next monday"))
    }

    @Test
    fun dateWithoutYearThatPassedMovesToNextYear() {
        assertEquals(LocalDate.of(2026, 12, 3), date("Party Dec 3"))
        assertEquals(LocalDate.of(2027, 3, 2), date("Party 2 March"))
        assertEquals(LocalDate.of(2026, 7, 14), date("Party July 14 2026"))
    }

    @Test
    fun numericDates() {
        assertEquals(LocalDate.of(2026, 12, 25), date("Kerst 25/12"))
        assertEquals(LocalDate.of(2027, 7, 14), date("Trip 7/14"))
        assertEquals(LocalDate.of(2026, 12, 3), date("Borrel 3-12"))
    }

    @Test
    fun times() {
        assertEquals(LocalTime.of(9, 5), time("Run at 9:05"))
        assertEquals(LocalTime.of(0, 30), time("Call 12:30 am"))
        assertEquals(LocalTime.of(12, 15), time("Call 12:15 pm"))
        assertEquals(LocalTime.of(15, 0), time("Koffie 15 uur"))
        assertEquals(LocalTime.of(2, 30), time("Etentje half 3"))
        assertEquals(LocalTime.NOON, time("Lunch at noon"))
        assertEquals(LocalTime.of(18, 0), time("Dinner at 18"))
    }

    @Test
    fun nothingFound() {
        assertNull(date("Weekly planning"))
        assertNull(time("Project 2.0 release"))
        assertNull(date("Call 31 feb"))
        assertNull(time("Call at 25"))
    }
}
