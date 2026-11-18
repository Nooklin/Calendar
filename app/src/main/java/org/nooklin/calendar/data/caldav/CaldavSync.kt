package org.nooklin.calendar.data.caldav

import android.content.Context
import androidx.room.withTransaction
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.time.LocalDate
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.nooklin.calendar.data.db.CalendarDatabase
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.CaldavAccountEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.ics.IcsEvent
import org.nooklin.calendar.data.ics.IcsParser
import org.nooklin.calendar.data.ics.IcsWriter
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.model.toStored
import org.nooklin.calendar.data.recurrence.Recurrence
import org.nooklin.calendar.reminder.ReminderScheduler
import org.nooklin.calendar.widget.WidgetUpdater

/** The result of connecting a CalDAV account: how many calendars were added, or why it did not work. */
sealed interface ConnectResult {
    data class Ok(val added: Int) : ConnectResult
    data class Failed(val reason: String) : ConnectResult
}

/**
 * Keeps calendars in step with a CalDAV server (RFC 4791): finds the calendars, downloads what changed, and sends what
 * was changed on this phone. One resource on the server holds one event with its repeat rule. When a change here and a
 * change on the server meet, the change made here wins.
 */
class CaldavSync(context: Context, private val db: CalendarDatabase) {
    private val appContext = context.applicationContext
    private val calendarDao = db.calendarDao()
    private val eventDao = db.eventDao()
    private val caldavDao = db.caldavDao()

    private class Found(val url: String, val name: String, val color: Int?)

    /** Logs in, finds the calendars with events and adds the ones this phone does not have yet. */
    suspend fun connect(rawServer: String, username: String, password: String, service: CaldavService, colors: List<Int>): ConnectResult {
        val text = service.address(rawServer)
        val withScheme = if (text.contains("://")) text else "https://$text"
        if (withScheme.startsWith("http://", ignoreCase = true)) {
            return ConnectResult.Failed("Use an address that starts with https://. A password is not sent over a plain connection.")
        }
        val start = withScheme.toHttpUrlOrNull() ?: return ConnectResult.Failed("This is not a valid server address.")
        val found = try {
            withContext(Dispatchers.IO) { discover(DavClient(username.trim(), password), start) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ConnectResult.Failed(describe(e))
        }
        if (found.isEmpty()) return ConnectResult.Failed("No calendars for events were found on this server.")

        val serverUrl = start.toString()
        val user = username.trim()
        val account = caldavDao.findAccount(serverUrl, user)
        val accountId = if (account != null) {
            caldavDao.setSecret(account.id, SecretBox.encrypt(password))
            account.id
        } else {
            caldavDao.insertAccount(CaldavAccountEntity(serverUrl = serverUrl, username = user, secret = SecretBox.encrypt(password), service = service.key))
        }
        val have = calendarDao.getAll().filter { it.accountId == accountId }.mapNotNull { it.remoteUrl }.toSet()
        val added = ArrayList<Long>()
        for (item in found) {
            if (item.url in have) continue
            val count = calendarDao.count()
            val id = calendarDao.insert(
                CalendarEntity(
                    name = item.name,
                    color = item.color ?: colors[count % colors.size],
                    sortOrder = count,
                    accountId = accountId,
                    remoteUrl = item.url
                )
            )
            added.add(id)
        }
        // A calendar that was already there is brought up to date as well: the password may have been the problem
        val all = calendarDao.getAll().filter { it.accountId == accountId }.map { it.id }
        all.forEach { sync(it) }
        return ConnectResult.Ok(added.size)
    }

    /** Sends what was changed here, then downloads what changed on the server. */
    suspend fun sync(calendarId: Long) {
        mutex.withLock { run(calendarId, pull = true) }
    }

    /** Sends what was changed here and does not download. For right after a change; a failed connection is tried again by [sync]. */
    suspend fun push(calendarId: Long) {
        mutex.withLock { run(calendarId, pull = false) }
    }

    private suspend fun run(calendarId: Long, pull: Boolean) {
        val calendar = calendarDao.get(calendarId) ?: return
        val accountId = calendar.accountId ?: return
        val remote = calendar.remoteUrl ?: return
        val account = caldavDao.account(accountId) ?: return
        val password = SecretBox.decrypt(account.secret)
        if (password == null) {
            if (pull) calendarDao.setSync(calendarId, calendar.lastSync, "Sign in again: connect this account once more.")
            return
        }
        val client = DavClient(account.username, password)
        try {
            val base = remote.toHttpUrl()
            val problem = withContext(Dispatchers.IO) { pushChanges(client, calendar, base) }
            if (!pull) {
                if (problem != null) calendarDao.setSync(calendarId, calendar.lastSync, problem)
                return
            }
            val unread = pullChanges(client, calendar, base)
            val error = problem ?: if (unread > 0) "$unread events could not be read and were left out." else null
            calendarDao.setSync(calendarId, System.currentTimeMillis() / 1000, error)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed connection after a change is not shown: the change waits and goes with the next sync
            if (pull || e is DavException) calendarDao.setSync(calendarId, calendar.lastSync, describe(e))
        }
    }

    // Sends the removals and the changed events. Returns what the server refused, or null when all went well.
    private suspend fun pushChanges(client: DavClient, calendar: CalendarEntity, base: HttpUrl): String? {
        var problem: String? = null
        for (deletion in caldavDao.deletions(calendar.id)) {
            val conditions = if (deletion.etag != null) mapOf("If-Match" to deletion.etag) else emptyMap()
            var code = client.send("DELETE", deletion.href, headers = conditions).code
            // The resource changed on the server since this phone saw it: removing it here wins
            if (code == 412) code = client.send("DELETE", deletion.href).code
            when {
                code in 200..299 || code == 404 -> caldavDao.deleteDeletion(deletion.id)
                code == 401 -> throw DavException(codeText(401))
                code == 403 || code == 405 -> {
                    caldavDao.deleteDeletion(deletion.id)
                    problem = "The server does not allow removing events from this calendar."
                }
                else -> problem = "Removing an event failed: ${codeText(code)}"
            }
        }
        for (event in eventDao.dirtyOf(calendar.id)) {
            val body = IcsWriter.write(calendar.name, listOf(event), Instant.now(), LocalDate.now().year)
            val href = event.href ?: base.newBuilder().addPathSegment(event.uid + ".ics").build().toString()
            var response = put(client, href, body, create = event.href == null, etag = event.etag)
            if (response.code == 412) {
                // Someone changed the resource on the server: ask for its version and send this phone's event over it
                val state = client.send("PROPFIND", href, ETAG_BODY, headers = mapOf("Depth" to "0"))
                response = if (state.code == 404) {
                    put(client, href, body, create = true, etag = null)
                } else {
                    val tag = parseMultistatus(state.body).firstOrNull()?.props?.get("getetag")?.takeIf { it.isNotEmpty() }
                    put(client, href, body, create = false, etag = tag)
                }
            }
            when {
                response.code in 200..299 -> eventDao.markPushed(event.id, href, response.etag)
                response.code == 401 -> throw DavException(codeText(401))
                response.code == 403 || response.code == 405 -> {
                    eventDao.clearDirty(event.id)
                    problem = "The server does not allow changes in this calendar. Changes stay on this phone only."
                }
                else -> problem = "Sending an event failed: ${codeText(response.code)}"
            }
        }
        return problem
    }

    private fun put(client: DavClient, href: String, body: String, create: Boolean, etag: String?): DavResponse {
        val conditions = when {
            create -> mapOf("If-None-Match" to "*")
            etag != null -> mapOf("If-Match" to etag)
            else -> emptyMap()
        }
        return client.send("PUT", href, body, DavClient.ICS, conditions)
    }

    // Downloads the events when the server says the calendar changed. Returns how many resources could not be read.
    private suspend fun pullChanges(client: DavClient, calendar: CalendarEntity, base: HttpUrl): Int {
        val marker = withContext(Dispatchers.IO) { markerOf(client, base) }
        val waiting = eventDao.dirtyOf(calendar.id).isNotEmpty() || caldavDao.deletions(calendar.id).isNotEmpty()
        if (marker != null && marker == calendar.ctag && !waiting) return 0

        val response = withContext(Dispatchers.IO) {
            client.send("REPORT", base.toString(), REPORT_BODY, headers = mapOf("Depth" to "1"))
        }
        if (response.code != 207) throw DavException(codeText(response.code))
        val entries = withContext(Dispatchers.Default) {
            parseMultistatus(response.body).filter { !it.props["calendar-data"].isNullOrBlank() }
        }

        var unread = 0
        db.withTransaction {
            val local = eventDao.withHref(calendar.id).groupBy { it.href.orEmpty() }
            val seen = HashSet<String>()
            for (entry in entries) {
                val href = response.url.resolve(entry.href)?.toString() ?: continue
                seen.add(href)
                val group = local[href].orEmpty()
                // Changed here and not sent yet: this phone wins
                if (group.any { it.dirty }) continue
                val etag = entry.props["getetag"]?.takeIf { it.isNotEmpty() }
                if (group.isNotEmpty() && etag != null && group.all { it.etag == etag }) continue
                val file = try {
                    IcsParser.parse(entry.props["calendar-data"].orEmpty())
                } catch (e: Exception) {
                    unread++
                    continue
                }
                val locked = file.events.size > 1
                val byUid = group.associateBy { it.uid }
                val kept = HashSet<Long>()
                for (event in file.events) {
                    val old = byUid[event.uid]
                    val row = event.toRow(calendar.id).copy(id = old?.id ?: 0, href = href, etag = etag, dirty = false, locked = locked)
                    if (old != null) {
                        eventDao.update(row)
                        kept.add(old.id)
                    } else {
                        eventDao.insert(row)
                    }
                }
                group.filter { it.id !in kept }.forEach { eventDao.delete(it.id) }
            }
            // Gone from the server: removed there. A change that was not sent yet stays and is sent as a new event.
            for ((href, group) in local) {
                if (href in seen) continue
                for (event in group) if (event.dirty) eventDao.forgetHref(event.id) else eventDao.delete(event.id)
            }
        }
        calendarDao.setCtag(calendar.id, marker)
        ReminderScheduler.skipPast(appContext)
        ReminderScheduler.schedule(appContext)
        WidgetUpdater.updateAll(appContext)
        return unread
    }

    // The change marker of the calendar, null when the server has none or does not answer
    private fun markerOf(client: DavClient, base: HttpUrl): String? = try {
        val response = client.send("PROPFIND", base.toString(), MARKER_BODY, headers = mapOf("Depth" to "0"))
        if (response.code != 207) null else {
            val props = parseMultistatus(response.body).firstOrNull()?.props
            (props?.get("getctag") ?: props?.get("sync-token"))?.takeIf { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        null
    }

    private fun discover(client: DavClient, start: HttpUrl): List<Found> {
        // A server that does not know the address given is still tried at its well known address
        val first = try {
            propfind(client, start)
        } catch (e: DavException) {
            if (e.message == codeText(401)) throw e
            null
        }
        val single = first?.entries?.firstOrNull()
        // The address of one calendar itself
        if (first != null && single != null && "calendar" in single.types) return listOf(found(first.url, single))
        var home = first?.let { homeFrom(client, it) }
        if (home == null) {
            // The well known address that CalDAV servers are asked to have
            val wellKnown = start.resolve("/.well-known/caldav")
            if (wellKnown != null) {
                val listing = try { propfind(client, wellKnown) } catch (e: DavException) { null }
                if (listing != null) home = homeFrom(client, listing)
            }
        }
        if (home == null) throw DavException("No calendars were found at this address. Try the address of your account or calendar.")
        val listing = propfind(client, home, depth = 1)
        return listing.entries
            .filter { "calendar" in it.types && (it.components.isEmpty() || "VEVENT" in it.components) }
            .mapNotNull { entry -> listing.url.resolve(entry.href)?.let { found(it, entry) } }
    }

    private class Listing(val url: HttpUrl, val entries: List<DavEntry>)

    private fun propfind(client: DavClient, url: HttpUrl, depth: Int = 0): Listing {
        val response = client.send("PROPFIND", url.toString(), PROPFIND_BODY, headers = mapOf("Depth" to depth.toString()))
        if (response.code != 207) throw DavException(codeText(response.code))
        return Listing(response.url, parseMultistatus(response.body))
    }

    private fun homeFrom(client: DavClient, listing: Listing): HttpUrl? {
        val entry = listing.entries.firstOrNull() ?: return null
        entry.props["calendar-home-set"]?.takeIf { it.isNotBlank() }?.let { return listing.url.resolve(it) }
        val principal = entry.props["current-user-principal"]?.takeIf { it.isNotBlank() }
            ?.let { listing.url.resolve(it) } ?: return null
        val second = propfind(client, principal)
        return second.entries.firstOrNull()?.props?.get("calendar-home-set")?.takeIf { it.isNotBlank() }
            ?.let { second.url.resolve(it) }
    }

    private fun found(url: HttpUrl, entry: DavEntry): Found {
        val name = entry.props["displayname"]?.takeIf { it.isNotBlank() }
            ?: url.pathSegments.lastOrNull { it.isNotEmpty() }
            ?: "Calendar"
        val color = entry.props["calendar-color"]?.let { raw ->
            val hex = raw.trim().take(7)
            runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
        }
        return Found(url.toString(), name, color)
    }

    private fun describe(e: Exception): String = when (e) {
        is DavException -> e.message ?: "The server did not accept the request."
        is UnknownHostException -> "The server could not be found. Check the address, your connection and that the app is allowed to use the internet."
        is SSLException -> "A secure connection to the server could not be made."
        is SocketTimeoutException -> "The server did not answer in time."
        is IOException -> "There is no connection to the server."
        is IllegalArgumentException -> "This is not a valid server address."
        else -> "The answer of the server could not be read."
    }

    private fun codeText(code: Int): String = when (code) {
        401 -> "Wrong username or password."
        403 -> "The server refused access to this account."
        404 -> "Nothing was found at this address."
        in 500..599 -> "The server has a problem (code $code)."
        else -> "The server answered with code $code."
    }

    private fun IcsEvent.toRow(calendarId: Long) = EventEntity(
        calendarId = calendarId,
        uid = uid,
        title = title,
        location = location,
        notes = notes,
        allDay = allDay,
        zoneId = zoneId,
        startLocal = start.toStored(),
        endLocal = end.toStored(),
        rrule = rrule,
        exdates = exdates.sorted().joinToString(",") { Recurrence.formatExdate(it, allDay) },
        reminders = Reminders.format(reminders),
        travelMinutes = travelMinutes
    )

    private companion object {
        // One lock for all calendars: two syncs at the same time would send an event twice
        val mutex = Mutex()

        const val NAMESPACES = "xmlns:d=\"DAV:\" xmlns:c=\"urn:ietf:params:xml:ns:caldav\" " +
            "xmlns:cs=\"http://calendarserver.org/ns/\" xmlns:ic=\"http://apple.com/ns/ical/\""

        const val PROPFIND_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind $NAMESPACES><d:prop>" +
            "<d:resourcetype/><d:displayname/><ic:calendar-color/><c:supported-calendar-component-set/>" +
            "<d:current-user-principal/><c:calendar-home-set/></d:prop></d:propfind>"

        const val MARKER_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind $NAMESPACES><d:prop>" +
            "<cs:getctag/><d:sync-token/></d:prop></d:propfind>"

        const val ETAG_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind $NAMESPACES><d:prop>" +
            "<d:getetag/></d:prop></d:propfind>"

        const val REPORT_BODY = "<?xml version=\"1.0\" encoding=\"utf-8\"?><c:calendar-query $NAMESPACES><d:prop>" +
            "<d:getetag/><c:calendar-data/></d:prop><c:filter><c:comp-filter name=\"VCALENDAR\">" +
            "<c:comp-filter name=\"VEVENT\"/></c:comp-filter></c:filter></c:calendar-query>"
    }
}
