package org.nooklin.calendar.data.backup

import android.content.Context
import androidx.room.withTransaction
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nooklin.calendar.BuildConfig
import org.nooklin.calendar.data.LinkSecret
import org.nooklin.calendar.data.caldav.SecretBox
import org.nooklin.calendar.data.db.CaldavAccountEntity
import org.nooklin.calendar.data.db.CaldavDeletionEntity
import org.nooklin.calendar.data.db.CalendarDatabase
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.local.AppFont
import org.nooklin.calendar.data.local.AppPreferences
import org.nooklin.calendar.data.local.ContrastColor
import org.nooklin.calendar.data.local.MonthDensity
import org.nooklin.calendar.data.local.WeekStart
import org.nooklin.calendar.reminder.ReminderScheduler
import org.nooklin.calendar.widget.WidgetUpdater

/** What goes into a backup. All four are on by default. */
data class BackupOptions(
    val calendars: Boolean = true,
    val caldav: Boolean = true,
    val links: Boolean = true,
    val settings: Boolean = true
) {
    fun any() = calendars || caldav || links || settings
}

class BackupBuild(
    val bytes: ByteArray,
    val calendars: Int,
    val events: Int,
    val accounts: Int,
    val settings: Int,
    val notes: List<String>
)

class RestoreReport(
    val calendars: Int,
    val events: Int,
    val accounts: Int,
    val settings: Int,
    val notes: List<String>
)

/**
 * Makes and restores a backup of the calendars, the events, the CalDAV accounts and the settings. The passwords and
 * the addresses of link calendars are encrypted with a key of the Android Keystore of this phone, which is gone after
 * a new install. So they are written in readable form into the backup and the whole backup is encrypted with the
 * password or PIN of the user (see [BackupCrypto]); at a restore they are encrypted again with the key of the phone.
 */
class BackupManager(
    private val context: Context,
    private val db: CalendarDatabase,
    private val preferences: AppPreferences
) {
    private val calendarDao = db.calendarDao()
    private val eventDao = db.eventDao()
    private val caldavDao = db.caldavDao()

    suspend fun create(options: BackupOptions, secret: String): BackupBuild = withContext(Dispatchers.IO) {
        val notes = ArrayList<String>()
        val allCalendars = calendarDao.getAll()
        val eventsByCalendar = eventDao.getAll().groupBy { it.calendarId }
        val sections = LinkedHashSet<String>()
        val calendars = ArrayList<BackupCalendar>()
        val accounts = ArrayList<BackupAccount>()
        val deletions = ArrayList<BackupDeletion>()
        val calendarIds = HashSet<Long>()

        fun addCalendar(c: CalendarEntity, kind: String, url: String?) {
            calendars.add(
                BackupCalendar(c.id, kind, c.name, c.color, c.visible, c.sortOrder, url, c.lastSync, c.accountId, c.remoteUrl, c.ctag)
            )
            calendarIds.add(c.id)
        }

        if (options.calendars) {
            sections.add(BackupSection.CALENDARS)
            allCalendars.filter { it.sourceUrl == null && it.accountId == null }.forEach { addCalendar(it, BackupKind.LOCAL, null) }
        }
        if (options.links) {
            sections.add(BackupSection.LINKS)
            allCalendars.filter { it.sourceUrl != null }.forEach { c ->
                val url = LinkSecret.reveal(c.sourceUrl!!)
                if (url == null) notes.add("The address of the calendar \"${c.name}\" could not be read on this phone, so it was left out.")
                else addCalendar(c, BackupKind.LINK, url)
            }
        }
        if (options.caldav) {
            sections.add(BackupSection.CALDAV)
            caldavDao.accounts().forEach { account ->
                val password = SecretBox.decrypt(account.secret)
                if (password == null) {
                    notes.add("The password of the CalDAV account ${account.username} could not be read on this phone, so the account and its calendars were left out.")
                } else {
                    accounts.add(BackupAccount(account.id, account.serverUrl, account.username, password, account.service))
                    allCalendars.filter { it.accountId == account.id }.forEach { c ->
                        addCalendar(c, BackupKind.CALDAV, null)
                        caldavDao.deletions(c.id).forEach { deletions.add(BackupDeletion(c.id, it.href, it.etag)) }
                    }
                }
            }
        }
        if (options.settings) {
            sections.add(BackupSection.SETTINGS)
        }
        val settings = if (options.settings) BackupSettings.forBackup(preferences.readAll()) else emptyMap()

        val events = ArrayList<BackupEvent>()
        calendarIds.forEach { id ->
            eventsByCalendar[id].orEmpty().forEach { e ->
                events.add(
                    BackupEvent(
                        e.calendarId, e.uid, e.title, e.location, e.notes, e.allDay, e.zoneId, e.startLocal, e.endLocal, e.rrule,
                        e.exdates, e.reminders, e.href, e.etag, e.dirty, e.locked, e.travelMinutes
                    )
                )
            }
        }

        val doc = BackupDocument(
            format = BackupJson.FORMAT,
            app = BuildConfig.VERSION_NAME,
            dbVersion = db.openHelper.readableDatabase.version,
            created = Instant.now().epochSecond,
            sections = sections,
            calendars = calendars,
            events = events,
            accounts = accounts,
            deletions = deletions,
            settings = settings
        )
        val bytes = BackupCrypto.encrypt(BackupJson.write(doc).toByteArray(Charsets.UTF_8), secret)
        BackupBuild(bytes, calendars.size, events.size, accounts.size, settings.size, notes)
    }

    /** Decrypts and reads a backup. Throws [WrongSecretException] or [BackupFormatException]. Nothing is changed. */
    suspend fun open(bytes: ByteArray, secret: String): ParsedBackup = withContext(Dispatchers.Default) {
        BackupJson.parse(String(BackupCrypto.decrypt(bytes, secret), Charsets.UTF_8))
    }

    /** Replaces the parts that are in the backup with what the backup holds. */
    suspend fun restore(parsed: ParsedBackup): RestoreReport = withContext(Dispatchers.IO) {
        val doc = parsed.document
        val notes = ArrayList(parsed.notes)
        val has = doc.sections
        val calendarMap = HashMap<Long, Long>()
        var calendarCount = 0
        var eventCount = 0
        var accountCount = 0

        db.withTransaction {
            if (BackupSection.CALENDARS in has) calendarDao.deleteOnPhone()
            if (BackupSection.LINKS in has) calendarDao.deleteLinks()
            if (BackupSection.CALDAV in has) {
                calendarDao.deleteCaldav()
                caldavDao.deleteAllAccounts()
            }

            val accountMap = HashMap<Long, Long>()
            if (BackupSection.CALDAV in has) {
                doc.accounts.forEach { a ->
                    accountMap[a.id] = caldavDao.insertAccount(
                        CaldavAccountEntity(serverUrl = a.serverUrl, username = a.username, secret = SecretBox.encrypt(a.password), service = a.service)
                    )
                    accountCount++
                }
            }
            doc.calendars.forEach { c ->
                val entity = when (c.kind) {
                    BackupKind.LOCAL -> if (BackupSection.CALENDARS in has) toEntity(c, null, null) else null
                    BackupKind.LINK -> if (BackupSection.LINKS in has) toEntity(c, LinkSecret.protect(c.url.orEmpty()), null) else null
                    BackupKind.CALDAV -> if (BackupSection.CALDAV in has) {
                        val account = c.accountId?.let { accountMap[it] }
                        if (account == null) {
                            notes.add("The calendar \"${c.name}\" belongs to a CalDAV account that is not in the backup, so it was left out.")
                            null
                        } else toEntity(c, null, account)
                    } else null
                    else -> null
                }
                if (entity != null) {
                    calendarMap[c.id] = calendarDao.insert(entity)
                    calendarCount++
                }
            }
            doc.events.forEach { e ->
                val calendarId = calendarMap[e.calendarId] ?: return@forEach
                eventDao.insert(
                    EventEntity(
                        calendarId = calendarId, uid = e.uid, title = e.title, location = e.location, notes = e.notes, allDay = e.allDay,
                        zoneId = e.zoneId, startLocal = e.startLocal, endLocal = e.endLocal, rrule = e.rrule, exdates = e.exdates,
                        reminders = e.reminders, href = e.href, etag = e.etag, dirty = e.dirty, locked = e.locked, travelMinutes = e.travelMinutes
                    )
                )
                eventCount++
            }
            doc.deletions.forEach { d ->
                val calendarId = calendarMap[d.calendarId] ?: return@forEach
                caldavDao.insertDeletion(CaldavDeletionEntity(calendarId = calendarId, href = d.href, etag = d.etag))
            }
            // A new event always needs a calendar on this phone
            if (calendarDao.countOnPhone() == 0) {
                calendarDao.insert(CalendarEntity(name = "Calendar", color = CalendarDatabase.DEFAULT_CALENDAR_COLOR, sortOrder = 0))
            }
        }

        var settingCount = 0
        if (BackupSection.SETTINGS in has) {
            val checked = BackupSettings.forRestore(doc.settings, notes)
            val values = LinkedHashMap(checked)
            val remove = HashSet<String>()
            // The dashboard names calendars by number, and the numbers are new
            (values["dash_calendars"] as? String)?.let { stored ->
                val anyRestored = has.any { it != BackupSection.SETTINGS }
                val existing = if (anyRestored) emptySet() else calendarDao.getAll().map { it.id }.toSet()
                val mapped = stored.split(",").mapNotNull { it.toLongOrNull() }.mapNotNull { old ->
                    if (anyRestored) calendarMap[old] else old.takeIf { it in existing }
                }
                if (mapped.isEmpty()) {
                    values.remove("dash_calendars")
                    remove.add("dash_calendars")
                } else {
                    values["dash_calendars"] = mapped.joinToString(",")
                }
            }
            preferences.writeAll(values, remove)
            settingCount = values.size
        }

        ReminderScheduler.schedule(context)
        WidgetUpdater.updateAll(context)
        RestoreReport(calendarCount, eventCount, accountCount, settingCount, notes)
    }

    private fun toEntity(c: BackupCalendar, sourceUrl: String?, accountId: Long?) = CalendarEntity(
        name = c.name, color = c.color, visible = c.visible, sortOrder = c.sortOrder, sourceUrl = sourceUrl, lastSync = c.lastSync,
        accountId = accountId, remoteUrl = c.remoteUrl, ctag = c.ctag
    )
}

/**
 * Which settings go into a backup and which are taken over at a restore. The settings that belong to this phone (the
 * app lock and blocking screenshots) are never in a backup and never restored, so a restore cannot lock anyone out.
 */
internal object BackupSettings {
    private val ON_THIS_PHONE = setOf("app_lock", "lock_delay", "allow_screenshots")
    private val BOOLEANS = setOf(
        "week_numbers", "colorful", "dash_enabled", "tour_done", "dutch_holidays", "bg_sync", "bg_sync_wifi", "ask_location",
        "haptics_enabled", "dash_all_day_busy", "lock_screen_notifications"
    )
    private val INTS = setOf(
        "bg_sync_minutes", "dash_evening_start", "dash_evening_end", "dash_day_start", "dash_day_end", "dash_min_free",
        "dash_look_ahead", "dash_all_day_ahead"
    )
    private val CHOICES: Map<String, Set<String>> = mapOf(
        "theme_mode" to setOf("light", "dark", "black", "system"),
        "contrast_color" to ContrastColor.entries.map { it.storageKey }.toSet(),
        "app_font" to AppFont.entries.map { it.storageKey }.toSet(),
        "week_start" to WeekStart.entries.map { it.storageKey }.toSet(),
        "month_density" to MonthDensity.entries.map { it.storageKey }.toSet()
    )
    private val TEXT = setOf("dash_order", "dash_disabled", "dash_extended", "dash_calendars")

    private fun accepts(name: String, value: Any): Boolean = when (name) {
        in BOOLEANS -> value is Boolean
        in INTS -> value is Int && (name != "bg_sync_minutes" || value >= 15)
        in CHOICES.keys -> value is String && value in CHOICES.getValue(name)
        in TEXT -> value is String
        else -> false
    }

    fun forBackup(all: Map<String, Any>): Map<String, Any> =
        all.filter { (name, value) -> name !in ON_THIS_PHONE && accepts(name, value) }

    fun forRestore(values: Map<String, Any>, notes: MutableList<String>): Map<String, Any> {
        val accepted = LinkedHashMap<String, Any>()
        values.forEach { (name, value) ->
            when {
                name in ON_THIS_PHONE -> Unit
                name !in BOOLEANS && name !in INTS && name !in CHOICES.keys && name !in TEXT ->
                    notes.add("The setting \"$name\" is not supported by this version and was left out.")
                accepts(name, value) -> accepted[name] = value
                else -> notes.add("The setting \"$name\" has a value this version does not know (\"$value\") and was left out.")
            }
        }
        notes.add("The app lock and the screenshot setting belong to this phone and are not restored.")
        return accepted
    }
}
