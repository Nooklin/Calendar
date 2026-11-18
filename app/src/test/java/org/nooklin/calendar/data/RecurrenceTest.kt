package org.nooklin.calendar.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.nooklin.calendar.data.recurrence.Recurrence

class RecurrenceTest {
    private fun at(day: Int, month: Int = 1, hour: Int = 9) = LocalDateTime.of(2026, month, day, hour, 0)

    @Test
    fun weeklyOnMondayAndWednesday() {
        val rule = Recurrence.parse("FREQ=WEEKLY;BYDAY=MO,WE")!!
        val result = rule.expand(at(5), at(5), at(14))
        assertEquals(listOf(at(5), at(7), at(12)), result)
    }

    @Test
    fun countStopsTheSeries() {
        val rule = Recurrence.parse("FREQ=DAILY;COUNT=3")!!
        assertEquals(listOf(at(1), at(2), at(3)), rule.expand(at(1), at(1), at(20)))
    }

    @Test
    fun leftOutDayStillCountsTowardsCount() {
        val rule = Recurrence.parse("FREQ=DAILY;COUNT=3")!!
        assertEquals(listOf(at(1), at(3)), rule.expand(at(1), at(1), at(20), setOf(at(2))))
    }

    @Test
    fun secondTuesdayOfTheMonth() {
        val rule = Recurrence.parse("FREQ=MONTHLY;BYDAY=2TU")!!
        val result = rule.expand(at(13), at(1), LocalDateTime.of(2026, 4, 1, 0, 0))
        assertEquals(listOf(at(13), at(10, 2), at(10, 3)), result)
    }

    @Test
    fun untilIsIncludedOnItsDay() {
        val rule = Recurrence.parse("FREQ=DAILY;UNTIL=20260103")!!
        assertEquals(listOf(at(1), at(2), at(3)), rule.expand(at(1), at(1), at(20)))
    }

    @Test
    fun unsupportedRulesGiveNull() {
        assertNull(Recurrence.parse("FREQ=HOURLY"))
        assertNull(Recurrence.parse("FREQ=DAILY;BYSETPOS=1"))
        assertNull(Recurrence.parse("FREQ=DAILY;COUNT=2;UNTIL=20260110"))
        assertNull(Recurrence.parse("FREQ=WEEKLY;WKST=SU"))
    }

    @Test
    fun ruleSurvivesWritingAndReading() {
        val rule = Recurrence.parse("FREQ=WEEKLY;INTERVAL=2;BYDAY=TH")!!
        assertNotNull(Recurrence.parse(rule.toRule()))
        assertEquals(rule, Recurrence.parse(rule.toRule()))
    }

    @Test
    fun exdatesRoundTrip() {
        val text = Recurrence.formatExdate(at(2), allDay = false)
        assertEquals(setOf(at(2)), Recurrence.parseExdates(text))
    }
}
