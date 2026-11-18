package org.nooklin.calendar.data.ics

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Downloads the .ics text of a web calendar. Only https links, no login, at most 8 MB. */
object IcsFetcher {
    private const val MAX_BYTES = 8 * 1024 * 1024

    sealed interface Fetched {
        data class Text(val text: String) : Fetched
        data class Failed(val reason: String) : Fetched
    }

    /** A webcal:// link is the same address over https. Returns null when the text is not a usable https link. */
    fun normalize(input: String): String? {
        var url = input.trim()
        if (url.startsWith("webcal://", ignoreCase = true)) url = "https://" + url.substring(9)
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return null
        }
        if (uri.host.isNullOrEmpty()) return null
        return url
    }

    /** Blocking: call it from a background thread. */
    fun fetch(url: String): Fetched {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                requestMethod = "GET"
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/calendar, text/plain, */*")
            }
            val code = connection.responseCode
            if (code == 401 || code == 403) {
                return Fetched.Failed("The server asks for a login (HTTP $code). Links that need a password are not supported.")
            }
            if (code == 404) return Fetched.Failed("Nothing found at this link (HTTP 404).")
            if (code !in 200..299) return Fetched.Failed("The server answered HTTP $code.")
            val bytes = read(connection.inputStream) ?: return Fetched.Failed("The calendar is larger than 8 MB.")
            val text = String(bytes, Charsets.UTF_8)
            if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) {
                return Fetched.Failed("The link does not give a calendar (.ics) file.")
            }
            return Fetched.Text(text)
        } catch (e: UnknownHostException) {
            return Fetched.Failed("The server was not found. Check the link and your connection.")
        } catch (e: SocketTimeoutException) {
            return Fetched.Failed("The server did not answer in time.")
        } catch (e: SSLException) {
            return Fetched.Failed("The secure connection failed.")
        } catch (e: IOException) {
            return Fetched.Failed("Could not connect. Check the link and your connection.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun read(input: InputStream): ByteArray? {
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > MAX_BYTES) return null
            }
            return out.toByteArray()
        }
    }
}
