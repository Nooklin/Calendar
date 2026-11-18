package org.nooklin.calendar.data.ics

import java.time.Instant
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.toStored

class IcsTest {
    private fun file(vararg events: String): String =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nX-WR-CALNAME:Work\r\n" + events.joinToString("") + "END:VCALENDAR\r\n"

    private fun event(vararg lines: String): String = "BEGIN:VEVENT\r\n" + lines.joinToString("") { it + "\r\n" } + "END:VEVENT\r\n"

    @Test
    fun readsAWeeklyEventWithZoneLeftOutDayAndReminder() {
        val text = file(
            event(
                "UID:a1",
                "DTSTART;TZID=Europe/Amsterdam:20260105T090000",
                "DTEND;TZID=Europe/Amsterdam:20260105T100000",
                "SUMMARY:Stand\\, up",
                "RRULE:FREQ=WEEKLY;UNTIL=20260201T225959Z;WKST=MO",
                "EXDATE;TZID=Europe/Amsterdam:20260112T090000",
                "BEGIN:VALARM", "TRIGGER:-PT10M", "ACTION:DISPLAY", "END:VALARM"
            )
        )
        val parsed = IcsParser.parse(text)
        assertEquals("Work", parsed.name)
        val e = parsed.events.single()
        assertEquals("Stand, up", e.title)
        assertEquals("Europe/Amsterdam", e.zoneId)
        assertEquals(LocalDateTime.of(2026, 1, 5, 9, 0), e.start)
        assertEquals(LocalDateTime.of(2026, 1, 5, 10, 0), e.end)
        assertEquals("FREQ=WEEKLY;UNTIL=20260201T235959", e.rrule)
        assertEquals(listOf(LocalDateTime.of(2026, 1, 12, 9, 0)), e.exdates)
        assertEquals(listOf(10), e.reminders)
    }

    @Test
    fun readsAllDayUtcAndFloatingEvents() {
        val parsed = IcsParser.parse(
            file(
                event("UID:d", "DTSTART;VALUE=DATE:20260310", "DTEND;VALUE=DATE:20260312", "SUMMARY:Trip"),
                event("UID:u", "DTSTART:20260310T120000Z", "DURATION:PT90M", "SUMMARY:Call"),
                event("UID:f", "DTSTART:20260310T080000", "SUMMARY:Floating")
            )
        )
        val byUid = parsed.events.associateBy { it.uid }
        assertTrue(byUid.getValue("d").allDay)
        assertNull(byUid.getValue("d").zoneId)
        assertEquals(LocalDateTime.of(2026, 3, 12, 0, 0), byUid.getValue("d").end)
        assertEquals("UTC", byUid.getValue("u").zoneId)
        assertEquals(LocalDateTime.of(2026, 3, 10, 13, 30), byUid.getValue("u").end)
        assertNull(byUid.getValue("f").zoneId)
        assertEquals(byUid.getValue("f").start, byUid.getValue("f").end)
    }

    @Test
    fun cancelledEventsAreSkippedAndUnknownZonesBecomeFloating() {
        val parsed = IcsParser.parse(
            file(
                event("UID:c", "DTSTART:20260310T080000", "STATUS:CANCELLED", "SUMMARY:Gone"),
                event("UID:w", "DTSTART;TZID=W. Europe Standard Time:20260310T080000", "SUMMARY:Windows"),
                event("UID:x", "SUMMARY:No start")
            )
        )
        assertEquals(2, parsed.skipped)
        assertEquals(1, parsed.simplified)
        assertNull(parsed.events.single().zoneId)
    }

    @Test
    fun aChangedRepeatBecomesAnEventOfItsOwn() {
        val parsed = IcsParser.parse(
            file(
                event("UID:m1", "DTSTART;TZID=Europe/Amsterdam:20260105T090000", "DTEND;TZID=Europe/Amsterdam:20260105T100000", "SUMMARY:Weekly", "RRULE:FREQ=WEEKLY"),
                event(
                    "UID:m1", "RECURRENCE-ID;TZID=Europe/Amsterdam:20260112T090000",
                    "DTSTART;TZID=Europe/Amsterdam:20260112T110000", "DTEND;TZID=Europe/Amsterdam:20260112T120000", "SUMMARY:Moved"
                )
            )
        )
        assertEquals(2, parsed.events.size)
        val master = parsed.events.first { it.title == "Weekly" }
        val moved = parsed.events.first { it.title == "Moved" }
        assertEquals(listOf(LocalDateTime.of(2026, 1, 12, 9, 0)), master.exdates)
        assertNull(moved.rrule)
        assertEquals("m1-r1", moved.uid)
    }

    @Test
    fun remindersAreReadRelativeToTheStartOnly() {
        val parsed = IcsParser.parse(
            file(
                event("UID:a", "DTSTART;VALUE=DATE:20260310", "SUMMARY:Day", "BEGIN:VALARM", "TRIGGER:PT9H", "END:VALARM"),
                event(
                    "UID:t", "DTSTART:20260310T080000", "SUMMARY:Timed",
                    "BEGIN:VALARM", "TRIGGER:PT5M", "END:VALARM",
                    "BEGIN:VALARM", "TRIGGER;VALUE=DATE-TIME:20260310T070000Z", "END:VALARM"
                )
            )
        )
        val byUid = parsed.events.associateBy { it.uid }
        assertEquals(listOf(-540), byUid.getValue("a").reminders)
        assertTrue(byUid.getValue("t").reminders.isEmpty())
    }

    @Test
    fun writingAndReadingGivesTheSameEvent() {
        val start = LocalDateTime.of(2026, 1, 5, 9, 0)
        val entity = EventEntity(
            id = 1, calendarId = 1, uid = "u1", title = "Weekly; with, special\nchars", location = "Room 2",
            notes = "Line one\nLine two", zoneId = "Europe/Amsterdam",
            startLocal = start.toStored(), endLocal = start.plusHours(1).toStored(),
            rrule = "FREQ=WEEKLY;UNTIL=20260201T235959", exdates = "20260112T090000", reminders = "10"
        )
        val text = IcsWriter.write("Work", listOf(entity), Instant.parse("2026-01-01T00:00:00Z"), 2026)
        assertTrue(text.contains("BEGIN:VTIMEZONE"))
        assertTrue(text.contains("TZID:Europe/Amsterdam"))
        assertTrue(text.contains("DTSTART;TZID=Europe/Amsterdam:20260105T090000"))
        val back = IcsParser.parse(text).events.single()
        assertEquals(entity.title, back.title)
        assertEquals(entity.notes, back.notes)
        assertEquals(start, back.start)
        assertEquals(start.plusHours(1), back.end)
        assertEquals("Europe/Amsterdam", back.zoneId)
        assertEquals(entity.rrule, back.rrule)
        assertEquals(listOf(LocalDateTime.of(2026, 1, 12, 9, 0)), back.exdates)
        assertEquals(listOf(10), back.reminders)
    }

    @Test
    fun travelTimeSurvivesWritingAndReading() {
        val start = LocalDateTime.of(2026, 1, 5, 9, 0)
        val entity = EventEntity(
            calendarId = 1, uid = "u3", title = "Dentist", zoneId = null, travelMinutes = 90,
            startLocal = start.toStored(), endLocal = start.plusHours(1).toStored()
        )
        val text = IcsWriter.write("Work", listOf(entity), Instant.parse("2026-01-01T00:00:00Z"), 2026)
        assertTrue(text.contains("X-NOOKLIN-TRAVEL:90"))
        assertEquals(90, IcsParser.parse(text).events.single().travelMinutes)
    }

    @Test
    fun longLinesAreFoldedAtSeventyFiveOctets() {
        val start = LocalDateTime.of(2026, 1, 5, 9, 0)
        val entity = EventEntity(
            calendarId = 1, uid = "u2", title = "é".repeat(120), zoneId = null,
            startLocal = start.toStored(), endLocal = start.toStored()
        )
        val text = IcsWriter.write("Work", listOf(entity), Instant.parse("2026-01-01T00:00:00Z"), 2026)
        text.split("\r\n").forEach { assertTrue(it.toByteArray(Charsets.UTF_8).size <= 75) }
        assertEquals(entity.title, IcsParser.parse(text).events.single().title)
    }
}
