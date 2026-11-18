package org.nooklin.calendar.ui.editor

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.nooklin.calendar.ui.theme.MinTouchTarget
import android.Manifest
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.LazyListState
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.nooklin.calendar.data.EventDraft
import org.nooklin.calendar.data.Scope
import org.nooklin.calendar.data.Suggestion
import org.nooklin.calendar.data.TitleMatch
import org.nooklin.calendar.data.TitleParser
import org.nooklin.calendar.data.model.TRAVEL_CHOICES
import org.nooklin.calendar.data.model.travelLabel
import org.nooklin.calendar.ui.common.GlassTextField
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.recurrence.Freq
import org.nooklin.calendar.data.recurrence.Recurrence
import org.nooklin.calendar.reminder.ReminderNotifier
import org.nooklin.calendar.ui.calendar.timeFormatter
import org.nooklin.calendar.ui.common.ClockState
import org.nooklin.calendar.ui.common.NooklinMenu
import org.nooklin.calendar.ui.settings.SettingsActionRow
import org.nooklin.calendar.ui.settings.SettingsGroup
import org.nooklin.calendar.ui.settings.SettingsNote
import org.nooklin.calendar.ui.settings.SettingsToggleRow
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import org.nooklin.calendar.ui.common.GlassPill
import java.time.DayOfWeek
import java.time.format.TextStyle
import org.nooklin.calendar.data.recurrence.ByDay
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private enum class RepeatKind(val label: String) {
    NONE("Never"), DAILY("Every day"), WEEKLY("Every week"), BIWEEKLY("Every 2 weeks"),
    MONTHLY("Every month"), YEARLY("Every year"), CUSTOM("Custom"), KEPT("Custom, as it was")
}

private enum class EndKind(val label: String) { NEVER("Never"), ON_DATE("On a date"), AFTER("After a number of times") }

private class RuleView(val kind: RepeatKind, val end: EndKind, val until: LocalDate?, val count: Int?, val rule: Recurrence? = null)

private enum class MonthMode { DAY, NTH, LAST }

// What the Custom choice shows: how often, how many days or weeks apart, the weekdays (a bit per weekday, Monday is bit 0), and the monthly way
private class CustomView(val freq: Freq, val every: Int, val days: Int, val mode: MonthMode)

// The rules that the Custom rows can make. A rule with more than that (for example two days of the month) is kept as it was.
private fun expressible(rule: Recurrence): Boolean = when (rule.freq) {
    Freq.DAILY, Freq.YEARLY -> rule.byDay.isEmpty() && rule.byMonthDay.isEmpty() && rule.byMonth.isEmpty()
    Freq.WEEKLY -> rule.byDay.all { it.position == 0 } && rule.byMonthDay.isEmpty() && rule.byMonth.isEmpty()
    Freq.MONTHLY -> rule.byMonth.isEmpty() &&
        ((rule.byDay.size == 1 && rule.byDay[0].position != 0 && rule.byMonthDay.isEmpty()) ||
            (rule.byMonthDay.size == 1 && rule.byMonthDay[0] > 0 && rule.byDay.isEmpty()))
}

private fun customOf(rule: Recurrence?, start: LocalDate): CustomView {
    val startBit = 1 shl start.dayOfWeek.ordinal
    if (rule == null || !expressible(rule)) return CustomView(Freq.WEEKLY, 1, startBit, MonthMode.DAY)
    val days = if (rule.freq == Freq.WEEKLY && rule.byDay.isNotEmpty()) {
        rule.byDay.fold(0) { mask, d -> mask or (1 shl d.day.ordinal) }
    } else startBit
    val mode = when {
        rule.freq == Freq.MONTHLY && rule.byDay.isNotEmpty() -> if (rule.byDay[0].position < 0) MonthMode.LAST else MonthMode.NTH
        else -> MonthMode.DAY
    }
    return CustomView(rule.freq, rule.interval, days, mode)
}

// Only plain rules are shown as a choice. A rule that the Custom rows can make shows there, anything else is kept as it is.
private fun viewOf(rrule: String?): RuleView {
    if (rrule == null) return RuleView(RepeatKind.NONE, EndKind.NEVER, null, null)
    val rule = Recurrence.parse(rrule) ?: return RuleView(RepeatKind.KEPT, EndKind.NEVER, null, null)
    val plain = rule.byDay.isEmpty() && rule.byMonthDay.isEmpty() && rule.byMonth.isEmpty()
    val preset = when {
        !plain -> null
        rule.freq == Freq.DAILY && rule.interval == 1 -> RepeatKind.DAILY
        rule.freq == Freq.WEEKLY && rule.interval == 1 -> RepeatKind.WEEKLY
        rule.freq == Freq.WEEKLY && rule.interval == 2 -> RepeatKind.BIWEEKLY
        rule.freq == Freq.MONTHLY && rule.interval == 1 -> RepeatKind.MONTHLY
        rule.freq == Freq.YEARLY && rule.interval == 1 -> RepeatKind.YEARLY
        else -> null
    }
    val kind = preset ?: if (expressible(rule)) RepeatKind.CUSTOM else RepeatKind.KEPT
    val end = when {
        rule.count != null -> EndKind.AFTER
        rule.until != null -> EndKind.ON_DATE
        else -> EndKind.NEVER
    }
    return RuleView(kind, end, rule.until?.toLocalDate(), rule.count, rule)
}

private fun ruleOf(kind: RepeatKind, end: EndKind, until: LocalDate, count: Int): String? {
    val (freq, interval) = when (kind) {
        RepeatKind.DAILY -> Freq.DAILY to 1
        RepeatKind.WEEKLY -> Freq.WEEKLY to 1
        RepeatKind.BIWEEKLY -> Freq.WEEKLY to 2
        RepeatKind.MONTHLY -> Freq.MONTHLY to 1
        RepeatKind.YEARLY -> Freq.YEARLY to 1
        else -> return null
    }
    return Recurrence(
        freq = freq,
        interval = interval,
        count = if (end == EndKind.AFTER) count else null,
        until = if (end == EndKind.ON_DATE) until.atTime(23, 59, 59) else null
    ).toRule()
}

private val shortDate: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    target: EditorTarget,
    clock: ClockState,
    calendars: List<CalendarEntity>,
    onSave: (EventDraft, Scope) -> Unit,
    onCancel: () -> Unit,
    // The earlier event that a new event looks like, see [Suggestion]
    onSuggest: suspend (String) -> Suggestion? = { null },
    // Whether saving without a location asks if that is right, and the way to stop asking
    askLocation: Boolean = true,
    onStopAskingLocation: () -> Unit = {}
) {
    val context = LocalContext.current
    val source = target.occurrence
    // The event that is changed; null when a new one is made (also for a copy)
    val existing = if (target.duplicate) null else source

    // The start values. Saved over a turn of the screen, so what was typed is not lost.
    val firstStart = remember {
        when {
            source != null -> source.sourceStart
            target.start != null -> target.start
            target.date == clock.today -> target.date.atTime((clock.now.hour + 1).coerceAtMost(22), 0)
            else -> target.date.atTime(9, 0)
        }
    }
    val firstEnd = remember {
        when {
            source != null -> source.sourceEnd
            target.end != null -> target.end
            else -> firstStart.plusHours(1)
        }
    }
    val firstAllDay = source?.allDay ?: false
    val firstRule = remember { viewOf(source?.rrule) }

    var title by rememberSaveable { mutableStateOf(source?.title ?: "") }
    var location by rememberSaveable { mutableStateOf(source?.location ?: "") }
    var notes by rememberSaveable { mutableStateOf(source?.notes ?: "") }
    var allDay by rememberSaveable { mutableStateOf(firstAllDay) }
    var calendarId by rememberSaveable {
        mutableStateOf(source?.calendarId ?: calendars.firstOrNull { it.visible }?.id ?: calendars.firstOrNull()?.id ?: 0L)
    }
    var startDate by rememberSaveable { mutableStateOf(firstStart.toLocalDate()) }
    var startTime by rememberSaveable { mutableStateOf(if (firstAllDay) LocalTime.of(9, 0) else firstStart.toLocalTime()) }
    var endDate by rememberSaveable {
        mutableStateOf(
            if (firstAllDay) maxOf(firstStart.toLocalDate(), firstEnd.toLocalDate().minusDays(1)) else firstEnd.toLocalDate()
        )
    }
    var endTime by rememberSaveable { mutableStateOf(if (firstAllDay) LocalTime.of(10, 0) else firstEnd.toLocalTime()) }
    var repeatKind by rememberSaveable { mutableStateOf(firstRule.kind) }
    var endKind by rememberSaveable { mutableStateOf(firstRule.end) }
    var untilDate by rememberSaveable { mutableStateOf(firstRule.until ?: firstStart.toLocalDate().plusMonths(1)) }
    var countText by rememberSaveable { mutableStateOf((firstRule.count ?: 10).toString()) }
    var ruleTouched by rememberSaveable { mutableStateOf(false) }
    val firstCustom = remember { customOf(firstRule.rule, firstStart.toLocalDate()) }
    var customFreq by rememberSaveable { mutableStateOf(firstCustom.freq) }
    var customEvery by rememberSaveable { mutableStateOf(firstCustom.every.toString()) }
    var customDays by rememberSaveable { mutableStateOf(firstCustom.days) }
    var monthMode by rememberSaveable { mutableStateOf(firstCustom.mode) }
    var reminderText by rememberSaveable { mutableStateOf(Reminders.format(source?.reminders ?: emptyList())) }
    var pickDate by rememberSaveable { mutableStateOf(0) }
    var pickTime by rememberSaveable { mutableStateOf(0) }
    var askScope by rememberSaveable { mutableStateOf(false) }
    var travelMinutes by rememberSaveable { mutableStateOf(source?.travelMinutes ?: 0) }
    var pickTravel by rememberSaveable { mutableStateOf(false) }
    var askNoLocation by rememberSaveable { mutableStateOf(false) }
    var noLocationText by rememberSaveable { mutableStateOf("") }
    var stopAsking by rememberSaveable { mutableStateOf(false) }

    // A new event takes over what an earlier event with a similar title had, once, when the typing stops
    val firstCalendar = remember { calendarId }
    var filled by rememberSaveable { mutableStateOf(false) }
    var filledFrom by remember { mutableStateOf<String?>(null) }
    var undoFill by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(title) {
        if (source != null || filled || title.trim().length < TitleMatch.MIN_LENGTH) return@LaunchedEffect
        delay(600)
        val s = onSuggest(title) ?: return@LaunchedEffect
        filled = true
        val undo = ArrayList<() -> Unit>()
        // Only what is still empty or untouched is filled in, so nothing that was typed or picked is replaced
        val timeUntouched = !allDay && startDate == firstStart.toLocalDate() && startTime == firstStart.toLocalTime() &&
            endDate == firstEnd.toLocalDate() && endTime == firstEnd.toLocalTime()
        if (timeUntouched && (s.allDay || s.seconds != Duration.between(firstStart, firstEnd).seconds)) {
            val oldAllDay = allDay
            val oldEndDate = endDate
            val oldEndTime = endTime
            val oldReminders = reminderText
            if (s.allDay) {
                allDay = true
                endDate = startDate.plusDays((s.seconds / 86400 - 1).coerceAtLeast(0))
                // The options of a reminder differ between an all day and a timed event
                reminderText = ""
            } else {
                val end = startDate.atTime(startTime).plusSeconds(s.seconds)
                endDate = end.toLocalDate()
                endTime = end.toLocalTime()
            }
            undo.add { allDay = oldAllDay; endDate = oldEndDate; endTime = oldEndTime; reminderText = oldReminders }
        }
        if (calendarId == firstCalendar && s.calendarId != calendarId && calendars.any { it.id == s.calendarId }) {
            val old = calendarId
            calendarId = s.calendarId
            undo.add { calendarId = old }
        }
        if (reminderText.isBlank() && s.reminders.isNotEmpty() && s.allDay == allDay) {
            val old = reminderText
            reminderText = Reminders.format(s.reminders)
            undo.add { reminderText = old }
        }
        if (location.isBlank() && s.location.isNotBlank()) {
            val old = location
            location = s.location
            undo.add { location = old }
        }
        if (notes.isBlank() && s.notes.isNotBlank()) {
            val old = notes
            notes = s.notes
            undo.add { notes = old }
        }
        if (undo.isNotEmpty()) {
            filledFrom = s.title
            undoFill = { undo.asReversed().forEach { it() } }
        }
    }

    val reminders = Reminders.parse(reminderText)
    var notificationsOn by remember { mutableStateOf(ReminderNotifier.canShow(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsOn = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notificationsOn = ReminderNotifier.canShow(context) }

    fun moveStart(date: LocalDate, time: LocalTime) {
        if (allDay) {
            endDate = endDate.plusDays(ChronoUnit.DAYS.between(startDate, date))
        } else {
            val length = Duration.between(startDate.atTime(startTime), endDate.atTime(endTime))
            val end = date.atTime(time).plus(length)
            endDate = end.toLocalDate()
            endTime = end.toLocalTime()
        }
        startDate = date
        startTime = time
    }

    val count = countText.toIntOrNull() ?: 0
    val repeating = repeatKind != RepeatKind.NONE
    val rangeOk = if (allDay) !endDate.isBefore(startDate) else !endDate.atTime(endTime).isBefore(startDate.atTime(startTime))
    val customEveryValue = customEvery.toIntOrNull() ?: 0
    val customOk = repeatKind != RepeatKind.CUSTOM || customEveryValue in 1..999
    val repeatOk = customOk && (!(repeating && repeatKind != RepeatKind.KEPT) ||
        when (endKind) {
            EndKind.NEVER -> true
            EndKind.AFTER -> count >= 1
            EndKind.ON_DATE -> !untilDate.isBefore(startDate)
        })
    val valid = title.isNotBlank() && calendars.any { it.id == calendarId } && rangeOk && repeatOk

    fun customRule(): Recurrence {
        val every = customEveryValue.coerceIn(1, 999)
        val base = when (customFreq) {
            Freq.DAILY -> Recurrence(Freq.DAILY, every)
            Freq.WEEKLY -> Recurrence(
                Freq.WEEKLY, every,
                byDay = DayOfWeek.entries.filter { customDays and (1 shl it.ordinal) != 0 }.map { ByDay(it) }
            )
            Freq.MONTHLY -> when (monthMode) {
                MonthMode.DAY -> Recurrence(Freq.MONTHLY, every, byMonthDay = listOf(startDate.dayOfMonth))
                MonthMode.NTH -> Recurrence(Freq.MONTHLY, every, byDay = listOf(ByDay(startDate.dayOfWeek, (startDate.dayOfMonth - 1) / 7 + 1)))
                MonthMode.LAST -> Recurrence(Freq.MONTHLY, every, byDay = listOf(ByDay(startDate.dayOfWeek, -1)))
            }
            Freq.YEARLY -> Recurrence(Freq.YEARLY, every)
        }
        return base.copy(
            count = if (endKind == EndKind.AFTER) count else null,
            until = if (endKind == EndKind.ON_DATE) untilDate.atTime(23, 59, 59) else null
        )
    }

    fun draft(): EventDraft {
        val rrule = when {
            !ruleTouched -> source?.rrule
            repeatKind == RepeatKind.KEPT -> source?.rrule
            repeatKind == RepeatKind.CUSTOM -> customRule().toRule()
            else -> ruleOf(repeatKind, endKind, untilDate, count)
        }
        val zone = if (source != null && !source.allDay) source.zoneId else clock.zone.id
        return EventDraft(
            calendarId = calendarId,
            title = title.trim(),
            location = location.trim(),
            notes = notes.trim(),
            allDay = allDay,
            zoneId = if (allDay) null else zone,
            start = if (allDay) startDate.atStartOfDay() else startDate.atTime(startTime),
            end = if (allDay) endDate.plusDays(1).atStartOfDay() else endDate.atTime(endTime),
            rrule = rrule,
            reminders = reminders,
            travelMinutes = if (allDay) 0 else travelMinutes
        )
    }

    fun saveNow() {
        if (existing != null && existing.repeats) askScope = true else onSave(draft(), Scope.ALL)
    }

    fun save() {
        if (location.isBlank() && askLocation) {
            noLocationText = ""
            stopAsking = false
            askNoLocation = true
        } else {
            saveNow()
        }
    }

    // A date and a time in the title, offered as chips above the keyboard
    val hits = TitleParser.parse(title, clock.today)
    val dateHit = hits.firstOrNull { it.isDate }
    val timeHit = if (allDay) null else hits.firstOrNull { !it.isDate }

    Scaffold(
        modifier = Modifier.imePadding(),
        bottomBar = {
            if (dateHit != null || timeHit != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chip = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    if (dateHit != null && dateHit.date != null) {
                        Text(
                            DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()).format(dateHit.date),
                            modifier = Modifier.clip(CircleShape).background(chip)
                                .clickable {
                                    moveStart(dateHit.date, startTime)
                                    title = TitleParser.remove(title, dateHit)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    if (timeHit != null && timeHit.time != null) {
                        Text(
                            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(timeHit.time),
                            modifier = Modifier.clip(CircleShape).background(chip)
                                .clickable {
                                    moveStart(startDate, timeHit.time)
                                    title = TitleParser.remove(title, timeHit)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing != null) "Edit Event" else "New Event",
                        fontSize = 28.sp, fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, contentDescription = "Cancel") }
                },
                actions = {
                    TextButton(onClick = { save() }, enabled = valid) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            SettingsGroup(
                null,
                listOf(
                    { PlainField(title, { title = it }, "Title") },
                    { PlainField(location, { location = it }, "Location") }
                )
            )
            filledFrom?.let { from ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Filled in from \"$from\"",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { undoFill?.invoke(); undoFill = null; filledFrom = null }) { Text("Undo") }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                null,
                listOf(
                    {
                        PickerRow(
                            label = "Calendar",
                            value = calendars.firstOrNull { it.id == calendarId }?.name ?: "",
                            options = calendars,
                            optionLabel = { it.name },
                            onSelect = { calendarId = it.id },
                            dot = calendars.firstOrNull { it.id == calendarId }?.color,
                            optionDot = { it.color },
                            isChosen = { it.id == calendarId }
                        )
                    },
                    {
                        SettingsToggleRow("All-day", allDay, Icons.Filled.DateRange) { on ->
                            allDay = on
                            // The options of a reminder differ between an all day and a timed event
                            reminderText = ""
                        }
                    },
                    {
                        FieldRow("Starts") {
                            ValueText(startDate.format(shortDate)) { pickDate = 1 }
                            if (!allDay) ValueText(startTime.format(timeFormatter)) { pickTime = 1 }
                        }
                    },
                    {
                        FieldRow("Ends") {
                            ValueText(endDate.format(shortDate)) { pickDate = 2 }
                            if (!allDay) ValueText(endTime.format(timeFormatter)) { pickTime = 2 }
                        }
                    }
                )
            )
            if (!rangeOk) SettingsNote("The end is before the start.")
            zoneNote(source, clock)?.let { SettingsNote(it) }
            if (!allDay) {
                Spacer(Modifier.height(16.dp))
                SettingsGroup(
                    null,
                    listOf({
                        FieldRow("Travel time") { ValueText(travelLabel(travelMinutes)) { pickTravel = true } }
                    })
                )
                if (travelMinutes > 0) SettingsNote("Reminders go off ${travelLabel(travelMinutes)} earlier, so you can leave on time.")
            }

            Spacer(Modifier.height(16.dp))
            val repeatRows = ArrayList<@Composable () -> Unit>()
            repeatRows.add {
                PickerRow(
                    label = "Repeat",
                    value = repeatKind.label,
                    options = RepeatKind.entries.filter { it != RepeatKind.KEPT || firstRule.kind == RepeatKind.KEPT },
                    optionLabel = { it.label },
                    onSelect = { repeatKind = it; ruleTouched = true }
                )
            }
            if (repeatKind == RepeatKind.CUSTOM) {
                repeatRows.add {
                    PickerRow(
                        label = "Frequency",
                        value = frequencyLabel(customFreq),
                        options = Freq.entries,
                        optionLabel = { frequencyLabel(it) },
                        onSelect = { customFreq = it; ruleTouched = true }
                    )
                }
                repeatRows.add {
                    FieldRow("Every") {
                        TextField(
                            value = customEvery,
                            onValueChange = { text -> customEvery = text.filter { it.isDigit() }.take(3); ruleTouched = true },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp),
                            colors = plainColors()
                        )
                        Text(frequencyUnit(customFreq, customEveryValue))
                    }
                }
                if (customFreq == Freq.WEEKLY) {
                    repeatRows.add { WeekdayRow(customDays) { customDays = it; ruleTouched = true } }
                }
                if (customFreq == Freq.MONTHLY) {
                    val lastPossible = startDate.plusDays(7).month != startDate.month
                    repeatRows.add {
                        PickerRow(
                            label = "On",
                            value = monthModeLabel(monthMode, startDate),
                            options = MonthMode.entries.filter { it != MonthMode.LAST || lastPossible },
                            optionLabel = { monthModeLabel(it, startDate) },
                            onSelect = { monthMode = it; ruleTouched = true }
                        )
                    }
                }
            }
            if (repeating && repeatKind != RepeatKind.KEPT) {
                repeatRows.add {
                    PickerRow(
                        label = "End repeat",
                        value = endKind.label,
                        options = EndKind.entries,
                        optionLabel = { it.label },
                        onSelect = { endKind = it; ruleTouched = true }
                    )
                }
                if (endKind == EndKind.ON_DATE) {
                    repeatRows.add { FieldRow("Last day") { ValueText(untilDate.format(shortDate)) { pickDate = 3 } } }
                }
                if (endKind == EndKind.AFTER) {
                    repeatRows.add {
                        FieldRow("Times") {
                            TextField(
                                value = countText,
                                onValueChange = { text -> countText = text.filter { it.isDigit() }.take(4); ruleTouched = true },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(96.dp),
                                colors = plainColors()
                            )
                        }
                    }
                }
            }
            SettingsGroup(null, repeatRows)
            if (repeatKind == RepeatKind.KEPT) {
                SettingsNote("This event repeats by a rule that the editor cannot show. It is kept as it is. Choose another repeat to replace it.")
            }
            if (repeating && repeatKind == RepeatKind.MONTHLY && startDate.dayOfMonth > 28) {
                SettingsNote("A month that has no day ${startDate.dayOfMonth} is skipped.")
            }

            Spacer(Modifier.height(16.dp))
            val reminderRows = ArrayList<@Composable () -> Unit>()
            reminders.forEach { minutes ->
                reminderRows.add {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).padding(start = 16.dp, end = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Reminders.label(minutes, allDay))
                        IconButton(onClick = { reminderText = Reminders.format(reminders - minutes) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove reminder")
                        }
                    }
                }
            }
            val choices = (if (allDay) Reminders.ALL_DAY_OPTIONS else Reminders.TIMED_OPTIONS).filter { it !in reminders }
            if (choices.isNotEmpty()) {
                reminderRows.add {
                    PickerRow(
                        label = "Add reminder",
                        value = "",
                        options = choices,
                        optionLabel = { Reminders.label(it, allDay) },
                        onSelect = {
                            reminderText = Reminders.format(reminders + it)
                            if (!notificationsOn) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        icon = Icons.Filled.Add
                    )
                }
            }
            SettingsGroup("Reminders", reminderRows)
            if (reminders.isNotEmpty() && !notificationsOn) {
                SettingsNote("Notifications are off for this app, so a reminder cannot show. Allow them to get reminders.")
                SettingsGroup(
                    null,
                    listOf(
                        { SettingsActionRow("Allow notifications", Icons.Filled.Notifications) { permission.launch(Manifest.permission.POST_NOTIFICATIONS) } },
                        {
                            SettingsActionRow("Open notification settings", Icons.Filled.Settings) {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        }
                    )
                )
            }

            Spacer(Modifier.height(16.dp))
            SettingsGroup(null, listOf({ PlainField(notes, { notes = it }, "Notes", singleLine = false) }))
            Spacer(Modifier.height(32.dp))
        }
    }

    if (pickDate != 0) {
        val initial = when (pickDate) { 1 -> startDate; 2 -> endDate; else -> untilDate }
        DatePickDialog(initial, onPick = { date ->
            when (pickDate) {
                1 -> moveStart(date, startTime)
                2 -> endDate = date
                else -> { untilDate = date; ruleTouched = true }
            }
        }, onDismiss = { pickDate = 0 })
    }
    if (pickTime != 0) {
        val initial = if (pickTime == 1) startTime else endTime
        // For the end: the length of the event as buttons, counted from the start
        val quick = if (pickTime == 2) {
            listOf(15L, 30L, 60L, 120L)
                .map { minutes -> travelLabel(minutes.toInt()) to startDate.atTime(startTime).plusMinutes(minutes) }
        } else {
            emptyList()
        }
        TimePickDialog(initial, quick, onQuick = { end -> endDate = end.toLocalDate(); endTime = end.toLocalTime() }, onPick = { time ->
            if (pickTime == 1) moveStart(startDate, time) else endTime = time
        }, onDismiss = { pickTime = 0 })
    }
    if (pickTravel) {
        TravelDialog(travelMinutes, onPick = { travelMinutes = it }, onDismiss = { pickTravel = false })
    }
    if (askNoLocation) {
        GlassAlertDialog(
            onDismissRequest = { askNoLocation = false },
            wide = true,
            title = { Text("No location") },
            text = {
                Column {
                    Text("This event has no location. Is that right?")
                    Spacer(Modifier.height(12.dp))
                    GlassTextField(
                        value = noLocationText,
                        onValueChange = { noLocationText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add a location") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { stopAsking = !stopAsking },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = stopAsking, onCheckedChange = { stopAsking = it })
                        Text("Don't ask again")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    askNoLocation = false
                    if (noLocationText.isNotBlank()) location = noLocationText.trim()
                    if (stopAsking) onStopAskingLocation()
                    saveNow()
                }) { Text(if (noLocationText.isBlank()) "Save without location" else "Save with location") }
            },
            dismissButton = { TextButton(onClick = { askNoLocation = false }) { Text("Back") } }
        )
    }
    if (askScope) {
        ScopeDialog(
            title = "This event is part of a series",
            destructive = false,
            onPick = { scope -> askScope = false; onSave(draft(), scope) },
            onDismiss = { askScope = false }
        )
    }
}

private fun zoneNote(source: Occurrence?, clock: ClockState): String? {
    if (source == null || source.allDay) return null
    val zone = source.zoneId
    return when {
        zone == null -> "Floating time: the times follow the clock of this phone."
        zone != clock.zone.id -> "The times are in the time zone $zone, where this event was made."
        else -> null
    }
}

@Composable
private fun plainColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)

@Composable
private fun PlainField(value: String, onChange: (String) -> Unit, placeholder: String, singleLine: Boolean = true) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth(),
        colors = plainColors()
    )
}

private fun frequencyLabel(freq: Freq): String = when (freq) {
    Freq.DAILY -> "Daily"
    Freq.WEEKLY -> "Weekly"
    Freq.MONTHLY -> "Monthly"
    Freq.YEARLY -> "Yearly"
}

private fun frequencyUnit(freq: Freq, every: Int): String {
    val one = every == 1
    return when (freq) {
        Freq.DAILY -> if (one) "day" else "days"
        Freq.WEEKLY -> if (one) "week" else "weeks"
        Freq.MONTHLY -> if (one) "month" else "months"
        Freq.YEARLY -> if (one) "year" else "years"
    }
}

private fun monthModeLabel(mode: MonthMode, date: LocalDate): String {
    val day = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return when (mode) {
        MonthMode.DAY -> "Day ${date.dayOfMonth}"
        MonthMode.NTH -> "The " + listOf("first", "second", "third", "fourth", "fifth")[(date.dayOfMonth - 1) / 7] + " " + day
        MonthMode.LAST -> "The last $day"
    }
}

/** The seven weekdays as round buttons; at least one stays chosen. */
@Composable
private fun WeekdayRow(mask: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DayOfWeek.entries.forEach { day ->
            val on = mask and (1 shl day.ordinal) != 0
            // The circle stays 38 dp, the part you tap is 48 dp high and a seventh of the width
            Box(
                Modifier
                    .weight(1f)
                    .height(MinTouchTarget)
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
                    .clickable {
                        val next = mask xor (1 shl day.ordinal)
                        if (next != 0) onChange(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (on) MaterialTheme.colorScheme.primary else GlassPill.adaptiveGlassBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinTouchTarget).padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
}

@Composable
private fun ValueText(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.heightIn(min = MinTouchTarget).clickable(onClick = onClick).padding(vertical = 10.dp)
    )
}

@Composable
private fun <T> PickerRow(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    dot: Int? = null,
    icon: ImageVector? = null,
    // A colour dot before each option, and a tick at the chosen one
    optionDot: ((T) -> Int?)? = null,
    isChosen: ((T) -> Boolean)? = null
) {
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Box {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dot != null) {
                    Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(Color(dot)))
                }
                if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(value, color = MaterialTheme.colorScheme.primary)
            }
            NooklinMenu(expanded = open, onDismiss = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        leadingIcon = optionDot?.invoke(option)?.let { color ->
                            { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(color))) }
                        },
                        trailingIcon = if (isChosen?.invoke(option) == true) {
                            { Icon(Icons.Filled.Check, contentDescription = "Chosen", tint = MaterialTheme.colorScheme.primary) }
                        } else {
                            null
                        },
                        onClick = { open = false; onSelect(option) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        colors = DatePickerDefaults.colors(containerColor = GlassPill.adaptivePopupBackground),
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        val accent = MaterialTheme.colorScheme.primary
        DatePicker(
            state = state,
            colors = DatePickerDefaults.colors(
                containerColor = Color.Transparent,
                titleContentColor = accent,
                headlineContentColor = accent,
                weekdayContentColor = accent,
                navigationContentColor = accent,
                currentYearContentColor = accent,
                selectedYearContainerColor = accent,
                selectedDayContainerColor = accent,
                todayContentColor = accent,
                todayDateBorderColor = accent
            )
        )
    }
}

private const val LOOPS = 1000

/** The row of [index] in the middle of a wheel that repeats its [count] values; non-looping wheels start at [index]. */
private fun wheelStart(count: Int, loop: Boolean, index: Int): Int = if (loop) (LOOPS / 2) * count + index else index

/**
 * One wheel of values that snaps to the middle row. A looping wheel repeats its values, so 55 is followed by 00.
 * [onChange] gets the number of the row in the middle, counted from the top of the whole list.
 */
@Composable
private fun Wheel(
    count: Int,
    loop: Boolean,
    state: LazyListState,
    label: (Int) -> String,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val centered by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index ?: state.firstVisibleItemIndex
        }
    }
    val haptic = LocalHapticFeedback.current
    // A tick for every value that passes, not for the one the wheel opens on
    var last by remember { mutableIntStateOf(centered) }
    LaunchedEffect(centered) {
        if (centered != last) {
            last = centered
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        onChange(centered)
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Center),
        contentPadding = PaddingValues(vertical = 80.dp),
        modifier = modifier.height(200.dp)
    ) {
        items(if (loop) count * LOOPS else count) { row ->
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    label(row % count),
                    fontSize = 22.sp,
                    fontWeight = if (row == centered) FontWeight.Bold else FontWeight.Normal,
                    color = if (row == centered) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
}

/** The travel time as a wheel of the choices. */
@Composable
private fun TravelDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = TRAVEL_CHOICES.indexOf(initial).coerceAtLeast(0)
    val state = rememberLazyListState(start)
    var row by remember { mutableIntStateOf(start) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Travel time") },
        confirmButton = {
            TextButton(onClick = {
                // A value from another app that is not a choice stays as it is when the wheel was not turned
                onPick(if (row == start && initial !in TRAVEL_CHOICES) initial else TRAVEL_CHOICES[row.coerceIn(0, TRAVEL_CHOICES.lastIndex)])
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                )
                Wheel(
                    TRAVEL_CHOICES.size, false, state,
                    label = { travelLabel(TRAVEL_CHOICES[it]) },
                    onChange = { row = it },
                    modifier = Modifier.width(160.dp)
                )
            }
        }
    )
}

/** A time with wheels for the hour and the minute (steps of 5 minutes). [quick] are end times to tap instead of turning. */
@Composable
private fun TimePickDialog(
    initial: LocalTime,
    quick: List<Pair<String, LocalDateTime>>,
    onQuick: (LocalDateTime) -> Unit,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val hourCount = if (is24) 24 else 12
    val hourStart = wheelStart(hourCount, true, if (is24) initial.hour else initial.hour % 12)
    val minuteStart = wheelStart(12, true, initial.minute / 5)
    val hourState = rememberLazyListState(hourStart)
    val minuteState = rememberLazyListState(minuteStart)
    val ampmState = rememberLazyListState(if (initial.hour >= 12) 1 else 0)
    val scope = rememberCoroutineScope()
    var hourRow by remember { mutableIntStateOf(hourStart) }
    var hourTarget by remember { mutableIntStateOf(hourStart) }
    var minuteRow by remember { mutableIntStateOf(minuteStart) }
    var pm by remember { mutableStateOf(initial.hour >= 12) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val hourIndex = hourRow % hourCount
                val hour = if (is24) hourIndex else hourIndex % 12 + if (pm) 12 else 0
                // A minute that was not turned keeps its exact value, also when it is not a multiple of 5
                val minuteIndex = minuteRow % 12
                val minute = if (minuteIndex == initial.minute / 5) initial.minute else minuteIndex * 5
                onPick(LocalTime.of(hour, minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Wheel(
                            hourCount, true, hourState,
                            label = { if (is24) "%02d".format(it) else if (it == 0) "12" else it.toString() },
                            onChange = { row ->
                                // On a 12 hour clock the AM or PM changes every time the hours go round
                                if (!is24 && Math.floorDiv(row, 12) != Math.floorDiv(hourRow, 12)) {
                                    pm = !pm
                                    scope.launch { ampmState.animateScrollToItem(if (pm) 1 else 0) }
                                }
                                hourRow = row
                            },
                            modifier = Modifier.width(72.dp)
                        )
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Wheel(
                            12, true, minuteState,
                            label = { "%02d".format(it * 5) },
                            onChange = { row ->
                                // Past 55 the hour goes on, and back past 00 it goes back
                                val carry = Math.floorDiv(row, 12) - Math.floorDiv(minuteRow, 12)
                                minuteRow = row
                                if (carry != 0) {
                                    val base = if (hourState.isScrollInProgress) hourTarget else hourRow
                                    hourTarget = base + carry
                                    scope.launch { hourState.animateScrollToItem(hourTarget) }
                                }
                            },
                            modifier = Modifier.width(72.dp)
                        )
                        if (!is24) {
                            Wheel(
                                2, false, ampmState,
                                label = { if (it == 0) "AM" else "PM" },
                                onChange = { row -> pm = row == 1 },
                                modifier = Modifier.width(72.dp)
                            )
                        }
                    }
                }
                if (quick.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Length of the event", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        quick.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (text, end) ->
                                    Box(
                                        Modifier.weight(1f).clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                            .clickable { onQuick(end); onDismiss() }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
