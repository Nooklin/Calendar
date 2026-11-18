package org.nooklin.calendar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.nooklin.calendar.data.CalendarRepository
import org.nooklin.calendar.data.EventDraft
import org.nooklin.calendar.data.ImportResult
import org.nooklin.calendar.data.ImportTarget
import org.nooklin.calendar.data.ics.IcsFile
import org.nooklin.calendar.data.Scope
import org.nooklin.calendar.data.dashboard.DashboardSettings
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.local.AppFont
import org.nooklin.calendar.data.local.AppPreferences
import org.nooklin.calendar.data.local.AppThemeMode
import org.nooklin.calendar.data.local.ContrastColor
import org.nooklin.calendar.data.local.LockDelay
import org.nooklin.calendar.data.local.MonthDensity
import org.nooklin.calendar.data.local.WeekStart
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.widget.WidgetUpdater
import org.nooklin.calendar.ui.editor.EditorTarget
import kotlinx.coroutines.flow.MutableStateFlow
import org.nooklin.calendar.data.SubscribeResult
import org.nooklin.calendar.data.Suggestion
import org.nooklin.calendar.data.caldav.CaldavService
import org.nooklin.calendar.data.caldav.ConnectResult
import org.nooklin.calendar.data.db.CaldavAccountEntity

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = AppPreferences(application)
    private val repository = CalendarRepository(application)

    // What the editor is open for; kept here so a turn of the screen does not close it
    var editorTarget by mutableStateOf<EditorTarget?>(null)

    val themeMode: StateFlow<AppThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.SYSTEM)

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    val contrastColor: StateFlow<ContrastColor> = preferences.contrastColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContrastColor.DEFAULT)

    fun setContrastColor(color: ContrastColor) {
        viewModelScope.launch { preferences.setContrastColor(color) }
    }

    val appFont: StateFlow<AppFont> = preferences.appFont
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppFont.INTER)

    fun setAppFont(font: AppFont) {
        viewModelScope.launch { preferences.setAppFont(font) }
    }

    val weekStart: StateFlow<WeekStart> = preferences.weekStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekStart.SYSTEM)

    fun setWeekStart(value: WeekStart) {
        viewModelScope.launch { preferences.setWeekStart(value) }
    }

    val showWeekNumbers: StateFlow<Boolean> = preferences.showWeekNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShowWeekNumbers(value: Boolean) {
        viewModelScope.launch { preferences.setShowWeekNumbers(value) }
    }

    val colorful: StateFlow<Boolean> = preferences.colorful
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setColorful(value: Boolean) {
        viewModelScope.launch { preferences.setColorful(value) }
    }

    val monthDensity: StateFlow<MonthDensity> = preferences.monthDensity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthDensity.COMPACT)

    fun setMonthDensity(value: MonthDensity) {
        viewModelScope.launch { preferences.setMonthDensity(value) }
    }

    val appLock: StateFlow<Boolean> = preferences.appLock
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setAppLock(value: Boolean) {
        viewModelScope.launch { preferences.setAppLock(value) }
    }

    val lockDelay: StateFlow<LockDelay> = preferences.lockDelay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LockDelay.ONE_MINUTE)

    fun setLockDelay(value: LockDelay) {
        viewModelScope.launch { preferences.setLockDelay(value) }
    }

    val allowScreenshots: StateFlow<Boolean> = preferences.allowScreenshots
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAllowScreenshots(value: Boolean) {
        viewModelScope.launch { preferences.setAllowScreenshots(value) }
    }

    val lockScreenNotifications: StateFlow<Boolean> = preferences.lockScreenNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setLockScreenNotifications(value: Boolean) {
        viewModelScope.launch { preferences.setLockScreenNotifications(value) }
    }

    val dashboard: StateFlow<DashboardSettings> = preferences.dashboard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSettings())

    fun setDashboard(value: DashboardSettings) {
        viewModelScope.launch { preferences.setDashboard(value) }
    }

    // null until the setting is read, so the tour does not flash on the screen
    val tourDone: StateFlow<Boolean?> = preferences.tourDone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTourDone(value: Boolean) {
        viewModelScope.launch { preferences.setTourDone(value) }
    }

    val dutchHolidays: StateFlow<Boolean> = preferences.dutchHolidays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setDutchHolidays(value: Boolean) {
        viewModelScope.launch {
            preferences.setDutchHolidays(value)
            WidgetUpdater.updateAll(getApplication())
        }
    }

    val backgroundSync: StateFlow<Boolean> = preferences.backgroundSync
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val backgroundSyncMinutes: StateFlow<Int> = preferences.backgroundSyncMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val backgroundSyncWifiOnly: StateFlow<Boolean> = preferences.backgroundSyncWifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setBackgroundSync(value: Boolean) {
        viewModelScope.launch { preferences.setBackgroundSync(value) }
    }

    fun setBackgroundSyncMinutes(value: Int) {
        viewModelScope.launch { preferences.setBackgroundSyncMinutes(value) }
    }

    fun setBackgroundSyncWifiOnly(value: Boolean) {
        viewModelScope.launch { preferences.setBackgroundSyncWifiOnly(value) }
    }

    val askLocation: StateFlow<Boolean> = preferences.askLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAskLocation(value: Boolean) {
        viewModelScope.launch { preferences.setAskLocation(value) }
    }

    val calendars: StateFlow<List<CalendarEntity>> = repository.calendars
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCalendar(name: String, color: Int) {
        viewModelScope.launch { repository.addCalendar(name, color) }
    }

    fun updateCalendar(id: Long, name: String, color: Int) {
        viewModelScope.launch { repository.updateCalendar(id, name, color) }
    }

    fun deleteCalendar(id: Long) {
        viewModelScope.launch { repository.deleteCalendar(id) }
    }

    // The followed calendars that are being downloaded right now
    private val syncingIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncing: StateFlow<Set<Long>> = syncingIds

    private suspend fun refreshNow(id: Long) {
        if (id in syncingIds.value) return
        syncingIds.value = syncingIds.value + id
        try {
            repository.refresh(id)
        } finally {
            syncingIds.value = syncingIds.value - id
        }
    }

    fun refresh(id: Long) {
        viewModelScope.launch { refreshNow(id) }
    }

    suspend fun subscribe(name: String, color: Int, url: String): SubscribeResult = repository.subscribe(name, color, url)

    suspend fun connectCaldav(server: String, username: String, password: String, service: CaldavService): ConnectResult =
        repository.connectCaldav(server, username, password, service)

    val caldavAccounts: StateFlow<List<CaldavAccountEntity>> = repository.caldavAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun suggestionFor(title: String): Suggestion? = repository.suggestionFor(title)

    // At the start of the app the followed calendars are refreshed, when the last good refresh is more than an hour old.
    // A CalDAV calendar is synced at every start: it first asks the server whether anything changed, which is quick.
    init {
        viewModelScope.launch {
            repository.protectLinks()
            val limit = System.currentTimeMillis() / 1000 - 3600
            repository.subscriptions().filter { it.accountId != null || it.lastSync < limit }.forEach { refreshNow(it.id) }
        }
    }

    fun setCalendarVisible(id: Long, visible: Boolean) {
        viewModelScope.launch { repository.setCalendarVisible(id, visible) }
    }

    fun occurrences(from: LocalDate, toExclusive: LocalDate, zone: ZoneId): Flow<List<Occurrence>> =
        repository.occurrences(from, toExclusive, zone)

    fun save(draft: EventDraft, occurrence: Occurrence?, scope: Scope) {
        viewModelScope.launch { repository.save(draft, occurrence, scope) }
    }

    fun delete(occurrence: Occurrence, scope: Scope) {
        viewModelScope.launch { repository.delete(occurrence, scope) }
    }

    suspend fun search(query: String, today: LocalDate, zone: ZoneId): List<Occurrence> =
        repository.search(query, today, zone)

    suspend fun importIcs(file: IcsFile, target: ImportTarget): ImportResult = repository.importIcs(file, target)

    suspend fun exportIcs(calendarId: Long): Pair<String, String>? = repository.exportIcs(calendarId)

    suspend fun createBackup(options: org.nooklin.calendar.data.backup.BackupOptions, secret: String) = repository.createBackup(options, secret)

    suspend fun openBackup(bytes: ByteArray, secret: String) = repository.openBackup(bytes, secret)

    suspend fun restoreBackup(parsed: org.nooklin.calendar.data.backup.ParsedBackup) = repository.restoreBackup(parsed)

    // Set by a tap on a search result: the calendar screen opens this event
    var pendingOpen by mutableStateOf<Occurrence?>(null)

}
