package org.nooklin.calendar.ui.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale
import org.nooklin.calendar.data.local.WeekStart

/** The rules for the first day of the week and for week numbers that follow from the setting. */
fun weekFieldsFor(setting: WeekStart): WeekFields = when (setting) {
    WeekStart.SYSTEM -> WeekFields.of(Locale.getDefault())
    // Monday with at least four days in the first week: the ISO numbering used in the Netherlands
    WeekStart.MONDAY -> WeekFields.ISO
    WeekStart.SUNDAY -> WeekFields.SUNDAY_START
}

fun LocalDate.startOfWeek(fields: WeekFields): LocalDate =
    with(TemporalAdjusters.previousOrSame(fields.firstDayOfWeek))

fun LocalDate.weekNumber(fields: WeekFields): Int = get(fields.weekOfWeekBasedYear())

/** The seven weekdays in the order they are shown, starting with the first day of the week. */
fun weekdayOrder(fields: WeekFields): List<DayOfWeek> =
    (0L..6L).map { fields.firstDayOfWeek.plus(it) }

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun String.capitalised(): String = replaceFirstChar { it.titlecase(Locale.getDefault()) }

/** "November 2026" */
fun monthTitle(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault())).capitalised()

/** "Thursday 5 November" */
fun dayTitle(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).capitalised()

/** The date in the long form of the phone's language, with the year. */
fun fullDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))

val timeFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())
