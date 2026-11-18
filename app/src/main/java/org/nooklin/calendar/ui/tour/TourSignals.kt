package org.nooklin.calendar.ui.tour

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** What a menu that lives inside a screen tells the tour: the tour moves on when the menu opens and when it closes. */
object TourSignals {
    var viewMenuOpen by mutableStateOf(false)
}
