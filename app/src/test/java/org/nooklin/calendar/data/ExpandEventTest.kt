package org.nooklin.calendar.data

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.expandEvent
import org.nooklin.calendar.data.model.toStored

class ExpandEventTest {
    private val calendar = CalendarEntity(id = 1, name = "c", color = 0)
    private val utc = ZoneId.of("UTC")

    private fun event(
        start: LocalDateTime,
        end: LocalDateTime,
        zoneId: String? = "Europe/Amsterdam",
        allDay: Boolean = false,
        rrule: String? = null
    ) = EventEntity(
        id = 1, calendarId = 1, uid = "u", title = "t", allDay = allDay,
        zoneId = if (allDay) null else zoneId,
        startLocal = start.toStored(), endLocal = end.toStored(), rrule = rrule
    )

    @Test
    fun weeklyTenOClockStaysTenOClockOverTheClockChange() {
        // Amsterdam goes back to winter time on 2026-10-25
        val first = LocalDateTime.of(2026, 10, 19, 10, 0)
        val e = event(first, first.plusMinutes(30), rrule = "FREQ=WEEKLY;COUNT=2")
        val onUtcPhone = expandEvent(e, calendar, LocalDateTime.of(2026, 10, 18, 0, 0), LocalDateTime.of(2026, 11, 2, 0, 0), utc)
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 19, 8, 0), LocalDateTime.of(2026, 10, 26, 9, 0)),
            onUtcPhone.map { it.start }
        )
        val onLocalPhone = expandEvent(e, calendar, LocalDateTime.of(2026, 10, 18, 0, 0), LocalDateTime.of(2026, 11, 2, 0, 0), ZoneId.of("Europe/Amsterdam"))
        assertEquals(listOf(10, 10), onLocalPhone.map { it.start.hour })
    }

    @Test
    fun eventEndingAtMidnightBelongsToOneDayOnly() {
        val start = LocalDateTime.of(2026, 3, 10, 22, 0)
        val e = event(start, LocalDateTime.of(2026, 3, 11, 0, 0))
        val o = expandEvent(e, calendar, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0), ZoneId.of("Europe/Amsterdam")).single()
        assertFalse(o.multiDay)
        assertTrue(o.touches(start.toLocalDate()))
        assertFalse(o.touches(start.toLocalDate().plusDays(1)))
    }

    @Test
    fun allDayEventCoversItsDaysOnly() {
        val e = event(LocalDateTime.of(2026, 5, 4, 0, 0), LocalDateTime.of(2026, 5, 7, 0, 0), allDay = true)
        val o = expandEvent(e, calendar, LocalDateTime.of(2026, 5, 1, 0, 0), LocalDateTime.of(2026, 6, 1, 0, 0), utc).single()
        assertTrue(o.touches(o.startDate.plusDays(2)))
        assertFalse(o.touches(o.startDate.plusDays(3)))
    }

    @Test
    fun floatingEventKeepsItsClockTimeInEveryZone() {
        val start = LocalDateTime.of(2026, 6, 1, 9, 0)
        val e = event(start, start.plusMinutes(30), zoneId = null)
        val tokyo = expandEvent(e, calendar, LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 6, 2, 0, 0), ZoneId.of("Asia/Tokyo")).single()
        assertEquals(start, tokyo.start)
    }

    @Test
    fun eventOutsideTheWindowIsLeftOut() {
        val start = LocalDateTime.of(2026, 6, 1, 9, 0)
        val e = event(start, start.plusHours(1))
        assertTrue(expandEvent(e, calendar, LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0), utc).isEmpty())
    }
}
