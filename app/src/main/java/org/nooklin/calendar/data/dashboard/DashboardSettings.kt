package org.nooklin.calendar.data.dashboard

/** The cards of the dashboard. [key] is what is stored, so it never changes. */
enum class DashCard(val key: String, val title: String) {
    TONIGHT("tonight", "Tonight"),
    FREE_EVENINGS("evenings", "Free evenings"),
    NEXT("next", "Up next"),
    TODAY("today", "Today in numbers"),
    FREE_BLOCKS("blocks", "Free blocks today"),
    WEEK_LOAD("load", "Week load"),
    WEEKEND("weekend", "Weekend"),
    OVERLAPS("overlaps", "Overlaps"),
    FIND_TIME("find", "Find time"),
    ALL_DAY("allday", "All-day coming up"),
    REMINDERS("reminders", "Reminders today"),
    SYNC("sync", "Sync");

    companion object {
        fun fromKey(key: String): DashCard? = entries.find { it.key == key }
    }
}

/** Everything that can be set for the dashboard. Times are minutes after midnight. */
data class DashboardSettings(
    // The master switch: off hides the dashboard and its button
    val enabled: Boolean = true,
    val order: List<DashCard> = DashCard.entries.toList(),
    val disabled: Set<DashCard> = emptySet(),
    val extended: Set<DashCard> = emptySet(),
    val eveningStart: Int = 18 * 60,
    val eveningEnd: Int = 23 * 60,
    val dayStart: Int = 8 * 60,
    val dayEnd: Int = 18 * 60,
    // Whether an all day event makes the day busy
    val allDayBusy: Boolean = false,
    // The calendars that count; null: all calendars that are visible
    val calendarIds: Set<Long>? = null,
    // The shortest gap in minutes that counts as free time
    val minFree: Int = 60,
    // Days ahead for Overlaps and Find time, and for All-day coming up
    val lookAhead: Int = 14,
    val allDayAhead: Int = 30
) {
    val shown: List<DashCard> get() = order.filter { it !in disabled }

    companion object {
        val LOOK_AHEAD_OPTIONS = listOf(7, 14, 30, 60)
        val ALL_DAY_AHEAD_OPTIONS = listOf(14, 30, 60, 90)
        val MIN_FREE_OPTIONS = listOf(15, 30, 45, 60, 90, 120, 180)

        /** The stored order, with a card that is missing (a card added later) at the end. */
        fun orderFrom(stored: String?): List<DashCard> {
            val read = stored.orEmpty().split(",").mapNotNull { DashCard.fromKey(it) }.distinct()
            return read + DashCard.entries.filter { it !in read }
        }

        fun setFrom(stored: String?): Set<DashCard> = stored.orEmpty().split(",").mapNotNull { DashCard.fromKey(it) }.toSet()
    }
}
