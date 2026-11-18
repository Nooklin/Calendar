package org.nooklin.calendar.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.nooklin.calendar.data.db.CalendarDatabase
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.ics.IcsFile
import org.nooklin.calendar.data.ics.IcsWriter
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.model.toReading
import org.nooklin.calendar.data.recurrence.Recurrence
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.expandEvent
import org.nooklin.calendar.data.model.toStored
import org.nooklin.calendar.reminder.ReminderScheduler
import org.nooklin.calendar.widget.WidgetUpdater
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nooklin.calendar.data.ics.IcsFetcher
import org.nooklin.calendar.data.ics.IcsParser
import org.nooklin.calendar.data.caldav.CaldavService
import org.nooklin.calendar.data.caldav.CaldavSync
import org.nooklin.calendar.data.db.CaldavAccountEntity
import org.nooklin.calendar.data.caldav.ConnectResult
import org.nooklin.calendar.data.db.CaldavDeletionEntity

class CalendarRepository(context: Context) {
    private val appContext = context.applicationContext
    private val db = CalendarDatabase.get(context)
    private val calendarDao = db.calendarDao()
    private val eventDao = db.eventDao()
    private val caldavDao = db.caldavDao()
    private val caldav = CaldavSync(appContext, db)
    private val preferences = org.nooklin.calendar.data.local.AppPreferences(appContext)

    val calendars: Flow<List<CalendarEntity>> = calendarDao.observeAll()

    suspend fun setCalendarVisible(id: Long, visible: Boolean) {
        calendarDao.setVisible(id, visible)
        WidgetUpdater.updateAll(appContext)
    }

    suspend fun addCalendar(name: String, color: Int) {
        calendarDao.insert(CalendarEntity(name = name.trim(), color = color, sortOrder = calendarDao.count()))
    }

    suspend fun updateCalendar(id: Long, name: String, color: Int) {
        calendarDao.edit(id, name.trim(), color)
        WidgetUpdater.updateAll(appContext)
    }

    /**
     * Removes a calendar with all its events. A calendar on this phone stays when it is the last one, so a new event
     * always has a place: false then.
     */
    suspend fun deleteCalendar(id: Long): Boolean {
        val all = calendarDao.getAll()
        val target = all.firstOrNull { it.id == id } ?: return false
        // A calendar on this phone: not a link and not a CalDAV calendar
        fun onPhone(calendar: CalendarEntity) = calendar.sourceUrl == null && calendar.accountId == null
        if (onPhone(target) && all.count { onPhone(it) } <= 1) return false
        calendarDao.delete(id)
        // The login goes with the last calendar of the account
        if (target.accountId != null && calendarDao.countOfAccount(target.accountId) == 0) caldavDao.deleteAccount(target.accountId)
        ReminderScheduler.schedule(appContext)
        WidgetUpdater.updateAll(appContext)
        return true
    }

    /** The calendars that are kept up to date from the internet: web calendars and CalDAV calendars. */
    suspend fun subscriptions(): List<CalendarEntity> = calendarDao.getAll().filter { it.sourceUrl != null || it.accountId != null }

    /** Encrypts the addresses of followed calendars that were stored before addresses were encrypted. */
    suspend fun protectLinks() {
        for (calendar in calendarDao.getAll()) {
            val stored = calendar.sourceUrl ?: continue
            if (LinkSecret.isProtected(stored)) continue
            runCatching { calendarDao.setSourceUrl(calendar.id, LinkSecret.protect(stored)) }
        }
    }

    /** Logs in to a CalDAV server and adds its calendars. */
    suspend fun connectCaldav(server: String, username: String, password: String, service: CaldavService): ConnectResult =
        caldav.connect(server, username, password, service, NEW_CALENDAR_COLORS)

    /** The CalDAV accounts, to tell which service a CalDAV calendar comes from. */
    val caldavAccounts: Flow<List<CaldavAccountEntity>> = caldavDao.observeAccounts()

    /**
     * Follows a web calendar: downloads the link and makes a read only calendar of it. Nothing is made when the link
     * does not work. An empty [name] becomes the name the calendar gives itself.
     */
    suspend fun subscribe(name: String, color: Int, rawUrl: String): SubscribeResult {
        val url = IcsFetcher.normalize(rawUrl)
            ?: return SubscribeResult.Failed("Enter a link that starts with https:// or webcal://")
        val text = when (val fetched = withContext(Dispatchers.IO) { IcsFetcher.fetch(url) }) {
            is IcsFetcher.Fetched.Failed -> return SubscribeResult.Failed(fetched.reason)
            is IcsFetcher.Fetched.Text -> fetched.text
        }
        val file = try {
            withContext(Dispatchers.Default) { IcsParser.parse(text) }
        } catch (e: Exception) {
            return SubscribeResult.Failed("The file could not be read.")
        }
        val title = name.trim().ifEmpty { file.name?.takeIf { it.isNotBlank() } ?: (URI(url).host ?: "Calendar") }
        val id = calendarDao.insert(
            CalendarEntity(name = title, color = color, sortOrder = calendarDao.count(), sourceUrl = LinkSecret.protect(url), lastSync = nowSeconds())
        )
        importIcs(file, ImportTarget.Existing(id))
        return SubscribeResult.Ok(file.events.size, title)
    }

    /** Downloads a followed calendar again and replaces its events. A failure keeps the old events and is noted on the calendar. */
    suspend fun refresh(id: Long) {
        val calendar = calendarDao.get(id) ?: return
        if (calendar.accountId != null) {
            caldav.sync(id)
            return
        }
        val stored = calendar.sourceUrl ?: return
        val url = LinkSecret.reveal(stored)
        if (url == null) {
            calendarDao.setSync(id, calendar.lastSync, "The link can no longer be read on this phone. Subscribe to it again.")
            return
        }
        when (val fetched = withContext(Dispatchers.IO) { IcsFetcher.fetch(url) }) {
            is IcsFetcher.Fetched.Failed -> calendarDao.setSync(id, calendar.lastSync, fetched.reason)
            is IcsFetcher.Fetched.Text -> {
                val file = try {
                    withContext(Dispatchers.Default) { IcsParser.parse(fetched.text) }
                } catch (e: Exception) {
                    null
                }
                if (file == null) {
                    calendarDao.setSync(id, calendar.lastSync, "The file could not be read.")
                } else {
                    eventDao.deleteOfCalendar(id)
                    importIcs(file, ImportTarget.Existing(id))
                    calendarDao.setSync(id, nowSeconds(), null)
                }
            }
        }
    }

    private fun nowSeconds(): Long = System.currentTimeMillis() / 1000

    /**
     * Everything on the visible calendars that touches [from] up to (not including) [toExclusive], oldest first.
     * The flow emits again when an event or calendar changes.
     */
    fun occurrences(from: LocalDate, toExclusive: LocalDate, zone: ZoneId): Flow<List<Occurrence>> {
        val windowStart = from.atStartOfDay()
        val windowEnd = toExclusive.atStartOfDay()
        // The stored readings can be in another zone than the phone, so the database gets a wider window
        val fromStored = windowStart.minusDays(PAD_DAYS).toStored()
        val toStored = windowEnd.plusDays(PAD_DAYS).toStored()
        return combine(
            calendarDao.observeAll(),
            eventDao.observeSingles(fromStored, toStored),
            eventDao.observeSeries(toStored),
            preferences.dutchHolidays
        ) { calendars, singles, series, holidaysOn ->
            val visible = calendars.filter { it.visible }.associateBy { it.id }
            val holidays = if (holidaysOn) {
                DutchHolidays.between(from.minusDays(1), toExclusive).mapIndexed { index, holiday ->
                    val start = holiday.date.atStartOfDay()
                    Occurrence(
                        // Negative, so it never matches a stored event
                        eventId = -(holiday.date.toEpochDay() * 20 + index + 1),
                        calendarId = DutchHolidays.CALENDAR_ID, calendarName = DutchHolidays.CALENDAR_NAME,
                        color = DutchHolidays.COLOR, title = holiday.name, location = "", notes = "",
                        allDay = true, start = start, end = start.plusDays(1), zoneId = null, repeats = false, rrule = null,
                        sourceStart = start, sourceEnd = start.plusDays(1), reminders = emptyList()
                    )
                }.filter { it.start < windowEnd && it.end > windowStart }
            } else {
                emptyList()
            }
            (singles + series)
                .flatMap { event ->
                    val calendar = visible[event.calendarId] ?: return@flatMap emptyList()
                    expandEvent(event, calendar, windowStart, windowEnd, zone)
                }
                .plus(holidays)
                .sortedWith(compareBy({ it.start }, { it.title }))
        }
    }

    /** A new event, or when [occurrence] is given a change to that event or to the series it belongs to. */
    suspend fun save(draft: EventDraft, occurrence: Occurrence?, scope: Scope) {
        val uid = UUID.randomUUID().toString()
        val changes = if (occurrence == null) {
            EventChanges.planCreate(draft, uid)
        } else {
            val master = eventDao.get(occurrence.eventId) ?: return
            EventChanges.planEdit(master, occurrence.sourceStart, draft, scope) { uid }
        }
        apply(changes)
    }

    /**
     * The earlier event whose title is most like [typed], for filling in a new event. A tie goes to the event that is
     * latest in time. Events of calendars that follow a link are left out, because a new event cannot go there.
     */
    suspend fun suggestionFor(typed: String): Suggestion? {
        val wanted = TitleMatch.normalize(typed)
        if (wanted.length < TitleMatch.MIN_LENGTH) return null
        val writable = calendarDao.getAll().filter { it.sourceUrl == null }.map { it.id }.toSet()
        var best: EventEntity? = null
        var bestScore = 0.0
        for (event in eventDao.getAll()) {
            if (event.calendarId !in writable) continue
            val score = TitleMatch.score(wanted, TitleMatch.normalize(event.title))
            if (score < TitleMatch.MIN_SCORE) continue
            val current = best
            if (current == null || score > bestScore || (score == bestScore && event.startLocal > current.startLocal)) {
                best = event
                bestScore = score
            }
        }
        val found = best ?: return null
        val seconds = (found.endLocal - found.startLocal).coerceAtLeast(0)
        return Suggestion(
            title = found.title,
            location = found.location,
            notes = found.notes,
            allDay = found.allDay,
            seconds = if (found.allDay) seconds.coerceAtLeast(86400) else seconds,
            calendarId = found.calendarId,
            reminders = Reminders.parse(found.reminders)
        )
    }

    suspend fun delete(occurrence: Occurrence, scope: Scope) {
        val master = eventDao.get(occurrence.eventId) ?: return
        apply(EventChanges.planDelete(master, occurrence.sourceStart, scope))
    }

    private suspend fun apply(changes: Changes) {
        // The CalDAV calendars that got a change which has to be sent to the server
        val toSend = HashSet<Long>()
        db.withTransaction {
            val onServer = calendarDao.getAll().filter { it.accountId != null }.map { it.id }.toSet()
            suspend fun forget(old: EventEntity) {
                val href = old.href
                if (old.calendarId in onServer && href != null) {
                    caldavDao.insertDeletion(CaldavDeletionEntity(calendarId = old.calendarId, href = href, etag = old.etag))
                    toSend.add(old.calendarId)
                }
            }
            for (event in changes.update) {
                val old = eventDao.get(event.id)
                // The editor does not know the server fields, they stay as they were
                var row = if (old != null) event.copy(href = old.href, etag = old.etag, locked = old.locked) else event
                if (old != null && old.calendarId != event.calendarId) {
                    // Moved to another calendar: it is removed from the old one and made new in the other
                    forget(old)
                    row = row.copy(href = null, etag = null)
                }
                if (row.calendarId in onServer) {
                    row = row.copy(dirty = true)
                    toSend.add(row.calendarId)
                }
                eventDao.update(row)
            }
            for (event in changes.insert) {
                if (event.calendarId in onServer) toSend.add(event.calendarId)
                eventDao.insert(if (event.calendarId in onServer) event.copy(dirty = true) else event)
            }
            for (id in changes.delete) {
                eventDao.get(id)?.let { forget(it) }
                eventDao.delete(id)
            }
        }
        // The reminders follow the events; what was due before this change has been handled by the alarm already
        ReminderScheduler.skipPast(appContext)
        ReminderScheduler.schedule(appContext)
        WidgetUpdater.updateAll(appContext)
        toSend.forEach { caldav.push(it) }
    }

    /**
     * The events whose title, place or notes contain every word of [query], the next one of a series from today on.
     * Upcoming first, then the past, newest first. Events of a hidden calendar are left out.
     */
    suspend fun search(query: String, today: LocalDate, zone: ZoneId): List<Occurrence> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val calendars = calendarDao.getAll().filter { it.visible }.associateBy { it.id }
        val from = today.atStartOfDay()
        val found = ArrayList<Occurrence>()
        for (event in eventDao.getAll()) {
            val text = (event.title + " " + event.location + " " + event.notes).lowercase()
            if (!words.all { text.contains(it) }) continue
            val calendar = calendars[event.calendarId] ?: continue
            var list = expandEvent(event, calendar, from, from.plusYears(2), zone)
            if (list.isEmpty()) {
                val first = event.startLocal.toReading()
                list = expandEvent(event, calendar, first.minusDays(2), first.plusDays(2), zone)
            }
            list.firstOrNull()?.let { found.add(it) }
        }
        val (upcoming, past) = found.partition { it.end >= from }
        return (upcoming.sortedBy { it.start } + past.sortedByDescending { it.start }).take(200)
    }

    /** Puts the events of an .ics file in a calendar. An event with a uid that the calendar already has is replaced. */
    suspend fun importIcs(file: IcsFile, target: ImportTarget): ImportResult {
        var added = 0
        var updated = 0
        var calendarName = ""
        var toSend: Long? = null
        db.withTransaction {
            val calendar = when (target) {
                is ImportTarget.Existing -> calendarDao.get(target.calendarId)
                is ImportTarget.New -> {
                    val color = NEW_CALENDAR_COLORS[calendarDao.count() % NEW_CALENDAR_COLORS.size]
                    val id = calendarDao.insert(CalendarEntity(name = target.name, color = color, sortOrder = calendarDao.count()))
                    calendarDao.get(id)
                }
            }
            if (calendar != null) {
                calendarName = calendar.name
                if (calendar.accountId != null) toSend = calendar.id
                for (event in file.events) {
                    val existing = eventDao.findByUid(calendar.id, event.uid)
                    val row = EventEntity(
                        id = existing?.id ?: 0,
                        calendarId = calendar.id,
                        uid = event.uid,
                        title = event.title,
                        location = event.location,
                        notes = event.notes,
                        allDay = event.allDay,
                        zoneId = event.zoneId,
                        startLocal = event.start.toStored(),
                        endLocal = event.end.toStored(),
                        rrule = event.rrule,
                        exdates = event.exdates.sorted().joinToString(",") { Recurrence.formatExdate(it, event.allDay) },
                        reminders = Reminders.format(event.reminders),
                        travelMinutes = event.travelMinutes,
                        href = existing?.href,
                        etag = existing?.etag,
                        dirty = calendar.accountId != null
                    )
                    if (existing != null) {
                        eventDao.update(row)
                        updated++
                    } else {
                        eventDao.insert(row)
                        added++
                    }
                }
            }
        }
        ReminderScheduler.skipPast(appContext)
        ReminderScheduler.schedule(appContext)
        WidgetUpdater.updateAll(appContext)
        toSend?.let { caldav.push(it) }
        return ImportResult(added, updated, file.skipped, file.simplified, calendarName)
    }

    /** The name and the .ics text of a calendar, or null when it is gone. */
    suspend fun exportIcs(calendarId: Long): Pair<String, String>? {
        val calendar = calendarDao.get(calendarId) ?: return null
        val text = IcsWriter.write(calendar.name, eventDao.ofCalendar(calendarId), Instant.now(), LocalDate.now().year)
        return calendar.name to text
    }


    private companion object {
        const val PAD_DAYS = 2L
        val NEW_CALENDAR_COLORS = listOf(0xFF0A84FF, 0xFFFF3B30, 0xFFFF9500, 0xFF34C759, 0xFFAF52DE, 0xFF5AC8FA).map { it.toInt() }
    }
}

/** Where the events of an imported file go. */
sealed interface ImportTarget {
    data class Existing(val calendarId: Long) : ImportTarget
    data class New(val name: String) : ImportTarget
}

data class ImportResult(val added: Int, val updated: Int, val skipped: Int, val simplified: Int, val calendarName: String)

/** The result of following a web calendar: how many events it has, or why it did not work. */
sealed interface SubscribeResult {
    data class Ok(val events: Int, val name: String) : SubscribeResult
    data class Failed(val reason: String) : SubscribeResult
}
