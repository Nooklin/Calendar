package org.nooklin.calendar.reminder

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.toStored

class ReminderPlannerTest {
    private val zone = ZoneId.of("Europe/Amsterdam")
    private val calendar = CalendarEntity(id = 1, name = "c", color = 0)

    private fun event(
        start: LocalDateTime,
        end: LocalDateTime,
        reminders: String,
        allDay: Boolean = false,
        rrule: String? = null
    ) = EventEntity(
        id = 1, calendarId = 1, uid = "u", title = "t", allDay = allDay,
        zoneId = if (allDay) null else "Europe/Amsterdam",
        startLocal = start.toStored(), endLocal = end.toStored(), rrule = rrule, reminders = reminders
    )

    private fun instant(time: LocalDateTime) = time.atZone(zone).toInstant()

    @Test
    fun twoRemindersOfOneEventComeOutInOrder() {
        val start = LocalDateTime.of(2026, 3, 10, 10, 0)
        val e = event(start, start.plusHours(1), "10,60")
        val result = ReminderPlanner.triggers(listOf(e), listOf(calendar), instant(start).minusSeconds(7200), instant(start), zone)
        assertEquals(listOf(60, 10), result.map { it.minutes })
        assertEquals(instant(start).minusSeconds(3600), result[0].trigger)
        assertEquals(instant(start).minusSeconds(600), result[1].trigger)
    }

    @Test
    fun travelTimeMakesTheReminderEarlier() {
        val start = LocalDateTime.of(2026, 3, 10, 10, 0)
        val e = event(start, start.plusHours(1), "10").copy(travelMinutes = 30)
        val result = ReminderPlanner.triggers(listOf(e), listOf(calendar), instant(start).minusSeconds(7200), instant(start), zone)
        assertEquals(1, result.size)
        assertEquals(instant(start).minusSeconds(40 * 60L), result[0].trigger)
    }

    @Test
    fun theMomentOfTheLastCheckDoesNotCountAgain() {
        val start = LocalDateTime.of(2026, 3, 10, 10, 0)
        val e = event(start, start.plusHours(1), "10")
        val trigger = instant(start).minusSeconds(600)
        assertTrue(ReminderPlanner.triggers(listOf(e), listOf(calendar), trigger, trigger.plusSeconds(60), zone).isEmpty())
    }

    @Test
    fun allDayReminderUsesTheClockOfThePhone() {
        val day = LocalDateTime.of(2026, 3, 10, 0, 0)
        val e = event(day, day.plusDays(1), "-540", allDay = true)
        val result = ReminderPlanner.triggers(listOf(e), listOf(calendar), instant(day).minusSeconds(60), instant(day.plusDays(1)), zone)
        assertEquals(instant(LocalDateTime.of(2026, 3, 10, 9, 0)), result.single().trigger)
    }

    @Test
    fun allDayReminderOnTheDayBeforeStaysAtNineOverAClockChange() {
        // Amsterdam changes to summer time in the night of 2026-03-29
        val day = LocalDateTime.of(2026, 3, 29, 0, 0)
        val e = event(day, day.plusDays(1), "900", allDay = true)
        val result = ReminderPlanner.triggers(listOf(e), listOf(calendar), instant(day.minusDays(2)), instant(day), zone)
        assertEquals(instant(LocalDateTime.of(2026, 3, 28, 9, 0)), result.single().trigger)
    }

    @Test
    fun weeklyReminderStaysAtTheSameClockTimeOverTheClockChange() {
        val first = LocalDateTime.of(2026, 10, 19, 10, 0)
        val e = event(first, first.plusMinutes(30), "0", rrule = "FREQ=WEEKLY;COUNT=2")
        val result = ReminderPlanner.triggers(listOf(e), listOf(calendar), instant(first).minusSeconds(60), instant(first.plusDays(8)), zone)
        assertEquals(
            listOf(instant(first), instant(LocalDateTime.of(2026, 10, 26, 10, 0))),
            result.map { it.trigger }
        )
    }

    @Test
    fun eventsWithoutRemindersGiveNothing() {
        val start = LocalDateTime.of(2026, 3, 10, 10, 0)
        val e = event(start, start.plusHours(1), "")
        assertTrue(ReminderPlanner.triggers(listOf(e), listOf(calendar), instant(start).minusSeconds(7200), instant(start), zone).isEmpty())
    }
}
