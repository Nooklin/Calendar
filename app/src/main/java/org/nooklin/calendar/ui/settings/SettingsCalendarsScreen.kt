package org.nooklin.calendar.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.calendar.ImportRequest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.nooklin.calendar.sync.BackgroundSync
import java.net.URI
import org.nooklin.calendar.data.LinkSecret
import org.nooklin.calendar.data.caldav.CaldavService
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.calendar.CALENDAR_COLORS
import org.nooklin.calendar.ui.calendar.CalendarDialog

private val GoodGreen = Color(0xFF30D158)

/**
 * Where calendars are added and managed: new local calendars, .ics files and .ics links. A calendar that follows a link
 * shows whether it works: syncing, the time of the last good refresh, or why it does not work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsCalendarsScreen(
    calendars: List<CalendarEntity>,
    syncing: Set<Long>,
    viewModel: CalendarViewModel,
    onBack: () -> Unit
) {
    var addingLocal by remember { mutableStateOf(false) }
    var addingLink by remember { mutableStateOf(false) }
    var addingCaldav by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEntity?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ImportRequest.uri = uri
    }
    val local = calendars.filter { it.sourceUrl == null && it.accountId == null }
    val linked = calendars.filter { it.sourceUrl != null }
    val dav = calendars.filter { it.accountId != null }
    val accounts by viewModel.caldavAccounts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendars", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "Add a calendar",
                listOf(
                    { SettingsActionRow("New local calendar", Icons.Filled.Add, tint = Tile.Blue) { addingLocal = true } },
                    { SettingsActionRow("Import an .ics file", Icons.Filled.Description, tint = Tile.Green) { picker.launch(arrayOf("*/*")) } },
                    { SettingsActionRow("Subscribe to an .ics link", Icons.Filled.Link, tint = Tile.Orange) { addingLink = true } },
                    { SettingsActionRow("Connect a CalDAV account", Icons.Filled.Cloud, tint = Tile.Purple) { addingCaldav = true } }
                )
            )
            SettingsNote("What each choice does is explained under Help and Support.")
            if (local.isNotEmpty()) {
                SettingsGroup(
                    "On this phone",
                    local.map { calendar ->
                        { CalendarRow(calendar, syncing = false, onClick = { editing = calendar }, onRefresh = {}) }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            if (dav.isNotEmpty()) {
                // One group per account, named after the service and the server it comes from
                dav.groupBy { it.accountId }.forEach { (accountId, ofAccount) ->
                    val account = accounts.firstOrNull { it.id == accountId }
                    val host = account?.let { runCatching { URI(it.serverUrl).host }.getOrNull() }
                    val title = if (account == null) "CalDAV" else CaldavService.fromKey(account.service).label + (host?.let { " ($it)" } ?: "")
                    SettingsGroup(
                        title,
                        ofAccount.map { calendar ->
                            {
                                CalendarRow(
                                    calendar,
                                    syncing = calendar.id in syncing,
                                    onClick = { editing = calendar },
                                    onRefresh = { viewModel.refresh(calendar.id) }
                                )
                            }
                        }
                    )
                }
                SettingsNote("A CalDAV calendar is synced when the app starts, after each change and in the background.")
                Spacer(Modifier.height(8.dp))
            }
            if (linked.isNotEmpty()) {
                SettingsGroup(
                    "Followed by link",
                    linked.map { calendar ->
                        {
                            CalendarRow(
                                calendar,
                                syncing = calendar.id in syncing,
                                onClick = { editing = calendar },
                                onRefresh = { viewModel.refresh(calendar.id) }
                            )
                        }
                    }
                )
                SettingsNote("Tap a calendar to change its name or colour, or to delete it.")
            }
            val syncOn by viewModel.backgroundSync.collectAsStateWithLifecycle()
            val syncMinutes by viewModel.backgroundSyncMinutes.collectAsStateWithLifecycle()
            val syncWifiOnly by viewModel.backgroundSyncWifiOnly.collectAsStateWithLifecycle()
            SettingsGroup(
                "Background sync",
                buildList<@Composable () -> Unit> {
                    add { SettingsToggleRow("Sync in the background", syncOn, Icons.Filled.Refresh, Tile.Green, onCheckedChange = viewModel::setBackgroundSync) }
                    add { SettingsToggleRow("Only on Wi-Fi", syncWifiOnly, Icons.Filled.Cloud, Tile.Blue, enabled = syncOn, onCheckedChange = viewModel::setBackgroundSyncWifiOnly) }
                    if (syncOn) add {
                        SettingsChoiceRow(
                            "How often",
                            BackgroundSync.INTERVALS.map { it to BackgroundSync.label(it) },
                            syncMinutes,
                            onSelect = viewModel::setBackgroundSyncMinutes
                        )
                    }
                }
            )
            SettingsNote("Refreshes followed calendars without opening the app, Android may run it a bit later.")
            Spacer(Modifier.height(16.dp))
        }
    }

    if (addingLocal) {
        CalendarDialog(
            initial = null,
            defaultColor = CALENDAR_COLORS[calendars.size % CALENDAR_COLORS.size],
            canDelete = false,
            onSave = { name, color -> addingLocal = false; viewModel.addCalendar(name, color) },
            onDelete = {},
            onDismiss = { addingLocal = false }
        )
    }
    if (addingLink) {
        SubscribeDialog(
            defaultColor = CALENDAR_COLORS[calendars.size % CALENDAR_COLORS.size],
            onSubscribe = { url, name, color -> viewModel.subscribe(name, color, url) },
            onDismiss = { addingLink = false }
        )
    }
    if (addingCaldav) {
        CaldavDialog(
            onConnect = { server, username, password, service -> viewModel.connectCaldav(server, username, password, service) },
            onDismiss = { addingCaldav = false }
        )
    }
    val current = editing
    if (current != null) {
        CalendarDialog(
            initial = current,
            defaultColor = current.color,
            canDelete = local.size > 1 || current.sourceUrl != null || current.accountId != null,
            onSave = { name, color -> editing = null; viewModel.updateCalendar(current.id, name, color) },
            onDelete = { editing = null; viewModel.deleteCalendar(current.id) },
            onDismiss = { editing = null },
            info = current.sourceUrl?.let { stored ->
                "Follows a link on " + (LinkSecret.hostOf(stored) ?: "a server that cannot be read now") +
                    ". The full address is stored encrypted and is not shown."
            } ?: if (current.accountId != null) {
                "A CalDAV calendar. Deleting it only removes it from this phone, the calendar on the server stays. " +
                    "Changes that were not sent yet are lost."
            } else null
        )
    }
}

@Composable
private fun CalendarRow(calendar: CalendarEntity, syncing: Boolean, onClick: () -> Unit, onRefresh: () -> Unit) {
    // A calendar that is downloaded from the internet: a link or a CalDAV account
    val isLink = calendar.sourceUrl != null || calendar.accountId != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = "Edit calendar", onClick = onClick)
            .padding(start = 16.dp, end = if (isLink) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color(calendar.color)))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(calendar.name)
            if (!isLink) {
                StatusLine("On this phone", MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (syncing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text("Syncing...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            } else if (calendar.lastError != null) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Error, contentDescription = "Not working", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp).padding(top = 2.dp))
                    Text("Not working: ${calendar.lastError}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 6.dp))
                }
            } else if (calendar.lastSync == 0L) {
                StatusLine("Not synced yet", MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val ago = android.text.format.DateUtils.getRelativeTimeSpanString(
                    calendar.lastSync * 1000,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Working", tint = GoodGreen, modifier = Modifier.size(14.dp))
                    Text("Synced $ago", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (isLink) {
            IconButton(onClick = onRefresh, enabled = !syncing) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh ${calendar.name}")
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(text, fontSize = 12.sp, color = color)
}
