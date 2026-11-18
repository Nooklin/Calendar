package org.nooklin.calendar.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.calendar.ImportRequest
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.ui.CalendarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsImportExportScreen(calendars: List<CalendarEntity>, viewModel: CalendarViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var exportId by remember { mutableStateOf(0L) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) ImportRequest.uri = uri
    }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
        if (uri != null) {
            scope.launch {
                val exported = viewModel.exportIcs(exportId)
                message = if (exported == null) {
                    "That calendar is gone."
                } else {
                    val ok = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(exported.second.toByteArray(Charsets.UTF_8)) } != null
                        }.getOrDefault(false)
                    }
                    if (ok) "Exported ${exported.first}." else "The file could not be written."
                }
            }
        }
    }

    val backup = rememberBackupFlow(viewModel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import and export", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "Import",
                listOf({ SettingsActionRow("Import an .ics file", Icons.Filled.Add) { picker.launch(arrayOf("*/*")) } })
            )
            SettingsNote("Events that are already in the calendar (the same uid) are replaced.")
            Spacer(Modifier.height(8.dp))
            val exportRows = ArrayList<@Composable () -> Unit>()
            calendars.forEach { calendar ->
                exportRows.add {
                    SettingsActionRow(calendar.name, Icons.Filled.Share) {
                        exportId = calendar.id
                        saver.launch(calendar.name.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ".ics")
                    }
                }
            }
            SettingsGroup("Export", exportRows)
            SettingsNote("Choose a calendar to write it as an .ics file.")
            if (message.isNotEmpty()) SettingsNote(message)
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                "Backup",
                listOf(
                    { SettingsActionRow("Create a backup", Icons.Filled.Backup) { backup.startBackup() } },
                    { SettingsActionRow("Restore from a backup", Icons.Filled.Restore) { backup.startRestore() } }
                )
            )
            SettingsNote("One encrypted file with your calendars, CalDAV accounts, link calendars and settings, to move to a new install.")
            Spacer(Modifier.height(16.dp))
        }
    }
    BackupDialogs(backup)
}
