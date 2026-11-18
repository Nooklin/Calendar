package org.nooklin.calendar.ui.ics

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import org.nooklin.calendar.ui.common.GlassTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.calendar.data.ImportResult
import org.nooklin.calendar.data.ImportTarget
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.ics.IcsFile
import org.nooklin.calendar.data.ics.IcsParser
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.settings.SettingsRadioRow

private const val MAX_BYTES = 8 * 1024 * 1024

/** Reads the file, tells what is in it, asks which calendar it goes to, and imports it. */
@Composable
fun ImportDialog(uri: Uri, calendars: List<CalendarEntity>, viewModel: CalendarViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var file by remember(uri) { mutableStateOf<IcsFile?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var result by remember(uri) { mutableStateOf<ImportResult?>(null) }
    // 0: a new calendar, otherwise the id of an existing one
    var choice by remember(uri) { mutableStateOf(0L) }
    var newName by remember(uri) { mutableStateOf("Imported") }
    var busy by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        val parsed: Pair<IcsFile?, String?> = withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    Pair(null, "The file could not be opened.")
                } else if (bytes.size > MAX_BYTES) {
                    Pair(null, "The file is larger than 8 MB.")
                } else {
                    val read = IcsParser.parse(String(bytes, Charsets.UTF_8))
                    if (read.events.isEmpty()) Pair(null, "There are no events in this file.") else Pair(read, null)
                }
            } catch (e: Exception) {
                Pair(null, "The file could not be read.")
            }
        }
        file = parsed.first
        error = parsed.second
        parsed.first?.name?.takeIf { it.isNotBlank() }?.let { newName = it }
    }

    val done = result
    val read = file
    GlassAlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (done != null) "Import done" else "Import events") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                when {
                    done != null -> {
                        Text("Added ${done.added}, replaced ${done.updated} in ${done.calendarName}.")
                        if (done.skipped > 0) Text("Left out: ${done.skipped} (cancelled or without a start).")
                        if (done.simplified > 0) {
                            Text("${done.simplified} event(s) have a repeat rule or time zone this app cannot show, so they are shown as a single event or in floating time.")
                        }
                    }
                    error != null -> Text(error.orEmpty())
                    read == null -> Text("Reading the file...")
                    else -> {
                        Text("${read.events.size} events found.")
                        Text("Put them in:", modifier = Modifier.padding(top = 8.dp))
                        SettingsRadioRow("A new calendar", choice == 0L) { choice = 0L }
                        if (choice == 0L) {
                            GlassTextField(value = newName, onValueChange = { newName = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                        calendars.forEach { calendar ->
                            SettingsRadioRow(calendar.name, choice == calendar.id) { choice = calendar.id }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                done != null || error != null -> TextButton(onClick = onClose) { Text("OK") }
                read != null -> TextButton(
                    enabled = !busy && (choice != 0L || newName.isNotBlank()),
                    onClick = {
                        busy = true
                        scope.launch {
                            val target = if (choice == 0L) ImportTarget.New(newName.trim()) else ImportTarget.Existing(choice)
                            result = viewModel.importIcs(read, target)
                            busy = false
                        }
                    }
                ) { Text("Import") }
            }
        },
        dismissButton = {
            if (done == null && error == null) TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") }
        }
    )
}
