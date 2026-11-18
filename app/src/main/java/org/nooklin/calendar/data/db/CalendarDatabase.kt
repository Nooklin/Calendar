package org.nooklin.calendar.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** A calendar that events belong to: on this phone, following a link, or kept in step with a CalDAV server. */
@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    // ARGB
    val color: Int,
    val visible: Boolean = true,
    val sortOrder: Int = 0,
    // The https link of a calendar that follows a web calendar (read only); null for a calendar on this phone
    val sourceUrl: String? = null,
    // Seconds since 1970 of the last good refresh, 0 when there has not been one
    @ColumnInfo(defaultValue = "0") val lastSync: Long = 0,
    // Why the last refresh failed; null when it went well
    val lastError: String? = null,
    // The CalDAV account this calendar belongs to; null for a calendar on this phone or one that follows a link
    val accountId: Long? = null,
    // The address of the calendar on the CalDAV server
    val remoteUrl: String? = null,
    // The change marker of the server at the last good sync (CTag or sync token)
    val ctag: String? = null
)

/** A CalDAV server login. The password is stored encrypted, see data/caldav/SecretBox.kt. */
@Entity(tableName = "caldav_accounts")
data class CaldavAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverUrl: String,
    val username: String,
    val secret: String,
    // The kind of server, see data/caldav/CaldavService.kt
    @ColumnInfo(defaultValue = "'other'") val service: String = "other"
)

/** An event that was removed on this phone and still has to be removed on the server. */
@Entity(
    tableName = "caldav_deletions",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("calendarId")]
)
data class CaldavDeletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calendarId: Long,
    val href: String,
    val etag: String? = null
)

/**
 * One event, or the first event of a repeating series.
 *
 * Times are stored as wall clock time, not as an instant: [startLocal] and [endLocal] are the seconds since
 * 1970-01-01T00:00 of the clock reading in the event's own time zone, as if that reading were UTC. That keeps a
 * repeating 09:00 meeting at 09:00 across the daylight saving switch.
 * - All day: both are midnight, [endLocal] is the start of the day AFTER the last day. [zoneId] is null.
 * - Floating time: [zoneId] is null and the reading is taken in whatever time zone the phone is in now.
 * - Otherwise [zoneId] is the zone the clock reading belongs to, for example "Europe/Amsterdam".
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("calendarId"), Index("startLocal")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val calendarId: Long,
    val uid: String,
    val title: String,
    val location: String = "",
    val notes: String = "",
    val allDay: Boolean = false,
    val zoneId: String? = null,
    val startLocal: Long,
    val endLocal: Long,
    // For example "FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20271231", see data/recurrence/Recurrence.kt
    val rrule: String? = null,
    // Start readings that are left out of the series, comma separated, "yyyyMMdd'T'HHmmss" or "yyyyMMdd" for all day
    val exdates: String = "",
    // Minutes before the start, comma separated, for example "10,1440". Negative: after the start (all day events).
    @ColumnInfo(defaultValue = "''") val reminders: String = "",
    // CalDAV: the address of the resource on the server, null for an event that is not (yet) on a server
    val href: String? = null,
    // CalDAV: the version of the resource that this phone has seen
    val etag: String? = null,
    // CalDAV: changed on this phone and not sent to the server yet
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
    // CalDAV: part of a resource with changed days of a repeating event, which this app cannot send back; read only
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    // Minutes of travel before the start; the reminders go off this much earlier. 0 for none.
    @ColumnInfo(defaultValue = "0") val travelMinutes: Int = 0
)

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CalendarEntity>>

    @Query("UPDATE calendars SET visible = :visible WHERE id = :id")
    suspend fun setVisible(id: Long, visible: Boolean)

    @Insert
    suspend fun insert(calendar: CalendarEntity): Long

    @Query("UPDATE calendars SET name = :name, color = :color WHERE id = :id")
    suspend fun edit(id: Long, name: String, color: Int)

    // The events of the calendar go with it (foreign key with cascade)
    @Query("DELETE FROM calendars WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE calendars SET lastSync = :lastSync, lastError = :error WHERE id = :id")
    suspend fun setSync(id: Long, lastSync: Long, error: String?)

    @Query("SELECT COUNT(*) FROM calendars")
    suspend fun count(): Int

    @Query("UPDATE calendars SET sourceUrl = :sourceUrl WHERE id = :id")
    suspend fun setSourceUrl(id: Long, sourceUrl: String)

    @Query("UPDATE calendars SET ctag = :ctag WHERE id = :id")
    suspend fun setCtag(id: Long, ctag: String?)

    @Query("SELECT COUNT(*) FROM calendars WHERE accountId = :accountId")
    suspend fun countOfAccount(accountId: Long): Int

    @Query("SELECT * FROM calendars ORDER BY sortOrder, id")
    suspend fun getAll(): List<CalendarEntity>

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun get(id: Long): CalendarEntity?

    @Query("DELETE FROM calendars WHERE name LIKE 'Sample %'")
    suspend fun deleteSamples()

    // For restoring a backup: the events go with the calendars (foreign key with cascade)
    @Query("DELETE FROM calendars WHERE sourceUrl IS NULL AND accountId IS NULL")
    suspend fun deleteOnPhone()

    @Query("DELETE FROM calendars WHERE sourceUrl IS NOT NULL")
    suspend fun deleteLinks()

    @Query("DELETE FROM calendars WHERE accountId IS NOT NULL")
    suspend fun deleteCaldav()

    @Query("SELECT COUNT(*) FROM calendars WHERE sourceUrl IS NULL AND accountId IS NULL")
    suspend fun countOnPhone(): Int
}

@Dao
interface EventDao {
    // Events that do not repeat and may touch the window. The window is padded by the caller, because the
    // stored clock readings can be in another time zone than the phone; the exact check happens in Kotlin.
    @Query("SELECT * FROM events WHERE rrule IS NULL AND startLocal < :toLocal AND endLocal > :fromLocal")
    fun observeSingles(fromLocal: Long, toLocal: Long): Flow<List<EventEntity>>

    // Repeating series that started before the end of the window; where they stop is decided in Kotlin
    @Query("SELECT * FROM events WHERE rrule IS NOT NULL AND startLocal < :toLocal")
    fun observeSeries(toLocal: Long): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): EventEntity?

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId ORDER BY startLocal, id")
    suspend fun ofCalendar(calendarId: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId AND uid = :uid LIMIT 1")
    suspend fun findByUid(calendarId: Long, uid: String): EventEntity?

    @Query("SELECT * FROM events WHERE reminders != ''")
    suspend fun withReminders(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("DELETE FROM events")
    suspend fun deleteAll()

    @Query("DELETE FROM events WHERE calendarId = :calendarId")
    suspend fun deleteOfCalendar(calendarId: Long)

    @Query("SELECT * FROM events WHERE calendarId = :calendarId AND href IS NOT NULL")
    suspend fun withHref(calendarId: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId AND dirty = 1")
    suspend fun dirtyOf(calendarId: Long): List<EventEntity>

    @Query("UPDATE events SET href = :href, etag = :etag, dirty = 0 WHERE id = :id")
    suspend fun markPushed(id: Long, href: String, etag: String?)

    @Query("UPDATE events SET href = NULL, etag = NULL WHERE id = :id")
    suspend fun forgetHref(id: Long)

    @Query("UPDATE events SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: Long)
}

@Dao
interface CaldavDao {
    @Insert
    suspend fun insertAccount(account: CaldavAccountEntity): Long

    @Query("SELECT * FROM caldav_accounts ORDER BY id")
    suspend fun accounts(): List<CaldavAccountEntity>

    @Query("DELETE FROM caldav_accounts")
    suspend fun deleteAllAccounts()

    @Query("SELECT * FROM caldav_accounts ORDER BY id")
    fun observeAccounts(): Flow<List<CaldavAccountEntity>>

    @Query("SELECT * FROM caldav_accounts WHERE id = :id")
    suspend fun account(id: Long): CaldavAccountEntity?

    @Query("SELECT * FROM caldav_accounts WHERE serverUrl = :serverUrl AND username = :username LIMIT 1")
    suspend fun findAccount(serverUrl: String, username: String): CaldavAccountEntity?

    @Query("UPDATE caldav_accounts SET secret = :secret WHERE id = :id")
    suspend fun setSecret(id: Long, secret: String)

    @Query("DELETE FROM caldav_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Insert
    suspend fun insertDeletion(deletion: CaldavDeletionEntity)

    @Query("SELECT * FROM caldav_deletions WHERE calendarId = :calendarId")
    suspend fun deletions(calendarId: Long): List<CaldavDeletionEntity>

    @Query("DELETE FROM caldav_deletions WHERE id = :id")
    suspend fun deleteDeletion(id: Long)
}

@Database(
    entities = [CalendarEntity::class, EventEntity::class, CaldavAccountEntity::class, CaldavDeletionEntity::class],
    version = 6,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {
    abstract fun calendarDao(): CalendarDao
    abstract fun eventDao(): EventDao
    abstract fun caldavDao(): CaldavDao

    companion object {
        @Volatile
        private var INSTANCE: CalendarDatabase? = null

        fun get(context: Context): CalendarDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CalendarDatabase::class.java,
                    "nooklin_calendar.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).addCallback(object : Callback() {
                    // The first start has one calendar, so there is always somewhere for an event to go
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "INSERT INTO calendars (name, color, visible, sortOrder) VALUES ('Calendar', ${DEFAULT_CALENDAR_COLOR}, 1, 0)"
                        )
                    }
                }).build().also { INSTANCE = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN reminders TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE calendars ADD COLUMN lastSync INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE calendars ADD COLUMN lastError TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE calendars ADD COLUMN accountId INTEGER")
                db.execSQL("ALTER TABLE calendars ADD COLUMN remoteUrl TEXT")
                db.execSQL("ALTER TABLE calendars ADD COLUMN ctag TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN href TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN etag TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN locked INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS caldav_accounts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "serverUrl TEXT NOT NULL, username TEXT NOT NULL, secret TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS caldav_deletions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "calendarId INTEGER NOT NULL, href TEXT NOT NULL, etag TEXT, " +
                        "FOREIGN KEY(calendarId) REFERENCES calendars(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_caldav_deletions_calendarId ON caldav_deletions (calendarId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caldav_accounts ADD COLUMN service TEXT NOT NULL DEFAULT 'other'")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN travelMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        // The accent blue, 0xFF0A84FF as an Int
        val DEFAULT_CALENDAR_COLOR: Int = 0xFF0A84FF.toInt()
    }
}
