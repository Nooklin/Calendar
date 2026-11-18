package org.nooklin.calendar.ui.navigation

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewDay
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.calendar.data.local.MonthDensity
import org.nooklin.calendar.ui.common.GlassPill
import org.nooklin.calendar.ui.common.NooklinMenu
import org.nooklin.calendar.ui.tour.TourSignals
import org.nooklin.calendar.ui.tour.tourTarget

/** The ways to look at the calendar. */
enum class ViewMode(val label: String, val icon: ImageVector) {
    DAY("Day", Icons.Rounded.CalendarViewDay),
    WEEK("Week", Icons.Rounded.CalendarViewWeek),
    MONTH("Month", Icons.Rounded.CalendarViewMonth),
    OVERVIEW("Overview", Icons.Rounded.ViewAgenda)
}

fun densityIcon(density: MonthDensity): ImageVector = when (density) {
    MonthDensity.COMPACT -> Icons.Rounded.ViewHeadline
    MonthDensity.STACKED -> Icons.Rounded.ViewAgenda
    MonthDensity.DETAILS -> Icons.Rounded.ViewStream
    MonthDensity.LIST -> Icons.Rounded.ViewList
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

/**
 * One menu in the top bar for everything about how the calendar looks: the view (Day, Week, Month, Year, Overview)
 * and, in the Month view, the layout of the month. The icon is the view that is open.
 */
@Composable
fun ViewMenu(
    mode: ViewMode,
    onSelectMode: (ViewMode) -> Unit,
    density: MonthDensity,
    onSelectDensity: (MonthDensity) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    // The tour waits for this menu to open and to close
    DisposableEffect(menuOpen) {
        TourSignals.viewMenuOpen = menuOpen
        onDispose { TourSignals.viewMenuOpen = false }
    }
    val haptic = LocalHapticFeedback.current
    Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.tourTarget("view")) {
            Icon(mode.icon, contentDescription = "View: ${mode.label}")
        }
        NooklinMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            MenuHeader("View")
            ViewMode.entries.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    leadingIcon = { Icon(entry.icon, contentDescription = null) },
                    trailingIcon = { if (entry == mode) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                    onClick = { haptic.performHapticFeedback(HapticFeedbackType.ToggleOn); menuOpen = false; onSelectMode(entry) }
                )
            }
            if (mode == ViewMode.MONTH) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                MenuHeader("Month layout")
                MonthDensity.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.label) },
                        leadingIcon = { Icon(densityIcon(entry), contentDescription = null) },
                        trailingIcon = { if (entry == density) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                        onClick = { menuOpen = false; onSelectDensity(entry) }
                    )
                }
            }
        }
    }
}

/**
 * The floating bar at the bottom, in three glass pills: on the left the switch between the Dashboard and the
 * Calendar, in the middle the plus for a new event, on the right Today and the calendars. The right pill is only
 * there in the Calendar, so the other two stay where they are.
 */
@Composable
fun BottomBar(
    dashboard: Boolean,
    // False when the dashboard is turned off in the settings: the switch is not shown
    dashboardAvailable: Boolean,
    onSelectDashboard: (Boolean) -> Unit,
    onNewEvent: () -> Unit,
    onToday: () -> Unit,
    onCalendars: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
        if (dashboardAvailable) {
            Row(
                Modifier.align(Alignment.CenterStart).barPill().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Segment(Icons.Rounded.Dashboard, "Dashboard", dashboard, Modifier.tourTarget("dashboard")) { onSelectDashboard(true) }
                Segment(Icons.Rounded.CalendarMonth, "Calendar", !dashboard) { onSelectDashboard(false) }
            }
        }
        Box(
            Modifier.align(Alignment.Center).tourTarget("add").barPill().clickable(onClickLabel = "New event", onClick = onNewEvent).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Add, contentDescription = "New event", tint = MaterialTheme.colorScheme.onSurface)
        }
        if (!dashboard) {
            Row(
                Modifier.align(Alignment.CenterEnd).barPill().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.height(48.dp).clip(CircleShape).clickable(onClickLabel = "Go to today", onClick = onToday).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Today", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    Modifier.tourTarget("calendars").height(48.dp).clip(CircleShape).clickable(onClickLabel = "Calendars", onClick = onCalendars).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Event, contentDescription = "Calendars", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Modifier.barPill(): Modifier = this
    .height(56.dp)
    .clip(CircleShape)
    .background(GlassPill.adaptiveFloatingBackground)
    .border(GlassPill.adaptiveBorder, CircleShape)

@Composable
private fun Segment(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(width = 48.dp, height = 48.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClickLabel = "Show $label", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
    }
}
