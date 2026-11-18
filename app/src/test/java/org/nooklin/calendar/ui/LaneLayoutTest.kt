package org.nooklin.calendar.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.nooklin.calendar.ui.calendar.layoutLanes

class LaneLayoutTest {
    private fun place(vararg ranges: Pair<Int, Int>) = layoutLanes(ranges.toList(), { it.first }, { it.second })

    @Test
    fun eventsThatTouchEndToEndShareNoLane() {
        val placed = place(60 to 120, 120 to 180)
        assertEquals(listOf(1, 1), placed.map { it.lanes })
    }

    @Test
    fun threeOverlappingEventsGetTwoLanesWhenOneFitsBehindAnother() {
        // 10:00-11:30, 11:00-12:00, 11:30-12:30: the third fits in the lane of the first
        val placed = place(600 to 690, 660 to 720, 690 to 750)
        assertEquals(listOf(2, 2, 2), placed.map { it.lanes })
        assertEquals(listOf(0, 1, 0), placed.map { it.lane })
    }

    @Test
    fun separateGroupsDoNotWidenEachOther() {
        val placed = place(0 to 60, 30 to 90, 300 to 360)
        assertEquals(listOf(2, 2, 1), placed.map { it.lanes })
    }
}
