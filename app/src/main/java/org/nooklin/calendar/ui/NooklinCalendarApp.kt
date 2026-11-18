package org.nooklin.calendar.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import org.nooklin.calendar.ImportRequest
import org.nooklin.calendar.LaunchRequest
import org.nooklin.calendar.data.local.AppThemeMode
import org.nooklin.calendar.ui.calendar.CalendarScreen
import org.nooklin.calendar.ui.calendar.weekFieldsFor
import org.nooklin.calendar.ui.common.LocalBottomOverlay
import org.nooklin.calendar.ui.common.rememberClock
import org.nooklin.calendar.ui.editor.EditorTarget
import org.nooklin.calendar.ui.ics.ImportDialog
import org.nooklin.calendar.ui.search.SearchScreen
import org.nooklin.calendar.ui.settings.SettingsImportExportScreen
import org.nooklin.calendar.ui.editor.EventEditorScreen
import org.nooklin.calendar.ui.navigation.BottomBar
import org.nooklin.calendar.ui.navigation.ViewMode
import org.nooklin.calendar.ui.settings.SettingsAboutScreen
import org.nooklin.calendar.ui.settings.LocalColorful
import org.nooklin.calendar.ui.settings.SettingsAppearanceScreen
import org.nooklin.calendar.ui.settings.SettingsCalendarScreen
import org.nooklin.calendar.ui.settings.SettingsCalendarsScreen
import org.nooklin.calendar.ui.settings.SettingsChangelogScreen
import org.nooklin.calendar.ui.dashboard.DashboardScreen
import org.nooklin.calendar.ui.tour.TourActions
import org.nooklin.calendar.ui.tour.TourContext
import org.nooklin.calendar.ui.tour.TourOverlay
import org.nooklin.calendar.ui.tour.TourSignals
import org.nooklin.calendar.ui.settings.SettingsDashboardScreen
import org.nooklin.calendar.ui.settings.SettingsHelpScreen
import org.nooklin.calendar.ui.settings.SettingsPrivacyScreen
import org.nooklin.calendar.ui.settings.SettingsScreen
import org.nooklin.calendar.ui.theme.DesignStyle
import org.nooklin.calendar.ui.theme.NooklinTheme

private enum class Route { CALENDAR, SETTINGS, CALENDAR_SETTINGS, APPEARANCE, ABOUT, CHANGELOG, EDITOR, SEARCH, IMPORT_EXPORT, CALENDARS_SETTINGS, HELP, PRIVACY, DASHBOARD_SETTINGS }

private fun parentOf(route: Route): Route = when (route) {
    Route.CALENDAR, Route.EDITOR, Route.SEARCH -> Route.CALENDAR
    Route.SETTINGS -> Route.CALENDAR
    Route.CHANGELOG -> Route.ABOUT
    Route.CALENDAR_SETTINGS, Route.APPEARANCE, Route.ABOUT, Route.IMPORT_EXPORT, Route.CALENDARS_SETTINGS, Route.HELP, Route.PRIVACY, Route.DASHBOARD_SETTINGS -> Route.SETTINGS
}

@Composable
fun NooklinCalendarApp() {
    val viewModel: CalendarViewModel = viewModel()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val contrastColor by viewModel.contrastColor.collectAsStateWithLifecycle()
    val appFont by viewModel.appFont.collectAsStateWithLifecycle()
    val colorful by viewModel.colorful.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val showWeekNumbers by viewModel.showWeekNumbers.collectAsStateWithLifecycle()
    val dutchHolidays by viewModel.dutchHolidays.collectAsStateWithLifecycle()
    val askLocation by viewModel.askLocation.collectAsStateWithLifecycle()

    val clock = rememberClock()
    val weekFields = remember(weekStart) { weekFieldsFor(weekStart) }

    val systemIsDark = isSystemInDarkTheme()
    val resolvedDarkTheme = when (themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.BLACK -> true
        AppThemeMode.SYSTEM -> systemIsDark
    }

    // Status bar icons follow our theme, not the system one
    val context = LocalContext.current
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        // Dark icons for a light background, the inverse of resolvedDark
        insetsController.isAppearanceLightStatusBars = !resolvedDarkTheme
        insetsController.isAppearanceLightNavigationBars = !resolvedDarkTheme
    }

    var savedRoute by rememberSaveable { mutableStateOf(Route.CALENDAR) }
    // After the app was restarted by the system the editor has nothing to open: back to the calendar
    val editorTarget = viewModel.editorTarget
    val route = if (savedRoute == Route.EDITOR && editorTarget == null) Route.CALENDAR else savedRoute
    fun closeEditor() { viewModel.editorTarget = null; savedRoute = Route.CALENDAR }
    fun openEditor(target: EditorTarget) { viewModel.editorTarget = target; savedRoute = Route.EDITOR }
    var calendarsOpen by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(ViewMode.MONTH) }
    var dashboard by rememberSaveable { mutableStateOf(false) }
    val dashSettings by viewModel.dashboard.collectAsStateWithLifecycle()
    val tourDone by viewModel.tourDone.collectAsStateWithLifecycle()
    // Turning the dashboard off in the settings brings the calendar back
    LaunchedEffect(dashSettings.enabled) { if (!dashSettings.enabled) dashboard = false }
    var selectedEpochDay by rememberSaveable { mutableLongStateOf(clock.today.toEpochDay()) }
    val selectedDate = LocalDate.ofEpochDay(selectedEpochDay)

    // A tap on a widget: open that day
    val requestedDay = LaunchRequest.epochDay
    LaunchedEffect(requestedDay) {
        if (requestedDay != null) {
            selectedEpochDay = requestedDay
            mode = ViewMode.DAY
            dashboard = false
            savedRoute = Route.CALENDAR
            LaunchRequest.epochDay = null
        }
    }

    // A file to import, from a file manager, a share, or the settings
    val importUri = ImportRequest.uri
    if (importUri != null) {
        val calendars by viewModel.calendars.collectAsStateWithLifecycle()
        NooklinTheme(
            style = DesignStyle.IOS,
            darkTheme = resolvedDarkTheme,
            contrastColor = contrastColor,
            appFont = appFont,
            blackMode = themeMode == AppThemeMode.BLACK
        ) {
            ImportDialog(importUri, calendars.filter { it.sourceUrl == null }, viewModel) { ImportRequest.uri = null }
        }
    }

    BackHandler(enabled = route == Route.CALENDAR && dashboard) { dashboard = false }
    BackHandler(enabled = route != Route.CALENDAR) { if (route == Route.EDITOR) closeEditor() else savedRoute = parentOf(route) }

    NooklinTheme(
        style = DesignStyle.IOS,
        darkTheme = resolvedDarkTheme,
        contrastColor = contrastColor,
        appFont = appFont,
        blackMode = themeMode == AppThemeMode.BLACK
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // The screens fill the whole window; the bottom bar floats on top of the calendar screen
            var overlayHeight by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current
            val showBar = route == Route.CALENDAR
            CompositionLocalProvider(
                LocalBottomOverlay provides (if (showBar) overlayHeight else 0.dp),
                LocalColorful provides colorful
            ) {
                Box(Modifier.fillMaxSize().consumeWindowInsets(WindowInsets.navigationBars)) {
                    when (route) {
                        Route.CALENDAR -> if (dashboard) DashboardScreen(
                            clock = clock,
                            weekFields = weekFields,
                            viewModel = viewModel,
                            onOpenSettings = { savedRoute = Route.SETTINGS },
                            onSearch = { savedRoute = Route.SEARCH },
                            onOpen = { occurrence ->
                                selectedEpochDay = occurrence.startDate.toEpochDay()
                                mode = ViewMode.DAY
                                viewModel.pendingOpen = occurrence
                                dashboard = false
                            },
                            onNewEventAt = { start, end ->
                                openEditor(EditorTarget(null, false, start.toLocalDate(), start, end))
                            }
                        ) else CalendarScreen(
                            view = mode,
                            onSelectView = { mode = it },
                            selectedDate = selectedDate,
                            onSelectDate = { selectedEpochDay = it.toEpochDay() },
                            clock = clock,
                            weekFields = weekFields,
                            showWeekNumbers = showWeekNumbers,
                            viewModel = viewModel,
                            onOpenSettings = { savedRoute = Route.SETTINGS },
                            onSearch = { savedRoute = Route.SEARCH },
                            onOpenDay = { selectedEpochDay = it.toEpochDay(); mode = ViewMode.DAY },
                            calendarsOpen = calendarsOpen,
                            onCloseCalendars = { calendarsOpen = false },
                            onEdit = { openEditor(EditorTarget(it, false, selectedDate)) },
                            onDuplicate = { openEditor(EditorTarget(it, true, selectedDate)) },
                            onNewEvent = { openEditor(EditorTarget(null, false, selectedDate)) }
                        )
                        Route.SETTINGS -> SettingsScreen(
                            onBack = { savedRoute = parentOf(Route.SETTINGS) },
                            onNavigate = { target ->
                                savedRoute = when (target) {
                                    "calendar" -> Route.CALENDAR_SETTINGS
                                    "appearance" -> Route.APPEARANCE
                                    "importexport" -> Route.IMPORT_EXPORT
                                    "calendars" -> Route.CALENDARS_SETTINGS
                                    "help" -> Route.HELP
                                    "privacy" -> Route.PRIVACY
                                    "dashboard" -> Route.DASHBOARD_SETTINGS
                                    "tour" -> { viewModel.setTourDone(false); dashboard = false; Route.CALENDAR }
                                    "about" -> Route.ABOUT
                                    else -> Route.SETTINGS
                                }
                            }
                        )
                        Route.CALENDAR_SETTINGS -> SettingsCalendarScreen(
                            weekStart = weekStart,
                            onWeekStartChange = viewModel::setWeekStart,
                            showWeekNumbers = showWeekNumbers,
                            onShowWeekNumbersChange = viewModel::setShowWeekNumbers,
                            dutchHolidays = dutchHolidays,
                            onDutchHolidaysChange = viewModel::setDutchHolidays,
                            askLocation = askLocation,
                            onAskLocationChange = viewModel::setAskLocation,
                            onBack = { savedRoute = parentOf(Route.CALENDAR_SETTINGS) }
                        )
                        Route.APPEARANCE -> SettingsAppearanceScreen(
                            themeMode = themeMode,
                            onThemeModeChange = viewModel::setThemeMode,
                            contrastColor = contrastColor,
                            onContrastColorChange = viewModel::setContrastColor,
                            appFont = appFont,
                            onAppFontChange = viewModel::setAppFont,
                            colorful = colorful,
                            onColorfulChange = viewModel::setColorful,
                            onBack = { savedRoute = parentOf(Route.APPEARANCE) }
                        )
                        Route.CALENDARS_SETTINGS -> {
                            val calendars by viewModel.calendars.collectAsStateWithLifecycle()
                            val syncing by viewModel.syncing.collectAsStateWithLifecycle()
                            SettingsCalendarsScreen(
                                calendars = calendars,
                                syncing = syncing,
                                viewModel = viewModel,
                                onBack = { savedRoute = parentOf(Route.CALENDARS_SETTINGS) }
                            )
                        }
                        Route.EDITOR -> editorTarget?.let { editing ->
                            val calendars by viewModel.calendars.collectAsStateWithLifecycle()
                            EventEditorScreen(
                                target = editing,
                                clock = clock,
                                calendars = calendars.filter { it.sourceUrl == null },
                                onSave = { draft, scope ->
                                    viewModel.save(draft, if (editing.duplicate) null else editing.occurrence, scope)
                                    // Show the day of the event that was made or changed
                                    selectedEpochDay = draft.start.toLocalDate().toEpochDay()
                                    closeEditor()
                                },
                                onCancel = { closeEditor() },
                                onSuggest = { typed -> viewModel.suggestionFor(typed) },
                                askLocation = askLocation,
                                onStopAskingLocation = { viewModel.setAskLocation(false) }
                            )
                        }
                        Route.SEARCH -> SearchScreen(
                            viewModel = viewModel,
                            clock = clock,
                            onOpen = { occurrence ->
                                selectedEpochDay = occurrence.startDate.toEpochDay()
                                mode = ViewMode.DAY
                                viewModel.pendingOpen = occurrence
                                savedRoute = Route.CALENDAR
                            },
                            onBack = { savedRoute = Route.CALENDAR }
                        )
                        Route.IMPORT_EXPORT -> {
                            val calendars by viewModel.calendars.collectAsStateWithLifecycle()
                            SettingsImportExportScreen(calendars, viewModel) { savedRoute = parentOf(Route.IMPORT_EXPORT) }
                        }
                        Route.PRIVACY -> {
                            val appLock by viewModel.appLock.collectAsStateWithLifecycle()
                            val lockDelay by viewModel.lockDelay.collectAsStateWithLifecycle()
                            val allowScreenshots by viewModel.allowScreenshots.collectAsStateWithLifecycle()
                            val lockScreenNotifications by viewModel.lockScreenNotifications.collectAsStateWithLifecycle()
                            SettingsPrivacyScreen(
                                appLock = appLock,
                                onAppLockChange = viewModel::setAppLock,
                                lockDelay = lockDelay,
                                onLockDelayChange = viewModel::setLockDelay,
                                allowScreenshots = allowScreenshots,
                                onAllowScreenshotsChange = viewModel::setAllowScreenshots,
                                lockScreenNotifications = lockScreenNotifications,
                                onLockScreenNotificationsChange = viewModel::setLockScreenNotifications,
                                onBack = { savedRoute = parentOf(Route.PRIVACY) }
                            )
                        }
                        Route.DASHBOARD_SETTINGS -> {
                            val dashboardSettings by viewModel.dashboard.collectAsStateWithLifecycle()
                            val calendars by viewModel.calendars.collectAsStateWithLifecycle()
                            SettingsDashboardScreen(
                                settings = dashboardSettings,
                                calendars = calendars,
                                onChange = viewModel::setDashboard,
                                onBack = { savedRoute = parentOf(Route.DASHBOARD_SETTINGS) }
                            )
                        }
                        Route.HELP -> SettingsHelpScreen(onBack = { savedRoute = parentOf(Route.HELP) })
                        Route.ABOUT -> SettingsAboutScreen(
                            onOpenChangelog = { savedRoute = Route.CHANGELOG },
                            onBack = { savedRoute = parentOf(Route.ABOUT) }
                        )
                        Route.CHANGELOG -> SettingsChangelogScreen(onBack = { savedRoute = parentOf(Route.CHANGELOG) })
                    }
                }
            }
            if (showBar) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                ) {
                    BottomBar(
                        dashboard = dashboard,
                        dashboardAvailable = dashSettings.enabled,
                        onSelectDashboard = { dashboard = it },
                        onNewEvent = { openEditor(EditorTarget(null, false, selectedDate)) },
                        onToday = { selectedEpochDay = clock.today.toEpochDay() },
                        onCalendars = { calendarsOpen = true },
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            }
            // The tour of the first start, over everything
            if (tourDone == false) {
                TourOverlay(
                    context = TourContext(
                        viewMenuOpen = TourSignals.viewMenuOpen,
                        editorOpen = route == Route.EDITOR,
                        calendarsOpen = calendarsOpen,
                        dashboard = dashboard,
                        settingsOpen = route == Route.SETTINGS,
                        calendarsSettingsOpen = route == Route.CALENDARS_SETTINGS
                    ),
                    dashboardAvailable = dashSettings.enabled,
                    actions = TourActions(
                        closeEditor = { closeEditor() },
                        showCalendar = { viewModel.editorTarget = null; savedRoute = Route.CALENDAR; dashboard = false; calendarsOpen = false },
                        finish = {
                            viewModel.editorTarget = null
                            savedRoute = Route.CALENDAR
                            dashboard = false
                            calendarsOpen = false
                            viewModel.setTourDone(true)
                        }
                    )
                )
            }
        }
    }
}
