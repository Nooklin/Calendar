package org.nooklin.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleMatchTest {
    private fun score(a: String, b: String) = TitleMatch.score(TitleMatch.normalize(a), TitleMatch.normalize(b))

    @Test
    fun caseAccentsAndPunctuationDoNotCount() {
        assertEquals(1.0, score("Tandarts", "  tandárts. "), 0.0)
    }

    @Test
    fun aSpaceMoreOrLessStillMatches() {
        assertTrue(score("Voetbal training", "Voetbaltraining") >= TitleMatch.MIN_SCORE)
    }

    @Test
    fun differentTitlesDoNotMatch() {
        assertTrue(score("Tandarts", "Tandartsafspraak Jansen") < TitleMatch.MIN_SCORE)
        assertTrue(score("Lunch", "Dinner") < TitleMatch.MIN_SCORE)
    }

    @Test
    fun aTitleThatIsTooShortNeverMatches() {
        assertEquals(0.0, score("Ab", "Ab"), 0.0)
    }
}
