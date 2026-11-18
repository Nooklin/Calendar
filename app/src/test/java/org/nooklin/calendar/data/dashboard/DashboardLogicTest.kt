package org.nooklin.calendar.data.dashboard

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nooklin.calendar.data.model.Occurrence

class DashboardLogicTest {
    private val day = LocalDate.of(2026, 11, 9)
    private val settings = DashboardSettings()

    private fun at(hour: Int, minute: Int = 0, plusDays: Long = 0): LocalDateTime = day.plusDays(plusDays).atTime(hour, minute)

    private fun event(start: LocalDateTime, end: LocalDateTime, allDay: Boolean = false, calendarId: Long = 1, title: String = "x") = Occurrence(
        eventId = start.hashCode().toLong(), calendarId = calendarId, calendarName = "c", color = 0, title = title, location = "", notes = "",
        allDay = allDay, start = start, end = end, zoneId = null, repeats = false, rrule = null, sourceStart = start, sourceEnd = end,
        reminders = emptyList()
    )

    @Test
    fun overlappingEventsAreMergedIntoOneBusySpan() {
        val list = listOf(event(at(10), at(12)), event(at(11), at(13)), event(at(15), at(16)))
        val busy = DashboardLogic.busy(list, settings, at(0), at(23))
        assertEquals(2, busy.size)
        assertEquals(at(10), busy[0].start)
        assertEquals(at(13), busy[0].end)
    }

    @Test
    fun anAllDayEventIsBusyOnlyWhenTheSettingSaysSo() {
        val list = listOf(event(at(0), at(0, plusDays = 1), allDay = true))
        assertTrue(DashboardLogic.busy(list, settings, at(0), at(23)).isEmpty())
        assertEquals(1, DashboardLogic.busy(list, settings.copy(allDayBusy = true), at(0), at(23)).size)
    }

    @Test
    fun anEveningIsFreeWithoutAnEventInTheWindow() {
        val list = listOf(event(at(9), at(17)), event(at(23, 30), at(23, 59)))
        assertTrue(DashboardLogic.evening(list, settings, day).free)
    }

    @Test
    fun anEventThatStartsBeforeTheWindowAndRunsIntoItMakesTheEveningBusy() {
        val evening = DashboardLogic.evening(listOf(event(at(17), at(19))), settings, day)
        assertFalse(evening.free)
        assertEquals(at(18), evening.busyFrom)
    }

    @Test
    fun busyFromIsTheStartOfTheFirstEventInTheEvening() {
        val evening = DashboardLogic.evening(listOf(event(at(19, 30), at(21))), settings, day)
        assertEquals(at(19, 30), evening.busyFrom)
    }

    @Test
    fun freeBlocksSkipGapsThatAreTooShort() {
        val busy = DashboardLogic.busy(listOf(event(at(9), at(10)), event(at(10, 30), at(12))), settings, at(8), at(18))
        val blocks = DashboardLogic.freeBlocks(busy, at(8), at(18), 60)
        assertEquals(listOf(at(8) to at(9), at(12) to at(18)), blocks.map { it.start to it.end })
    }

    @Test
    fun weekLoadCountsBusyMinutesPerDay() {
        val loads = DashboardLogic.weekLoad(listOf(event(at(9), at(11)), event(at(10), at(12), calendarId = 2)), settings, listOf(day, day.plusDays(1)))
        assertEquals(listOf(180L, 0L), loads)
    }

    @Test
    fun overlapsFindsEventsThatShareTime() {
        val a = event(at(10), at(12), title = "a")
        val b = event(at(11), at(13), title = "b")
        val c = event(at(13), at(14), title = "c")
        val result = DashboardLogic.overlaps(listOf(c, b, a), at(0), at(0, plusDays = 3))
        assertEquals(1, result.size)
        assertEquals("a", result[0].first.title)
        assertEquals("b", result[0].second.title)
    }

    @Test
    fun anEventThatStartsWhenAnotherEndsIsNoOverlap() {
        assertTrue(DashboardLogic.overlaps(listOf(event(at(10), at(11)), event(at(11), at(12))), at(0), at(0, plusDays = 1)).isEmpty())
    }

    @Test
    fun slotsStartAtTheNextQuarterAndStayInTheDaytime() {
        val now = at(8, 7)
        val slots = DashboardLogic.slots(listOf(event(at(9), at(10))), settings.copy(lookAhead = 7), now, 60, 3)
        assertEquals(at(8, 15) , roundedFirst(now))
        // 08:15 to 09:00 is only 45 minutes: the first hour is 10:00 to 18:00
        assertEquals(at(10), slots[0].start)
        assertEquals(at(8, plusDays = 1), slots[1].start)
        assertEquals(3, slots.size)
    }

    private fun roundedFirst(now: LocalDateTime) = DashboardLogic.roundUp(now, 15)

    @Test
    fun theWeekendOfASundayIsThatSundayAndTheDayBefore() {
        val sunday = LocalDate.of(2026, 11, 15)
        assertEquals(LocalDate.of(2026, 11, 14) to sunday, DashboardLogic.weekend(sunday))
        assertEquals(LocalDate.of(2026, 11, 14) to sunday, DashboardLogic.weekend(day))
    }

    @Test
    fun onlyTheChosenCalendarsCount() {
        val list = listOf(event(at(9), at(10), calendarId = 1), event(at(9), at(10), calendarId = 2))
        assertEquals(1, DashboardLogic.counted(list, settings.copy(calendarIds = setOf(2L))).size)
        assertEquals(2, DashboardLogic.counted(list, settings).size)
    }

    @Test
    fun aMissingCardIsAddedAtTheEndOfTheStoredOrder() {
        val order = DashboardSettings.orderFrom("sync,tonight")
        assertEquals(DashCard.SYNC, order[0])
        assertEquals(DashCard.TONIGHT, order[1])
        assertEquals(DashCard.entries.size, order.size)
        assertNull(DashCard.fromKey("nothing"))
    }
}
