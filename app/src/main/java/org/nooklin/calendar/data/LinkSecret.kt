package org.nooklin.calendar.data

import java.net.URI
import org.nooklin.calendar.data.caldav.SecretBox

/**
 * The address of a calendar that follows a link often holds a secret token, so it is stored encrypted (with the same
 * Keystore key as the CalDAV password). An address stored before this existed is still read as it is, and is
 * encrypted at the next start of the app.
 */
object LinkSecret {
    private const val PREFIX = "enc:"

    fun protect(url: String): String = if (url.startsWith(PREFIX)) url else PREFIX + SecretBox.encrypt(url)

    fun isProtected(stored: String): Boolean = stored.startsWith(PREFIX)

    /** The address, or null when it can no longer be decrypted (for example after a restore on another phone). */
    fun reveal(stored: String): String? = if (isProtected(stored)) SecretBox.decrypt(stored.removePrefix(PREFIX)) else stored

    /** Only the name of the server, for showing. */
    fun hostOf(stored: String): String? = reveal(stored)?.let { runCatching { URI(it).host }.getOrNull() }
}
