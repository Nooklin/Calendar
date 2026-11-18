package org.nooklin.calendar.ui.calendar

/** An item placed in a lane: [lane] counts from 0, and [lanes] is how many lanes its group of overlapping items has. */
data class Placed<T>(val item: T, val lane: Int, val lanes: Int)

/**
 * Puts events that overlap in time side by side. Items that touch each other, directly or through a third item, form
 * a group; every group gets as many lanes as it needs at most, and each item takes the first lane that is free.
 * [start] and [end] are in minutes; an item ends where the next one in its lane may start.
 */
fun <T> layoutLanes(items: List<T>, start: (T) -> Int, end: (T) -> Int): List<Placed<T>> {
    val sorted = items.sortedWith(compareBy<T> { start(it) }.thenByDescending { end(it) })
    val result = ArrayList<Placed<T>>()
    val group = ArrayList<Pair<T, Int>>()
    val laneEnds = ArrayList<Int>()
    var groupEnd = Int.MIN_VALUE

    fun closeGroup() {
        val lanes = laneEnds.size
        group.forEach { (item, lane) -> result.add(Placed(item, lane, lanes)) }
        group.clear()
        laneEnds.clear()
        groupEnd = Int.MIN_VALUE
    }

    for (item in sorted) {
        val itemStart = start(item)
        val itemEnd = end(item)
        if (group.isNotEmpty() && itemStart >= groupEnd) closeGroup()
        var lane = laneEnds.indexOfFirst { it <= itemStart }
        if (lane == -1) {
            lane = laneEnds.size
            laneEnds.add(itemEnd)
        } else {
            laneEnds[lane] = itemEnd
        }
        group.add(item to lane)
        groupEnd = maxOf(groupEnd, itemEnd)
    }
    if (group.isNotEmpty()) closeGroup()
    return result
}
