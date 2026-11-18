package org.nooklin.calendar.ui.editor

import java.time.LocalDate
import java.time.LocalDateTime
import org.nooklin.calendar.data.model.Occurrence

/**
 * What the editor opens with. [occurrence] null: a new event on [date]. Otherwise the event is changed, or with
 * [duplicate] a new event is made that starts as a copy of it. A new event can start at a given [start] and [end],
 * for example a free slot found on the dashboard.
 */
data class EditorTarget(
    val occurrence: Occurrence?,
    val duplicate: Boolean,
    val date: LocalDate,
    val start: LocalDateTime? = null,
    val end: LocalDateTime? = null
)
