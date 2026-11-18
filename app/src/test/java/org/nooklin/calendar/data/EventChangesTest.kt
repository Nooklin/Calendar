package org.nooklin.calendar.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.toStored

class EventChangesTest {
    private fun at(day: Int, month: Int = 1, hour: Int = 9) = LocalDateTime.of(2026, month, day, hour, 0)

    private fun master(rrule: String?, exdates: String = "") = EventEntity(
        id = 7, calendarId = 1, uid = "u", title = "Weekly", zoneId = "Europe/Amsterdam",
        startLocal = at(5).toStored(), endLocal = at(5, hour = 10).toStored(), rrule = rrule, exdates = exdates
    )

    private fun draft(start: LocalDateTime, end: LocalDateTime, rrule: String?) = EventDraft(
        calendarId = 1, title = "Changed", location = "", notes = "", allDay = false,
        zoneId = "Europe/Amsterdam", start = start, end = end, rrule = rrule, reminders = listOf(10)
    )

    @Test
    fun onlyThisOneLeavesTheSeriesAndMakesASingleEvent() {
        val result = EventChanges.planEdit(
            master("FREQ=WEEKLY"), at(12), draft(at(12, hour = 11), at(12, hour = 12), "FREQ=WEEKLY"), Scope.THIS
        ) { "new" }
        assertEquals("20260112T090000", result.update.single().exdates)
        val single = result.insert.single()
        assertNull(single.rrule)
        assertEquals("new", single.uid)
        assertEquals(at(12, hour = 11).toStored(), single.startLocal)
        assertEquals("10", single.reminders)
    }

    @Test
    fun thisAndFollowingSplitsTheSeriesAndKeepsTheCount() {
        val result = EventChanges.planEdit(
            master("FREQ=WEEKLY;COUNT=5"), at(19),
            draft(at(19, hour = 10), at(19, hour = 11), "FREQ=WEEKLY;COUNT=5"), Scope.FOLLOWING
        ) { "new" }
        assertEquals("FREQ=WEEKLY;UNTIL=20260112T090000", result.update.single().rrule)
        assertEquals("FREQ=WEEKLY;COUNT=3", result.insert.single().rrule)
        assertEquals(at(19, hour = 10).toStored(), result.insert.single().startLocal)
    }

    @Test
    fun thisAndFollowingOnTheFirstEventChangesTheWholeSeries() {
        val result = EventChanges.planEdit(
            master("FREQ=WEEKLY"), at(5), draft(at(5, hour = 8), at(5, hour = 9), "FREQ=WEEKLY"), Scope.FOLLOWING
        ) { "new" }
        assertTrue(result.insert.isEmpty())
        assertEquals(at(5, hour = 8).toStored(), result.update.single().startLocal)
        assertEquals(7L, result.update.single().id)
    }

    @Test
    fun allEventsMovesTheWholeSeriesAndItsLeftOutDays() {
        val result = EventChanges.planEdit(
            master("FREQ=WEEKLY", "20260119T090000"), at(12),
            draft(at(12, hour = 10), at(12, hour = 11), "FREQ=WEEKLY"), Scope.ALL
        ) { "new" }
        val changed = result.update.single()
        assertEquals(at(5, hour = 10).toStored(), changed.startLocal)
        assertEquals(at(5, hour = 11).toStored(), changed.endLocal)
        assertEquals("20260119T100000", changed.exdates)
        assertEquals("u", changed.uid)
    }

    @Test
    fun deleteOnlyThisLeavesOutThatDay() {
        val result = EventChanges.planDelete(master("FREQ=WEEKLY"), at(12), Scope.THIS)
        assertEquals("20260112T090000", result.update.single().exdates)
        assertTrue(result.delete.isEmpty())
    }

    @Test
    fun deleteThisAndFollowingStopsTheSeriesBeforeIt() {
        val result = EventChanges.planDelete(master("FREQ=WEEKLY"), at(19), Scope.FOLLOWING)
        assertEquals("FREQ=WEEKLY;UNTIL=20260112T090000", result.update.single().rrule)
    }

    @Test
    fun deleteAllAndDeleteOfASingleEventRemoveTheRow() {
        assertEquals(listOf(7L), EventChanges.planDelete(master("FREQ=WEEKLY"), at(12), Scope.ALL).delete)
        assertEquals(listOf(7L), EventChanges.planDelete(master(null), at(5), Scope.THIS).delete)
    }
}
