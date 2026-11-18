package org.nooklin.calendar.data.model

/** The reminders of an event are stored as minutes before the start, comma separated. */
object Reminders {
    fun parse(text: String): List<Int> =
        text.split(",").mapNotNull { it.trim().toIntOrNull() }.distinct().sortedDescending()

    fun format(minutes: List<Int>): String = minutes.distinct().sortedDescending().joinToString(",")

    // Timed events
    val TIMED_OPTIONS: List<Int> = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880)

    // All day events: minutes before midnight at the start of the first day. -540 is 09:00 on the day itself
    // and 900 is 09:00 on the day before, 1980 is 09:00 two days before.
    val ALL_DAY_OPTIONS: List<Int> = listOf(-540, 900, 1980)

    fun label(minutes: Int, allDay: Boolean): String {
        if (allDay) {
            return when (minutes) {
                -540 -> "On the day at 09:00"
                900 -> "1 day before at 09:00"
                1980 -> "2 days before at 09:00"
                else -> "$minutes minutes before"
            }
        }
        return when {
            minutes == 0 -> "At the time of the event"
            minutes < 60 -> "$minutes minutes before"
            minutes == 60 -> "1 hour before"
            minutes < 1440 -> "${minutes / 60} hours before"
            minutes == 1440 -> "1 day before"
            minutes % 1440 == 0 -> "${minutes / 1440} days before"
            else -> "$minutes minutes before"
        }
    }
}
