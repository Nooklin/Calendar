package org.nooklin.calendar.data.local

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.nooklin.calendar.data.dashboard.DashboardSettings

private val Context.appSettingsDataStore by preferencesDataStore(name = "nooklin_calendar_settings")

enum class AppThemeMode { LIGHT, DARK, BLACK, SYSTEM }

// DEFAULT has no tint (null)
enum class ContrastColor(val storageKey: String, val label: String, val color: Color?) {
    DEFAULT("default", "Default", null),
    BLUE("blue", "Blue", Color(0xFF0A84FF)),
    RED("red", "Red", Color(0xFFFF3B30)),
    ORANGE("orange", "Orange", Color(0xFFFF9500)),
    YELLOW("yellow", "Yellow", Color(0xFFFFCC00)),
    GREEN("green", "Green", Color(0xFF34C759)),
    TEAL("teal", "Teal", Color(0xFF30B0C7)),
    PURPLE("purple", "Purple", Color(0xFFAF52DE)),
    PINK("pink", "Pink", Color(0xFFFF2D55));

    companion object {
        fun fromKey(key: String): ContrastColor? = entries.find { it.storageKey == key }
    }
}

// Two fonts only: Helvetica has no free license to bundle
enum class AppFont(val storageKey: String, val label: String) {
    SYSTEM_DEFAULT("system_default", "Stock Android Font"),
    WORK_SANS("work_sans", "Work Sans"),
    INTER("inter", "Inter");

    companion object {
        fun fromKey(key: String): AppFont? = entries.find { it.storageKey == key }
    }
}

// SYSTEM follows the language and region of the phone (Netherlands: Monday, United States: Sunday)
enum class WeekStart(val storageKey: String, val label: String) {
    SYSTEM("system", "Follow System"),
    MONDAY("monday", "Monday"),
    SUNDAY("sunday", "Sunday");

    companion object {
        fun fromKey(key: String): WeekStart? = entries.find { it.storageKey == key }
    }
}

// How much of a day the month view shows
enum class MonthDensity(val storageKey: String, val label: String) {
    COMPACT("compact", "Compact"),
    STACKED("stacked", "Stacked"),
    DETAILS("details", "Details"),
    LIST("list", "List");

    companion object {
        fun fromKey(key: String): MonthDensity? = entries.find { it.storageKey == key }
    }
}

// How long the app may be out of sight before the app lock asks again
enum class LockDelay(val storageKey: String, val label: String, val seconds: Int) {
    IMMEDIATELY("0", "Immediately", 0),
    ONE_MINUTE("60", "After 1 minute", 60),
    FIVE_MINUTES("300", "After 5 minutes", 300),
    FIFTEEN_MINUTES("900", "After 15 minutes", 900);

    companion object {
        fun fromKey(key: String): LockDelay? = entries.find { it.storageKey == key }
    }
}

private val APP_LOCK_KEY = booleanPreferencesKey("app_lock")
private val LOCK_DELAY_KEY = stringPreferencesKey("lock_delay")
private val SCREENSHOTS_KEY = booleanPreferencesKey("allow_screenshots")
private val LOCK_SCREEN_NOTIFICATIONS_KEY = booleanPreferencesKey("lock_screen_notifications")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
private val CONTRAST_COLOR_KEY = stringPreferencesKey("contrast_color")
private val APP_FONT_KEY = stringPreferencesKey("app_font")
private val WEEK_START_KEY = stringPreferencesKey("week_start")
private val WEEK_NUMBERS_KEY = booleanPreferencesKey("week_numbers")
private val COLORFUL_KEY = booleanPreferencesKey("colorful")
private val MONTH_DENSITY_KEY = stringPreferencesKey("month_density")
private val DASH_ENABLED_KEY = booleanPreferencesKey("dash_enabled")
private val TOUR_DONE_KEY = booleanPreferencesKey("tour_done")
private val DUTCH_HOLIDAYS_KEY = booleanPreferencesKey("dutch_holidays")
private val BG_SYNC_KEY = booleanPreferencesKey("bg_sync")
private val BG_SYNC_MINUTES_KEY = intPreferencesKey("bg_sync_minutes")
private val BG_SYNC_WIFI_KEY = booleanPreferencesKey("bg_sync_wifi")
private val ASK_LOCATION_KEY = booleanPreferencesKey("ask_location")
private val HAPTICS_KEY = booleanPreferencesKey("haptics_enabled")
private val DASH_ORDER_KEY = stringPreferencesKey("dash_order")
private val DASH_DISABLED_KEY = stringPreferencesKey("dash_disabled")
private val DASH_EXTENDED_KEY = stringPreferencesKey("dash_extended")
private val DASH_CALENDARS_KEY = stringPreferencesKey("dash_calendars")
private val DASH_EVENING_START_KEY = intPreferencesKey("dash_evening_start")
private val DASH_EVENING_END_KEY = intPreferencesKey("dash_evening_end")
private val DASH_DAY_START_KEY = intPreferencesKey("dash_day_start")
private val DASH_DAY_END_KEY = intPreferencesKey("dash_day_end")
private val DASH_ALL_DAY_BUSY_KEY = booleanPreferencesKey("dash_all_day_busy")
private val DASH_MIN_FREE_KEY = intPreferencesKey("dash_min_free")
private val DASH_LOOK_AHEAD_KEY = intPreferencesKey("dash_look_ahead")
private val DASH_ALL_DAY_AHEAD_KEY = intPreferencesKey("dash_all_day_ahead")

class AppPreferences(private val context: Context) {

    val themeMode: Flow<AppThemeMode> = context.appSettingsDataStore.data.map { prefs ->
        when (prefs[THEME_MODE_KEY]) {
            "light" -> AppThemeMode.LIGHT
            "dark" -> AppThemeMode.DARK
            "black" -> AppThemeMode.BLACK
            else -> AppThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.appSettingsDataStore.edit { prefs -> prefs[THEME_MODE_KEY] = mode.name.lowercase() }
    }

    val contrastColor: Flow<ContrastColor> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CONTRAST_COLOR_KEY]?.let { ContrastColor.fromKey(it) } ?: ContrastColor.DEFAULT
    }

    suspend fun setContrastColor(color: ContrastColor) {
        context.appSettingsDataStore.edit { prefs -> prefs[CONTRAST_COLOR_KEY] = color.storageKey }
    }

    val appFont: Flow<AppFont> = context.appSettingsDataStore.data.map { prefs ->
        prefs[APP_FONT_KEY]?.let { AppFont.fromKey(it) } ?: AppFont.INTER
    }

    suspend fun setAppFont(font: AppFont) {
        context.appSettingsDataStore.edit { prefs -> prefs[APP_FONT_KEY] = font.storageKey }
    }

    val weekStart: Flow<WeekStart> = context.appSettingsDataStore.data.map { prefs ->
        prefs[WEEK_START_KEY]?.let { WeekStart.fromKey(it) } ?: WeekStart.SYSTEM
    }

    suspend fun setWeekStart(value: WeekStart) {
        context.appSettingsDataStore.edit { prefs -> prefs[WEEK_START_KEY] = value.storageKey }
    }

    val showWeekNumbers: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[WEEK_NUMBERS_KEY] ?: false
    }

    suspend fun setShowWeekNumbers(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[WEEK_NUMBERS_KEY] = value }
    }

    val monthDensity: Flow<MonthDensity> = context.appSettingsDataStore.data.map { prefs ->
        prefs[MONTH_DENSITY_KEY]?.let { MonthDensity.fromKey(it) } ?: MonthDensity.COMPACT
    }

    suspend fun setMonthDensity(value: MonthDensity) {
        context.appSettingsDataStore.edit { prefs -> prefs[MONTH_DENSITY_KEY] = value.storageKey }
    }

    val appLock: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[APP_LOCK_KEY] ?: false }

    suspend fun setAppLock(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[APP_LOCK_KEY] = value }
    }

    val lockDelay: Flow<LockDelay> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LOCK_DELAY_KEY]?.let { LockDelay.fromKey(it) } ?: LockDelay.ONE_MINUTE
    }

    suspend fun setLockDelay(value: LockDelay) {
        context.appSettingsDataStore.edit { prefs -> prefs[LOCK_DELAY_KEY] = value.storageKey }
    }

    val allowScreenshots: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[SCREENSHOTS_KEY] ?: true }

    suspend fun setAllowScreenshots(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[SCREENSHOTS_KEY] = value }
    }

    val lockScreenNotifications: Flow<Boolean> =
        context.appSettingsDataStore.data.map { prefs -> prefs[LOCK_SCREEN_NOTIFICATIONS_KEY] ?: false }

    suspend fun setLockScreenNotifications(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[LOCK_SCREEN_NOTIFICATIONS_KEY] = value }
    }

    val colorful: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[COLORFUL_KEY] ?: true
    }

    suspend fun setColorful(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[COLORFUL_KEY] = value }
    }

    val dashboard: Flow<DashboardSettings> = context.appSettingsDataStore.data.map { prefs ->
        val defaults = DashboardSettings()
        DashboardSettings(
            enabled = prefs[DASH_ENABLED_KEY] ?: defaults.enabled,
            order = DashboardSettings.orderFrom(prefs[DASH_ORDER_KEY]),
            disabled = DashboardSettings.setFrom(prefs[DASH_DISABLED_KEY]),
            extended = DashboardSettings.setFrom(prefs[DASH_EXTENDED_KEY]),
            eveningStart = prefs[DASH_EVENING_START_KEY] ?: defaults.eveningStart,
            eveningEnd = prefs[DASH_EVENING_END_KEY] ?: defaults.eveningEnd,
            dayStart = prefs[DASH_DAY_START_KEY] ?: defaults.dayStart,
            dayEnd = prefs[DASH_DAY_END_KEY] ?: defaults.dayEnd,
            allDayBusy = prefs[DASH_ALL_DAY_BUSY_KEY] ?: defaults.allDayBusy,
            // Not stored: all visible calendars count
            calendarIds = prefs[DASH_CALENDARS_KEY]?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet(),
            minFree = prefs[DASH_MIN_FREE_KEY] ?: defaults.minFree,
            lookAhead = prefs[DASH_LOOK_AHEAD_KEY] ?: defaults.lookAhead,
            allDayAhead = prefs[DASH_ALL_DAY_AHEAD_KEY] ?: defaults.allDayAhead
        )
    }

    suspend fun setDashboard(value: DashboardSettings) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[DASH_ENABLED_KEY] = value.enabled
            prefs[DASH_ORDER_KEY] = value.order.joinToString(",") { it.key }
            prefs[DASH_DISABLED_KEY] = value.disabled.joinToString(",") { it.key }
            prefs[DASH_EXTENDED_KEY] = value.extended.joinToString(",") { it.key }
            val ids = value.calendarIds
            if (ids == null) prefs.remove(DASH_CALENDARS_KEY) else prefs[DASH_CALENDARS_KEY] = ids.joinToString(",")
            prefs[DASH_EVENING_START_KEY] = value.eveningStart
            prefs[DASH_EVENING_END_KEY] = value.eveningEnd
            prefs[DASH_DAY_START_KEY] = value.dayStart
            prefs[DASH_DAY_END_KEY] = value.dayEnd
            prefs[DASH_ALL_DAY_BUSY_KEY] = value.allDayBusy
            prefs[DASH_MIN_FREE_KEY] = value.minFree
            prefs[DASH_LOOK_AHEAD_KEY] = value.lookAhead
            prefs[DASH_ALL_DAY_AHEAD_KEY] = value.allDayAhead
        }
    }

    // The tour that shows the first time the app opens
    val tourDone: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[TOUR_DONE_KEY] ?: false }

    suspend fun setTourDone(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[TOUR_DONE_KEY] = value }
    }

    // The Dutch public holidays as a calendar, on by default
    val dutchHolidays: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[DUTCH_HOLIDAYS_KEY] ?: true }

    suspend fun setDutchHolidays(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[DUTCH_HOLIDAYS_KEY] = value }
    }

    // Whether saving an event without a location asks if that is right, on by default
    val askLocation: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[ASK_LOCATION_KEY] ?: true }

    suspend fun setAskLocation(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[ASK_LOCATION_KEY] = value }
    }

    // Vibration feedback on the wheels, the view menu and the switches, on by default
    val hapticsEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[HAPTICS_KEY] ?: true }

    suspend fun setHapticsEnabled(on: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[HAPTICS_KEY] = on }
    }

    // Refreshing the followed calendars in the background: on by default, every hour, on any network
    val backgroundSync: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[BG_SYNC_KEY] ?: true }

    suspend fun setBackgroundSync(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[BG_SYNC_KEY] = value }
    }

    val backgroundSyncMinutes: Flow<Int> = context.appSettingsDataStore.data.map { prefs -> prefs[BG_SYNC_MINUTES_KEY] ?: 60 }

    suspend fun setBackgroundSyncMinutes(value: Int) {
        context.appSettingsDataStore.edit { prefs -> prefs[BG_SYNC_MINUTES_KEY] = value }
    }

    val backgroundSyncWifiOnly: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs -> prefs[BG_SYNC_WIFI_KEY] ?: false }

    suspend fun setBackgroundSyncWifiOnly(value: Boolean) {
        context.appSettingsDataStore.edit { prefs -> prefs[BG_SYNC_WIFI_KEY] = value }
    }
}
