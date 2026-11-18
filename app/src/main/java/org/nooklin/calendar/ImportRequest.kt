package org.nooklin.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** An .ics file that waits to be imported: opened from a file manager, shared to the app, or chosen in the settings. */
object ImportRequest {
    var uri by mutableStateOf<Uri?>(null)

    fun from(intent: Intent?) {
        val found: Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }
        if (found != null) uri = found
    }
}
