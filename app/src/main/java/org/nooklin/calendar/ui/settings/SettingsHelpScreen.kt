package org.nooklin.calendar.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private class HelpTopic(val title: String, val text: String)

private class HelpCategory(val key: String, val title: String, val icon: ImageVector, val tint: Color, val topics: List<HelpTopic>)

// Everything here describes what the app does today
private val CATEGORIES = listOf(
    HelpCategory(
        "around", "Getting around", Icons.Filled.Explore, Tile.Blue,
        listOf(
            HelpTopic("Choose a view", "Tap the view button at the top right: Day, Week, Month or Overview. A tick shows the view you are in. In the Month view the same menu also has the layout of the month."),
            HelpTopic("Go to today", "Tap Today in the bar at the bottom."),
            HelpTopic(
                "Month layouts",
                "In the Month view the view button at the top right also chooses the layout. Compact shows a dot for the events of a day, Stacked shows a bar per event, " +
                    "Details shows the titles, and List shows one whole month with the events of the selected day under it. Your choice is kept."
            ),
            HelpTopic(
                "Open a day",
                "In the Month view, tap a day to open it in the Day view. In the List layout a tap selects the day, and its events show under the month. Swipe left or right on the events to go to the next or the previous day. On the last or first day of a month the month changes as well."
            ),
            HelpTopic(
                "Search",
                "Tap the magnifier at the top right and type. A word has to be in the title, the location or the notes, and when you type more words, all of them have to be there. " +
                    "Tap a result to open the event. Only the calendars that are on show are searched."
            )
        )
    ),
    HelpCategory(
        "events", "Events", Icons.Filled.Event, Tile.Red,
        listOf(
            HelpTopic("Make an event", "Tap the plus in the middle of the bar at the bottom. Give it a title and choose the calendar, the start and the end. An all-day event has dates only. Tap Save when you are done."),
            HelpTopic("Background sync", "Followed calendars (.ics links and CalDAV) are refreshed without opening the app. Choose how often, or Wi-Fi only, in Settings > Calendars. Android may run it a bit later to save battery. Widgets are redrawn every 15 minutes."),
            HelpTopic("Travel time", "In a new event choose Travel time (5 minutes to 2 hours). The week and day view show a light block before the start, and the reminders go off that much earlier so you can leave on time. It shows in the lists of events too, as the line \"Travel 30 min\". All-day events have no travel time."),
            HelpTopic("See an event", "Tap an event in any view to see its details. Edit, Duplicate and Delete are at the bottom of the details."),
            HelpTopic("Open the location", "Tap the location in the details of an event to open it in a navigation app on your phone. When there is no such app, you see a message."),
            HelpTopic(
                "Repeating events",
                "When you change or delete an event of a series, the app asks which events: only this event, this and the following events, or all events."
            ),
            HelpTopic(
                "Events you cannot change",
                "An event in a calendar that follows a link is read only. Its details say so, and there are no Edit and Delete buttons. " +
                    "The same goes for a repeating CalDAV event that has days changed on their own: this app cannot send that back to the server."
            ),
            HelpTopic(
                "Fill in from an earlier event",
                "When you type the title of a new event and an earlier event has a very similar title, the app takes over its location, notes, length, all-day setting, calendar and reminders. " +
                    "It only fills what is still empty or untouched, and never the repeat. Tap Undo under the title to take it back. It happens once per new event."
            ),
            HelpTopic(
                "Date and time in the title",
                "Type something like \"Meeting with Eric at 2 PM on July 14\" (or in Dutch: \"om 14:00 op 14 juli\"). Above the keyboard a date and a time appear. " +
                    "Tap one to set it in the event; the words leave the title. Dates like tomorrow, Friday, in 2 weeks, July 14 and 14/7 work, and times like 2 PM, 14:30 and 14 uur."
            ),
            HelpTopic(
                "No location",
                "When you save an event without a location, the app asks if that is right and lets you fill one in. Tick Don't ask again to stop it; Settings > Calendar turns it back on."
            ),
            HelpTopic("Dutch holidays", "The Dutch public holidays show as all-day events in orange. They cannot be changed. Turn them off in Settings > Calendar."),
            HelpTopic("Time zones", "A timed event keeps the time zone it was made in. When that is not the zone of your phone, the details say so. A floating event always follows the clock of your phone.")
        )
    ),
    HelpCategory(
        "repeat", "Repeating", Icons.Filled.Repeat, Tile.Green,
        listOf(
            HelpTopic("Quick choices", "Under Repeat you find Every day, Every week, Every 2 weeks, Every month and Every year."),
            HelpTopic(
                "Custom",
                "Custom lets you set the Frequency (daily, weekly, monthly or yearly) and how often, for example every 3 weeks. " +
                    "A weekly repeat gets round buttons for the weekdays. A monthly repeat can fall on the day of the month, on the same weekday of the month (for example the second Tuesday), or on the last weekday."
            ),
            HelpTopic("When it stops", "End repeat has three choices: Never, On a date (the last day) and After a number of times."),
            HelpTopic("Short months", "A repeat on day 29, 30 or 31 skips the months that do not have that day.")
        )
    ),
    HelpCategory(
        "reminders", "Reminders", Icons.Filled.Notifications, Tile.Orange,
        listOf(
            HelpTopic(
                "Add a reminder",
                "In the editor, tap Add reminder. A timed event can remind you at the time of the event, 5, 10, 15 or 30 minutes before, 1 or 2 hours before, and 1 or 2 days before. " +
                    "An all-day event can remind you on the day itself, 1 day before, or 2 days before, always at 09:00. You can add more than one."
            ),
            HelpTopic("The notification", "A reminder is a notification with two buttons: Snooze 10 min and Dismiss."),
            HelpTopic(
                "Notifications are off",
                "Without permission for notifications a reminder cannot show. The editor then offers Allow notifications and a shortcut to the notification settings of the app."
            ),
            HelpTopic("After a restart", "Reminders are set again when the phone restarts, and when the time or the time zone of the phone changes.")
        )
    ),
    HelpCategory(
        "calendars", "Calendars", Icons.Filled.CalendarMonth, Tile.Purple,
        listOf(
            HelpTopic("Show and hide", "Tap the calendar button in the bar at the bottom, on the right. Tap a calendar there to show or hide its events."),
            HelpTopic(
                "Add a calendar",
                "Go to Settings > Calendars. New local calendar makes a calendar on this phone that you change yourself. " +
                    "Import an .ics file copies the events of the file into a local calendar. Subscribe to an .ics link follows a web address. Connect a CalDAV account adds the calendars of a CalDAV server."
            ),
            HelpTopic(
                "Calendars that follow a link",
                "Use a link that starts with https:// or webcal://. The calendar is read only. It is downloaded again when the app starts, at most once an hour, and when you tap its refresh button. " +
                    "Each one shows if it works: Synced, Syncing, Not synced yet, or Not working with the reason."
            ),
            HelpTopic(
                "CalDAV accounts",
                "Go to Settings > Calendars > Connect a CalDAV account, pick the kind of server (Nextcloud, ownCloud, Radicale, Baikal, Synology, Fastmail, iCloud, Posteo or Other) and fill in the address, your username and your password. The text under the kinds says what each one wants. " +
                    "All calendars with events on the account are added. Changes go both ways: what you add, change or delete here is sent to the server, and what changed on the server is downloaded. " +
                    "A CalDAV calendar is synced when the app starts, after each change and with its refresh button. When you are offline, changes wait and are sent at the next sync. " +
                    "If you change the same event here and on the server, the change made here wins. " +
                    "The password is stored encrypted on this phone. To use a new password, connect the account again with the same address and username."
            ),
            HelpTopic(
                "Change or delete",
                "Tap a calendar in Settings > Calendars to change its name or colour, or to delete it. The last local calendar cannot be deleted. " +
                    "Deleting a CalDAV calendar only removes it from this phone, the calendar on the server stays."
            )
        )
    ),
    HelpCategory(
        "files", "Import and export", Icons.Filled.Share, Tile.Green,
        listOf(
            HelpTopic(
                "Import",
                "Go to Settings > Import and export and choose Import an .ics file. You can also open an .ics file from a file manager, or share it to this app. " +
                    "An event that is already in the calendar (the same uid) is replaced."
            ),
            HelpTopic("Export", "Under Export a calendar as .ics, choose a calendar. The file is written where you choose.")
        )
    ),
    HelpCategory(
        "widgets", "Widgets and Quick add", Icons.Filled.Widgets, Tile.Orange,
        listOf(
            HelpTopic(
                "Widgets",
                "Add a widget from the widget list of your home screen. There are nine: Calendar today, week, month and date, " +
                    "up next (small and wide), day planner, today and tomorrow, and list. Tapping a widget opens the app on that day."
            ),
            HelpTopic(
                "Up next, day planner and list",
                "Up next shows the date and your next events. The day planner shows the hours of today and tomorrow with your events on them. The list shows your upcoming events, day by day."
            ),
            HelpTopic(
                "Quick add",
                "Add the Quick add tile to the quick settings of your phone. It opens a small window: type a title, choose Today or Tomorrow, and save. " +
                    "The event starts at the next full hour today, or at 09:00 tomorrow, and lasts an hour."
            ),
            HelpTopic("Widgets and the font", "Widgets always use the Inter font, whatever font you choose in Appearance.")
        )
    ),
    HelpCategory(
        "dashboard", "Dashboard", Icons.Filled.Dashboard, Tile.Green,
        listOf(
            HelpTopic(
                "Switching",
                "At the bottom left are two buttons, Dashboard and Calendar. Tap one to switch. The Back button of the phone goes from the Dashboard to the Calendar."
            ),
            HelpTopic(
                "The cards",
                "Tonight, Free evenings, Up next, Today in numbers, Free blocks today, Week load, Weekend, Overlaps, Find time, All-day coming up, Reminders today and Sync. " +
                    "Tap an event to open it. Tap the place of the next event to open a navigation app."
            ),
            HelpTopic("Find time", "Choose 30 minutes, 1 hour or 2 hours. Tap a free time to make an event that starts then."),
            HelpTopic(
                "Settings",
                "Settings > Dashboard turns the dashboard or single cards on or off, changes their order by dragging, and sets evening, daytime and which calendars count."
            ),
            HelpTopic("Tour", "Settings > Tour shows the tour of the first start again.")
        )
    ),
    HelpCategory(
        "settings", "Settings", Icons.Filled.Settings, Tile.Gray,
        listOf(
            HelpTopic("Appearance", "Choose the theme (Follow System, Light, Dark or Black), the colourful settings icons, the font, and the contrast colour of buttons and accents."),
            HelpTopic(
                "Calendar",
                "Choose the day the week starts on (Follow System, Monday or Sunday) and whether week numbers show in the Month and Week views."
            ),
            HelpTopic(
                "App lock",
                "Go to Settings > Privacy and Security and turn on Lock the app. You unlock with your fingerprint, face or the screen lock of the phone, and choose how long the app may be out of sight before it asks again. " +
                    "Turning the lock on or off also asks you to unlock. Widgets and reminders are not locked."
            ),
            HelpTopic(
                "Screenshots and recent apps",
                "Turn off Allow screenshots to block screenshots and screen recordings of the app. The recent apps overview then shows no picture of the app. It also shows none while the app lock is on."
            ),
            HelpTopic(
                "Reminders on the lock screen",
                "By default the lock screen only says there is a reminder, and the title shows after you unlock. Turn on Show on the lock screen in Privacy and Security to see the title there too. " +
                    "The setting of your phone for notifications on the lock screen also applies."
            )
        )
    )
)

/** The help page: categories that open and close, each with short topics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help and Support", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsNote("Tap a title to open or close it.")
            CATEGORIES.forEach { category -> HelpCategoryGroup(category) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HelpCategoryGroup(category: HelpCategory) {
    var open by rememberSaveable(category.key) { mutableStateOf(false) }
    val items = buildList<@Composable () -> Unit> {
        add {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable(onClickLabel = if (open) "Close" else "Open") { open = !open }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsIcon(category.icon, category.tint)
                    Text(category.title)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
        }
        if (open) {
            category.topics.forEach { topic ->
                add {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(topic.title, fontWeight = FontWeight.Bold)
                        Text(
                            topic.text,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
    SettingsGroup(null, items)
}
