package org.nooklin.calendar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private class ChangelogVersion(val version: String, val lines: List<String>)

private val CHANGELOG = listOf(
    ChangelogVersion("1.0.2", listOf(
        "Backup and restore in Settings > Import and export: one encrypted file with your calendars, events, CalDAV accounts, link calendars and settings.",
        "You choose what goes in and protect the file with a password or a 6-digit PIN.",
        "A backup from another version is restored as far as possible, with a list of what could not be taken over."
    )),
    ChangelogVersion("1.0.1", listOf(
        "App icon update: a new launcher icon in the Nooklin colors, also as a single color icon for themed icons."
    )),
    ChangelogVersion("1.0.0", listOf(
        "The settings look like those of Nooklin Photos: a heading above every group and one short line of explanation under it.",
        "Week starts on, Theme, Font, Lock again and How often are a row with the choice on the right.",
        "The About screen has a Changelog, GitHub, Privacy and Terms, and the Developer screen is removed.",
        "A calmer dark glass in black mode, and shared design values with the other Nooklin apps.",
        "A list with no events shows a short text and a New event button.",
        "Everything you tap is at least 48 dp.",
        "Vibration feedback on the time wheels, in the view menu and on switches, with a switch in Settings > Appearance."
    )),
    ChangelogVersion("0.0.43", listOf(
        "Followed calendars and CalDAV are refreshed in the background, on or off, from every 15 minutes to every day, optionally only on Wi-Fi.",
        "Widgets are redrawn every 15 minutes."
    )),
    ChangelogVersion("0.0.41", listOf(
        "Every widget has a preview with example events in the list where you choose a widget."
    )),
    ChangelogVersion("0.0.38", listOf(
        "Travel time for an event, from 5 minutes to 2 hours: a light block in the week and day view, a line in the details, and reminders that go off that much earlier.",
        "The minute wheel carries over to the hour."
    )),
    ChangelogVersion("0.0.37", listOf(
        "The time of an event is picked with wheels, with buttons for the length of the event.",
        "The date picker uses your accent colour, and the calendar menu of a new event shows the colour of each calendar."
    )),
    ChangelogVersion("0.0.36", listOf(
        "A date or time typed in the title shows as a chip above the keyboard (English and Dutch).",
        "A question when you save an event without a location.",
        "Dutch holidays, which you can turn off in Settings > Calendar.",
        "The Year view is removed."
    )),
    ChangelogVersion("0.0.27 to 0.0.35", listOf(
        "A Dashboard with twelve cards and its own settings.",
        "A tour that takes you through the app.",
        "A bottom bar in three pills and one View menu.",
        "A new widget with the events of today and tomorrow."
    )),
    ChangelogVersion("0.0.3 to 0.0.26", listOf(
        "Month, week, day and overview views, with four layouts for the month.",
        "Widgets, a Quick add tile, search, and .ics import and export.",
        "Repeating events, reminders, and calendars you follow by a link.",
        "CalDAV accounts, an app lock and privacy settings."
    ))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsChangelogScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Changelog", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            CHANGELOG.forEach { entry ->
                SettingsGroup(
                    entry.version,
                    entry.lines.map { line -> { Text(line, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) } }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
