package org.nooklin.calendar.data

import java.time.Duration
import java.time.LocalDateTime
import org.nooklin.calendar.data.db.EventEntity
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.model.toReading
import org.nooklin.calendar.data.model.toStored
import org.nooklin.calendar.data.recurrence.Recurrence

/** Which events of a repeating series an edit or a delete is about. */
enum class Scope { THIS, FOLLOWING, ALL }

/**
 * What the editor hands over. [start] and [end] are clock readings in the zone of the event ([zoneId]); for an all
 * day event [end] is the start of the day after the last day.
 */
data class EventDraft(
    val calendarId: Long,
    val title: String,
    val location: String,
    val notes: String,
    val allDay: Boolean,
    val zoneId: String?,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val rrule: String?,
    val reminders: List<Int>,
    val travelMinutes: Int = 0
)

/** Rows to change in one go. */
data class Changes(
    val update: List<EventEntity> = emptyList(),
    val insert: List<EventEntity> = emptyList(),
    val delete: List<Long> = emptyList()
)

/**
 * Works out what a change to one event of a series means for the stored rows. Nothing here touches the database,
 * so every case can be tested. [occurrenceStart] is the start of the event that was opened, as a reading in the zone
 * of the series ([org.nooklin.calendar.data.model.Occurrence.sourceStart]).
 */
object EventChanges {

    fun planCreate(draft: EventDraft, uid: String): Changes =
        Changes(insert = listOf(draft.toEntity(0, uid, draft.rrule, "")))

    fun planEdit(
        master: EventEntity,
        occurrenceStart: LocalDateTime,
        draft: EventDraft,
        scope: Scope,
        newUid: () -> String
    ): Changes {
        val masterStart = master.startLocal.toReading()
        val rule = master.rrule?.let { Recurrence.parse(it) }
        // A single event, or a rule this app cannot read (shown once): there is nothing to split
        if (rule == null || scope == Scope.ALL || (scope == Scope.FOLLOWING && occurrenceStart <= masterStart)) {
            return Changes(update = listOf(editAll(master, masterStart, occurrenceStart, draft)))
        }
        if (scope == Scope.THIS) {
            val left = master.copy(exdates = addExdate(master, occurrenceStart))
            return Changes(update = listOf(left), insert = listOf(draft.toEntity(0, newUid(), rrule = null, exdates = "")))
        }
        // FOLLOWING: the series stops before this event and a new series starts with it
        val before = rule.expand(masterStart, masterStart, occurrenceStart)
        val stopsAt = before.last()
        val stopped = master.copy(
            rrule = rule.copy(count = null, until = stopsAt).toRule(),
            exdates = Recurrence.parseExdates(master.exdates).filter { it < occurrenceStart }.toList().format(master.allDay)
        )
        val newRule = if (draft.rrule != null && draft.rrule == master.rrule && rule.count != null) {
            rule.copy(count = rule.count - before.size).toRule()
        } else {
            draft.rrule
        }
        val laterExdates = Recurrence.parseExdates(master.exdates).filter { it >= occurrenceStart }.toList().format(master.allDay)
        return Changes(
            update = listOf(stopped),
            insert = listOf(draft.toEntity(0, newUid(), rrule = newRule, exdates = if (newRule == null) "" else laterExdates))
        )
    }

    fun planDelete(master: EventEntity, occurrenceStart: LocalDateTime, scope: Scope): Changes {
        val masterStart = master.startLocal.toReading()
        val rule = master.rrule?.let { Recurrence.parse(it) }
        if (rule == null || scope == Scope.ALL || (scope == Scope.FOLLOWING && occurrenceStart <= masterStart)) {
            return Changes(delete = listOf(master.id))
        }
        if (scope == Scope.THIS) {
            return Changes(update = listOf(master.copy(exdates = addExdate(master, occurrenceStart))))
        }
        val stopsAt = rule.expand(masterStart, masterStart, occurrenceStart).last()
        return Changes(
            update = listOf(
                master.copy(
                    rrule = rule.copy(count = null, until = stopsAt).toRule(),
                    exdates = Recurrence.parseExdates(master.exdates).filter { it < occurrenceStart }.toList().format(master.allDay)
                )
            )
        )
    }

    // All events of the series: the move of the opened event is applied to the first one
    private fun editAll(
        master: EventEntity,
        masterStart: LocalDateTime,
        occurrenceStart: LocalDateTime,
        draft: EventDraft
    ): EventEntity {
        val delta = Duration.between(occurrenceStart, draft.start)
        val newStart = masterStart.plus(delta)
        val newEnd = newStart.plus(Duration.between(draft.start, draft.end))
        val exdates = if (draft.rrule == null) {
            ""
        } else {
            Recurrence.parseExdates(master.exdates).map { it.plus(delta) }.format(draft.allDay)
        }
        return draft.toEntity(master.id, master.uid, draft.rrule, exdates).copy(
            startLocal = newStart.toStored(),
            endLocal = newEnd.toStored()
        )
    }

    private fun addExdate(master: EventEntity, occurrenceStart: LocalDateTime): String {
        val all = Recurrence.parseExdates(master.exdates) + occurrenceStart
        return all.toList().format(master.allDay)
    }

    private fun List<LocalDateTime>.format(allDay: Boolean): String =
        sorted().joinToString(",") { Recurrence.formatExdate(it, allDay) }

    private fun EventDraft.toEntity(id: Long, uid: String, rrule: String?, exdates: String) = EventEntity(
        id = id,
        calendarId = calendarId,
        uid = uid,
        title = title,
        location = location,
        notes = notes,
        allDay = allDay,
        zoneId = if (allDay) null else zoneId,
        startLocal = start.toStored(),
        endLocal = end.toStored(),
        rrule = rrule,
        exdates = exdates,
        reminders = Reminders.format(reminders),
        travelMinutes = travelMinutes
    )
}
