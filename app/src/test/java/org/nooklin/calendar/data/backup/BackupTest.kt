package org.nooklin.calendar.data.backup

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupTest {
    private val calendar = BackupCalendar(1, BackupKind.LOCAL, "Home", 0xFF0A84FF.toInt(), true, 0, null, 0, null, null, null)
    private val link = BackupCalendar(2, BackupKind.LINK, "Football", -16711936, false, 1, "https://example.org/a.ics?token=abc", 99, null, null, null)
    private val caldav = BackupCalendar(3, BackupKind.CALDAV, "Work", -65536, true, 2, null, 5, 7, "https://dav.example.org/work/", "ctag1")

    private fun event(calendarId: Long, uid: String) = BackupEvent(
        calendarId, uid, "Title é中", "Office", "Notes\nline 2", false, "Europe/Amsterdam", 1000, 4600,
        "FREQ=WEEKLY", "20260101T090000", "10,1440", "/x.ics", "etag", true, false, 15
    )

    private fun document(format: Int = 1) = BackupDocument(
        format, "1.0.2", 6, 1700000000, linkedSetOf(BackupSection.CALENDARS, BackupSection.CALDAV, BackupSection.LINKS, BackupSection.SETTINGS),
        listOf(calendar, link, caldav), listOf(event(1, "a"), event(2, "b"), event(3, "c")),
        listOf(BackupAccount(7, "https://dav.example.org", "me", "päss word", "other")),
        listOf(BackupDeletion(3, "/gone.ics", null)),
        mapOf("colorful" to false, "bg_sync_minutes" to 30, "theme_mode" to "dark")
    )

    @Test
    fun pbkdf2MatchesTheJavaImplementation() {
        val salt = ByteArray(16) { it.toByte() }
        val ours = BackupCrypto.pbkdf2("correct horse".toByteArray(), salt, 1000)
        val spec = PBEKeySpec("correct horse".toCharArray(), salt, 1000, 256)
        val reference = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        assertArrayEquals(reference, ours)
    }

    @Test
    fun encryptsAndDecrypts() {
        val plain = "hello world".repeat(1000).toByteArray()
        val file = BackupCrypto.encrypt(plain, "secret-pass", rounds = 1000)
        assertArrayEquals(plain, BackupCrypto.decrypt(file, "secret-pass"))
        assertFalse(String(file).contains("hello world"))
    }

    @Test
    fun aPinOfSixDigitsWorks() {
        val file = BackupCrypto.encrypt("data".toByteArray(), "123456", rounds = 1000)
        assertEquals("data", String(BackupCrypto.decrypt(file, "123456")))
    }

    @Test
    fun aWrongSecretIsRefused() {
        val file = BackupCrypto.encrypt("data".toByteArray(), "right-one", rounds = 1000)
        try {
            BackupCrypto.decrypt(file, "wrong-one")
            fail("should not decrypt")
        } catch (e: WrongSecretException) {
            // expected
        }
    }

    @Test
    fun aChangedFileIsRefused() {
        val file = BackupCrypto.encrypt("data".toByteArray(), "right-one", rounds = 1000)
        val changedBody = file.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val changedHeader = file.copyOf().also { it[7] = (it[7].toInt() xor 1).toByte() }
        for (changed in listOf(changedBody, changedHeader)) {
            try {
                BackupCrypto.decrypt(changed, "right-one")
                fail("should not decrypt")
            } catch (e: WrongSecretException) {
                // expected
            } catch (e: BackupFormatException) {
                // a changed round count may also be reported as a damaged file
            }
        }
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() {
        try {
            BackupCrypto.decrypt("just some text that is long enough to pass the size check".toByteArray(), "x")
            fail("should not read")
        } catch (e: BackupFormatException) {
            assertTrue(e.message!!.contains("not a Nooklin Calendar backup"))
        }
    }

    @Test
    fun aNewerFileFormatIsReported() {
        val file = BackupCrypto.encrypt("data".toByteArray(), "right-one", rounds = 1000).also { it[3] = 9 }
        try {
            BackupCrypto.decrypt(file, "right-one")
            fail("should not read")
        } catch (e: BackupFormatException) {
            assertTrue(e.message!!.contains("newer version"))
        }
    }

    @Test
    fun twoBackupsOfTheSameDataDiffer() {
        val a = BackupCrypto.encrypt("data".toByteArray(), "same-pass", rounds = 1000, random = SecureRandom())
        val b = BackupCrypto.encrypt("data".toByteArray(), "same-pass", rounds = 1000, random = SecureRandom())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun jsonRoundTrip() {
        val parsed = BackupJson.parse(BackupJson.write(document()))
        val doc = parsed.document
        assertTrue(parsed.notes.isEmpty())
        assertEquals(3, doc.calendars.size)
        assertEquals(link, doc.calendars[1])
        assertEquals(caldav, doc.calendars[2])
        assertEquals(event(1, "a"), doc.events[0])
        assertEquals("päss word", doc.accounts[0].password)
        assertEquals(BackupDeletion(3, "/gone.ics", null), doc.deletions[0])
        assertEquals(false, doc.settings["colorful"])
        assertEquals(30, doc.settings["bg_sync_minutes"])
        assertEquals("dark", doc.settings["theme_mode"])
        assertEquals(setOf(BackupSection.CALENDARS, BackupSection.CALDAV, BackupSection.LINKS, BackupSection.SETTINGS), doc.sections)
    }

    @Test
    fun unknownFieldsAndPartsAreReportedAndLeftOut() {
        val text = """{"format":1,"app":"1.5.0","dbVersion":9,"created":1,"sections":["calendars","widgets"],
            "calendars":[{"id":1,"name":"Home","icon":"star"}],
            "events":[{"calendarId":1,"uid":"u","startLocal":1,"endLocal":2,"colour":"red"}],
            "tasks":[{"x":1}]}"""
        val parsed = BackupJson.parse(text)
        val notes = parsed.notes.joinToString("\n")
        assertTrue(notes.contains("\"tasks\""))
        assertTrue(notes.contains("\"widgets\""))
        assertTrue(notes.contains("Calendars: the field \"icon\""))
        assertTrue(notes.contains("Events: the field \"colour\""))
        assertEquals(1, parsed.document.calendars.size)
        assertEquals(1, parsed.document.events.size)
        assertEquals(setOf(BackupSection.CALENDARS), parsed.document.sections)
    }

    @Test
    fun missingFieldsGetDefaults() {
        val text = """{"format":1,"calendars":[{"id":4,"name":"Old"}],"events":[{"calendarId":4,"uid":"u","startLocal":10,"endLocal":20}]}"""
        val doc = BackupJson.parse(text).document
        val c = doc.calendars.single()
        assertEquals(BackupKind.LOCAL, c.kind)
        assertEquals(0xFF0A84FF.toInt(), c.color)
        assertTrue(c.visible)
        val e = doc.events.single()
        assertEquals("", e.title)
        assertEquals("", e.reminders)
        assertEquals(0, e.travelMinutes)
        assertFalse(e.dirty)
        assertNull(e.rrule)
        assertEquals(setOf(BackupSection.CALENDARS), doc.sections)
    }

    @Test
    fun damagedAndOrphanedItemsAreCountedNotFatal() {
        val text = """{"format":1,"calendars":[{"id":1,"name":"A"},{"name":"no id"},{"id":2,"name":"Odd","kind":"hologram"}],
            "events":[{"calendarId":1,"uid":"ok","startLocal":1,"endLocal":2},{"calendarId":1,"startLocal":1,"endLocal":2},{"calendarId":9,"uid":"x","startLocal":1,"endLocal":2},{"calendarId":2,"uid":"y","startLocal":1,"endLocal":2}]}"""
        val parsed = BackupJson.parse(text)
        val notes = parsed.notes.joinToString("\n")
        assertEquals(1, parsed.document.calendars.size)
        assertEquals(1, parsed.document.events.size)
        assertTrue(notes.contains("\"hologram\""))
        assertTrue(notes.contains("1 calendars could not be read"))
        assertTrue(notes.contains("1 events could not be read"))
        assertTrue(notes.contains("2 events belong to a calendar that was left out"))
    }

    @Test
    fun aNewerFormatIsReportedButStillRead() {
        val parsed = BackupJson.parse("""{"format":7,"calendars":[{"id":1,"name":"A"}]}""")
        assertTrue(parsed.notes.first().contains("newer version"))
        assertEquals(1, parsed.document.calendars.size)
    }

    @Test
    fun textThatIsNotABackupIsRefused() {
        for (text in listOf("not json", "{}", "[]")) {
            try {
                BackupJson.parse(text)
                fail("should not read: $text")
            } catch (e: BackupFormatException) {
                // expected
            }
        }
    }
}
