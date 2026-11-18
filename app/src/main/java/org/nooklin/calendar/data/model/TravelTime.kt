package org.nooklin.calendar.data.model

/** The choices for the travel time of an event, in minutes. 0 is none. */
val TRAVEL_CHOICES: List<Int> = listOf(0, 5, 15, 30, 60, 90, 120)

/** For example "30 min", "1 hour", "1.5 hours". */
fun travelLabel(minutes: Int): String = when {
    minutes <= 0 -> "None"
    minutes < 60 -> "$minutes min"
    minutes == 60 -> "1 hour"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    minutes == 90 -> "1.5 hours"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
