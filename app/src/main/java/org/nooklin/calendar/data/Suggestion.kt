package org.nooklin.calendar.data

import java.text.Normalizer
import java.util.Locale

/** What a new event can take over from an earlier event with a similar title. */
data class Suggestion(
    val title: String,
    val location: String,
    val notes: String,
    val allDay: Boolean,
    // The length of the earlier event; for an all day event a multiple of one day
    val seconds: Long,
    val calendarId: Long,
    val reminders: List<Int>
)

/**
 * When two titles are similar enough. Case, accents, punctuation and extra spaces do not count. Otherwise the pairs
 * of letters of both titles are compared (Dice coefficient): 1.0 is the same, 0.0 has nothing in common.
 */
object TitleMatch {
    const val MIN_LENGTH = 3
    const val MIN_SCORE = 0.8

    fun normalize(title: String): String =
        Normalizer.normalize(title.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() || it == ' ' }
            .trim()
            .replace(Regex("\\s+"), " ")

    /** Both titles must be normalized already. */
    fun score(a: String, b: String): Double {
        if (a.length < MIN_LENGTH || b.length < MIN_LENGTH) return 0.0
        if (a == b) return 1.0
        val pairs = HashMap<String, Int>()
        for (i in 0 until a.length - 1) pairs.merge(a.substring(i, i + 2), 1, Int::plus)
        var shared = 0
        for (i in 0 until b.length - 1) {
            val key = b.substring(i, i + 2)
            val left = pairs[key] ?: 0
            if (left > 0) {
                shared++
                pairs[key] = left - 1
            }
        }
        return 2.0 * shared / ((a.length - 1) + (b.length - 1))
    }
}
