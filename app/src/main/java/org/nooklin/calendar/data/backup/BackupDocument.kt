package org.nooklin.calendar.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** The four parts of a backup. A restore replaces exactly the parts that are in the backup. */
object BackupSection {
    /** Calendars on this phone, with their events. */
    const val CALENDARS = "calendars"

    /** CalDAV accounts with their password, their calendars and events, and removals that still have to reach the server. */
    const val CALDAV = "caldav"

    /** Calendars that follow a link, with the address and events. */
    const val LINKS = "links"

    /** The settings of the app. */
    const val SETTINGS = "settings"

    val ALL = listOf(CALENDARS, CALDAV, LINKS, SETTINGS)
}

object BackupKind {
    const val LOCAL = "local"
    const val LINK = "link"
    const val CALDAV = "caldav"
}

data class BackupCalendar(
    val id: Long,
    val kind: String,
    val name: String,
    val color: Int,
    val visible: Boolean,
    val sortOrder: Int,
    // The readable address of a link calendar
    val url: String?,
    val lastSync: Long,
    val accountId: Long?,
    val remoteUrl: String?,
    val ctag: String?
)

data class BackupEvent(
    val calendarId: Long,
    val uid: String,
    val title: String,
    val location: String,
    val notes: String,
    val allDay: Boolean,
    val zoneId: String?,
    val startLocal: Long,
    val endLocal: Long,
    val rrule: String?,
    val exdates: String,
    val reminders: String,
    val href: String?,
    val etag: String?,
    val dirty: Boolean,
    val locked: Boolean,
    val travelMinutes: Int
)

data class BackupAccount(val id: Long, val serverUrl: String, val username: String, val password: String, val service: String)

data class BackupDeletion(val calendarId: Long, val href: String, val etag: String?)

class BackupDocument(
    val format: Int,
    val app: String,
    val dbVersion: Int,
    val created: Long,
    val sections: Set<String>,
    val calendars: List<BackupCalendar>,
    val events: List<BackupEvent>,
    val accounts: List<BackupAccount>,
    val deletions: List<BackupDeletion>,
    val settings: Map<String, Any>
)

/** A backup that was read, and what this version of the app could not take over from it. */
class ParsedBackup(val document: BackupDocument, val notes: List<String>)

/**
 * Writes and reads the JSON inside a backup. Reading is forgiving on purpose, so a backup from another version still
 * restores as much as possible: fields this version does not know are left out, fields that are missing get their
 * default, and everything that is left out is reported in [ParsedBackup.notes].
 */
object BackupJson {
    /** The newest file format this version can read completely. */
    const val FORMAT = 1

    private const val DEFAULT_COLOR = 0xFF0A84FF.toInt()

    private val TOP_KEYS = setOf("format", "app", "dbVersion", "created", "sections", "calendars", "events", "accounts", "deletions", "settings")
    private val CALENDAR_KEYS = setOf("id", "kind", "name", "color", "visible", "sortOrder", "url", "lastSync", "accountId", "remoteUrl", "ctag")
    private val EVENT_KEYS = setOf(
        "calendarId", "uid", "title", "location", "notes", "allDay", "zoneId", "startLocal", "endLocal", "rrule", "exdates",
        "reminders", "href", "etag", "dirty", "locked", "travelMinutes"
    )
    private val ACCOUNT_KEYS = setOf("id", "serverUrl", "username", "password", "service")
    private val DELETION_KEYS = setOf("calendarId", "href", "etag")

    fun write(doc: BackupDocument): String {
        val root = JSONObject()
        root.put("format", doc.format)
        root.put("app", doc.app)
        root.put("dbVersion", doc.dbVersion)
        root.put("created", doc.created)
        root.put("sections", JSONArray(doc.sections.toList()))
        root.put("calendars", JSONArray().also { array ->
            doc.calendars.forEach { c ->
                array.put(JSONObject().apply {
                    put("id", c.id)
                    put("kind", c.kind)
                    put("name", c.name)
                    put("color", c.color)
                    put("visible", c.visible)
                    put("sortOrder", c.sortOrder)
                    c.url?.let { put("url", it) }
                    put("lastSync", c.lastSync)
                    c.accountId?.let { put("accountId", it) }
                    c.remoteUrl?.let { put("remoteUrl", it) }
                    c.ctag?.let { put("ctag", it) }
                })
            }
        })
        root.put("events", JSONArray().also { array ->
            doc.events.forEach { e ->
                array.put(JSONObject().apply {
                    put("calendarId", e.calendarId)
                    put("uid", e.uid)
                    put("title", e.title)
                    put("location", e.location)
                    put("notes", e.notes)
                    put("allDay", e.allDay)
                    e.zoneId?.let { put("zoneId", it) }
                    put("startLocal", e.startLocal)
                    put("endLocal", e.endLocal)
                    e.rrule?.let { put("rrule", it) }
                    put("exdates", e.exdates)
                    put("reminders", e.reminders)
                    e.href?.let { put("href", it) }
                    e.etag?.let { put("etag", it) }
                    put("dirty", e.dirty)
                    put("locked", e.locked)
                    put("travelMinutes", e.travelMinutes)
                })
            }
        })
        root.put("accounts", JSONArray().also { array ->
            doc.accounts.forEach { a ->
                array.put(JSONObject().apply {
                    put("id", a.id)
                    put("serverUrl", a.serverUrl)
                    put("username", a.username)
                    put("password", a.password)
                    put("service", a.service)
                })
            }
        })
        root.put("deletions", JSONArray().also { array ->
            doc.deletions.forEach { d ->
                array.put(JSONObject().apply {
                    put("calendarId", d.calendarId)
                    put("href", d.href)
                    d.etag?.let { put("etag", it) }
                })
            }
        })
        root.put("settings", JSONObject().also { obj -> doc.settings.forEach { (key, value) -> obj.put(key, value) } })
        return root.toString()
    }

    fun parse(text: String): ParsedBackup {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupFormatException("The backup file is damaged.")
        }
        val format = root.longOrNull("format")?.toInt() ?: throw BackupFormatException("This is not a Nooklin Calendar backup.")
        val notes = ArrayList<String>()
        if (format > FORMAT) {
            notes.add("This backup was made by a newer version of the app, so some of it may not be understood.")
        }
        root.keys().asSequence().filter { it !in TOP_KEYS }.sorted().forEach {
            notes.add("The part \"$it\" of the backup is not supported by this version and was left out.")
        }

        val unknownFields = LinkedHashMap<String, LinkedHashSet<String>>()
        fun JSONObject.noteUnknown(type: String, known: Set<String>) {
            keys().forEach { if (it !in known) unknownFields.getOrPut(type) { LinkedHashSet() }.add(it) }
        }

        // Calendars
        val calendars = ArrayList<BackupCalendar>()
        var damagedCalendars = 0
        val skippedCalendars = ArrayList<String>()
        root.optJSONArray("calendars")?.forEachObject { o ->
            o.noteUnknown("Calendars", CALENDAR_KEYS)
            try {
                val kind = o.stringOrNull("kind") ?: BackupKind.LOCAL
                val name = o.stringOrNull("name") ?: ""
                val id = o.longOrNull("id")
                if (id == null) {
                    damagedCalendars++
                } else if (kind != BackupKind.LOCAL && kind != BackupKind.LINK && kind != BackupKind.CALDAV) {
                    skippedCalendars.add("The calendar \"$name\" is of a type this version does not know (\"$kind\") and was left out.")
                } else {
                    val url = o.stringOrNull("url")
                    val accountId = o.longOrNull("accountId")
                    if ((kind == BackupKind.LINK && url.isNullOrBlank()) || (kind == BackupKind.CALDAV && accountId == null)) {
                        damagedCalendars++
                    } else {
                        calendars.add(
                            BackupCalendar(
                                id = id,
                                kind = kind,
                                name = name,
                                color = o.longOrNull("color")?.toInt() ?: DEFAULT_COLOR,
                                visible = o.booleanOrNull("visible") ?: true,
                                sortOrder = o.longOrNull("sortOrder")?.toInt() ?: 0,
                                url = url,
                                lastSync = o.longOrNull("lastSync") ?: 0L,
                                accountId = accountId,
                                remoteUrl = o.stringOrNull("remoteUrl"),
                                ctag = o.stringOrNull("ctag")
                            )
                        )
                    }
                }
            } catch (e: JSONException) {
                damagedCalendars++
            }
        }
        notes.addAll(skippedCalendars)
        if (damagedCalendars > 0) notes.add("$damagedCalendars calendars could not be read and were left out.")

        // Events
        val events = ArrayList<BackupEvent>()
        var damagedEvents = 0
        var eventsWithoutCalendar = 0
        val calendarIds = calendars.map { it.id }.toSet()
        root.optJSONArray("events")?.forEachObject { o ->
            o.noteUnknown("Events", EVENT_KEYS)
            try {
                val calendarId = o.longOrNull("calendarId")
                val uid = o.stringOrNull("uid")
                val start = o.longOrNull("startLocal")
                val end = o.longOrNull("endLocal")
                if (calendarId == null || uid == null || start == null || end == null) {
                    damagedEvents++
                } else if (calendarId !in calendarIds) {
                    eventsWithoutCalendar++
                } else {
                    events.add(
                        BackupEvent(
                            calendarId = calendarId,
                            uid = uid,
                            title = o.stringOrNull("title") ?: "",
                            location = o.stringOrNull("location") ?: "",
                            notes = o.stringOrNull("notes") ?: "",
                            allDay = o.booleanOrNull("allDay") ?: false,
                            zoneId = o.stringOrNull("zoneId"),
                            startLocal = start,
                            endLocal = end,
                            rrule = o.stringOrNull("rrule"),
                            exdates = o.stringOrNull("exdates") ?: "",
                            reminders = o.stringOrNull("reminders") ?: "",
                            href = o.stringOrNull("href"),
                            etag = o.stringOrNull("etag"),
                            dirty = o.booleanOrNull("dirty") ?: false,
                            locked = o.booleanOrNull("locked") ?: false,
                            travelMinutes = o.longOrNull("travelMinutes")?.toInt() ?: 0
                        )
                    )
                }
            } catch (e: JSONException) {
                damagedEvents++
            }
        }
        if (damagedEvents > 0) notes.add("$damagedEvents events could not be read and were left out.")
        if (eventsWithoutCalendar > 0) notes.add("$eventsWithoutCalendar events belong to a calendar that was left out, so they were left out too.")

        // CalDAV accounts and removals
        val accounts = ArrayList<BackupAccount>()
        var damagedAccounts = 0
        root.optJSONArray("accounts")?.forEachObject { o ->
            o.noteUnknown("CalDAV accounts", ACCOUNT_KEYS)
            try {
                val id = o.longOrNull("id")
                val server = o.stringOrNull("serverUrl")
                val user = o.stringOrNull("username")
                val password = o.stringOrNull("password")
                if (id == null || server == null || user == null || password == null) {
                    damagedAccounts++
                } else {
                    accounts.add(BackupAccount(id, server, user, password, o.stringOrNull("service") ?: "other"))
                }
            } catch (e: JSONException) {
                damagedAccounts++
            }
        }
        if (damagedAccounts > 0) notes.add("$damagedAccounts CalDAV accounts could not be read and were left out.")
        val deletions = ArrayList<BackupDeletion>()
        root.optJSONArray("deletions")?.forEachObject { o ->
            o.noteUnknown("Removals for CalDAV", DELETION_KEYS)
            try {
                val calendarId = o.longOrNull("calendarId")
                val href = o.stringOrNull("href")
                if (calendarId != null && href != null && calendarId in calendarIds) deletions.add(BackupDeletion(calendarId, href, o.stringOrNull("etag")))
            } catch (e: JSONException) {
                // A removal that cannot be read is not worth stopping for
            }
        }

        // Settings: only values of a type that can be stored; the meaning of a key is checked by the restore
        val settings = LinkedHashMap<String, Any>()
        root.optJSONObject("settings")?.let { obj ->
            obj.keys().forEach { key ->
                when (val value = obj.opt(key)) {
                    is Boolean -> settings[key] = value
                    is Int -> settings[key] = value
                    is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) settings[key] = value.toInt() else notes.add("The setting \"$key\" has a value that is too large and was left out.")
                    is String -> settings[key] = value
                    else -> notes.add("The setting \"$key\" has a kind of value this version does not know and was left out.")
                }
            }
        }

        unknownFields.forEach { (type, fields) ->
            fields.sorted().forEach { notes.add("$type: the field \"$it\" is not supported by this version and was left out.") }
        }

        // The parts: as written, or worked out from the content for a file that does not say
        val sections = LinkedHashSet<String>()
        val written = root.optJSONArray("sections")
        if (written != null) {
            for (i in 0 until written.length()) {
                val name = written.optString(i)
                if (name in BackupSection.ALL) sections.add(name) else notes.add("The part \"$name\" of the backup is not supported by this version and was left out.")
            }
        } else {
            if (calendars.any { it.kind == BackupKind.LOCAL }) sections.add(BackupSection.CALENDARS)
            if (accounts.isNotEmpty() || calendars.any { it.kind == BackupKind.CALDAV }) sections.add(BackupSection.CALDAV)
            if (calendars.any { it.kind == BackupKind.LINK }) sections.add(BackupSection.LINKS)
            if (settings.isNotEmpty()) sections.add(BackupSection.SETTINGS)
        }

        val doc = BackupDocument(
            format = format,
            app = root.stringOrNull("app") ?: "unknown",
            dbVersion = root.longOrNull("dbVersion")?.toInt() ?: 0,
            created = root.longOrNull("created") ?: 0L,
            sections = sections,
            calendars = calendars,
            events = events,
            accounts = accounts,
            deletions = deletions,
            settings = settings
        )
        return ParsedBackup(doc, notes)
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.longOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.booleanOrNull(key: String): Boolean? = if (has(key) && !isNull(key)) getBoolean(key) else null

    private fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) optJSONObject(i)?.let(action)
    }
}
