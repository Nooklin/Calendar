package org.nooklin.calendar

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A tap on a widget asks the app to open on a day. The app root picks it up and clears it. */
object LaunchRequest {
    const val EXTRA_EPOCH_DAY = "org.nooklin.calendar.EPOCH_DAY"

    var epochDay by mutableStateOf<Long?>(null)

    fun from(intent: Intent?) {
        val day = intent?.getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE) ?: return
        if (day != Long.MIN_VALUE) {
            epochDay = day
            intent.removeExtra(EXTRA_EPOCH_DAY)
        }
    }
}
