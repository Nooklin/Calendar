package org.nooklin.calendar.ui.settings

import org.nooklin.calendar.ui.theme.AppColors
import org.nooklin.calendar.ui.theme.MinTouchTarget
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.nooklin.calendar.ui.common.GlassPill
import org.nooklin.calendar.ui.tour.tourTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "General",
                listOf(
                    { SettingsNavRow("Appearance", Icons.Filled.Palette, Tile.Purple) { onNavigate("appearance") } },
                    { SettingsNavRow("Calendar", Icons.Filled.CalendarMonth, Tile.Red) { onNavigate("calendar") } },
                    { SettingsNavRow("Dashboard", Icons.Filled.Dashboard, Tile.Green) { onNavigate("dashboard") } }
                )
            )
            SettingsGroup(
                "Calendars",
                listOf(
                    { Box(Modifier.tourTarget("settings_calendars")) { SettingsNavRow("Calendars", Icons.Filled.Event, Tile.Blue) { onNavigate("calendars") } } },
                    { SettingsNavRow("Import and export", Icons.Filled.Share, Tile.Green) { onNavigate("importexport") } }
                )
            )
            SettingsGroup(
                "Privacy and security",
                listOf({ SettingsNavRow("Privacy and Security", Icons.Filled.Lock, Tile.Red) { onNavigate("privacy") } })
            )
            SettingsGroup(
                "Help and about",
                listOf(
                    { SettingsNavRow("Tour", Icons.Filled.Explore, Tile.Orange) { onNavigate("tour") } },
                    { SettingsNavRow("Help and Support", Icons.AutoMirrored.Filled.Help, Tile.Blue) { onNavigate("help") } },
                    { SettingsNavRow("About", Icons.Filled.Info, Tile.Gray) { onNavigate("about") } }
                )
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// Items instead of a content lambda: a divider goes between items, never after the last
@Composable
fun SettingsGroup(title: String?, items: List<@Composable () -> Unit>) {
    if (title != null) SettingsHeading(title)
    Surface(
        color = GlassPill.adaptiveGlassBackground,
        shape = RoundedCornerShape(20.dp),
        border = GlassPill.adaptiveBorder,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        val edgePadding = if (items.size == 1) 4.dp else 8.dp
        Column(Modifier.padding(vertical = edgePadding)) {
            items.forEachIndexed { index, item ->
                // The row itself is the touch target, so a Switch or RadioButton adds no 48 dp
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) { item() }
                if (index != items.lastIndex) {
                    // start = 56dp aligns past the leading icon
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsNavRow(label: String, icon: ImageVector = Icons.Filled.MoreHoriz, tint: Color = Tile.Blue, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon, tint)
            Text(label)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}

/** A row that does something when tapped, without a chevron. */
@Composable
fun SettingsActionRow(label: String, icon: ImageVector, destructive: Boolean = false, tint: Color = Tile.Blue, onClick: () -> Unit) {
    val color = if (destructive) AppColors.DestructiveText else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIcon(icon, if (destructive) Tile.Red else tint)
        Text(label, color = color)
    }
}
