package org.nooklin.calendar.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.calendar.BuildConfig
import org.nooklin.calendar.data.backup.BackupFormatException
import org.nooklin.calendar.data.backup.BackupKind
import org.nooklin.calendar.data.backup.BackupOptions
import org.nooklin.calendar.data.backup.BackupSection
import org.nooklin.calendar.data.backup.ParsedBackup
import org.nooklin.calendar.data.backup.WrongSecretException
import org.nooklin.calendar.ui.CalendarViewModel
import org.nooklin.calendar.ui.common.GlassAlertDialog
import org.nooklin.calendar.ui.common.GlassChip
import org.nooklin.calendar.ui.common.GlassTextField
import org.nooklin.calendar.ui.theme.AppColors
import org.nooklin.calendar.ui.theme.MinTouchTarget

internal enum class Stage { NONE, OPTIONS, WORKING, BACKUP_DONE, PASSWORD, PREVIEW, RESTORE_DONE }

/** The steps of making and restoring a backup. The screen shows the dialogs of the step with [BackupDialogs]. */
class BackupFlow internal constructor(
    private val viewModel: CalendarViewModel,
    private val scope: CoroutineScope,
    private val context: Context
) {
    internal var stage by mutableStateOf(Stage.NONE)
    internal var options by mutableStateOf(BackupOptions())
    internal var usePin by mutableStateOf(false)
    internal var secret by mutableStateOf("")
    internal var secretAgain by mutableStateOf("")
    internal var error by mutableStateOf("")
    internal var parsed by mutableStateOf<ParsedBackup?>(null)
    internal var title by mutableStateOf("")
    internal var lines by mutableStateOf<List<String>>(emptyList())
    internal var notes by mutableStateOf<List<String>>(emptyList())
    private var bytes: ByteArray? = null
    internal var saver: ActivityResultLauncher<String>? = null
    internal var picker: ActivityResultLauncher<Array<String>>? = null

    fun startBackup() {
        clear()
        options = BackupOptions()
        usePin = false
        stage = Stage.OPTIONS
    }

    fun startRestore() {
        clear()
        picker?.launch(arrayOf("*/*"))
    }

    internal fun clear() {
        secret = ""
        secretAgain = ""
        error = ""
        parsed = null
        bytes = null
        title = ""
        lines = emptyList()
        notes = emptyList()
        stage = Stage.NONE
    }

    internal fun chooseFile() {
        saver?.launch("nooklin-calendar-${LocalDate.now()}.nkb")
    }

    internal fun onSaveTarget(uri: Uri?) {
        if (uri == null) return
        stage = Stage.WORKING
        scope.launch {
            try {
                val build = viewModel.createBackup(options, secret)
                val written = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(build.bytes) } != null }.getOrDefault(false)
                }
                if (written) {
                    title = "Backup saved"
                    lines = buildList {
                        add("${build.calendars} calendars, ${build.events} events, ${build.accounts} CalDAV accounts and ${build.settings} settings.")
                        add("Keep the password or PIN safe. Without it nobody can open the backup, and it cannot be recovered.")
                    }
                    notes = build.notes
                } else {
                    title = "The backup failed"
                    lines = listOf("The file could not be written.")
                    notes = emptyList()
                }
            } catch (e: Exception) {
                title = "The backup failed"
                lines = listOf(e.message ?: "Something went wrong.")
                notes = emptyList()
            }
            secret = ""
            secretAgain = ""
            stage = Stage.BACKUP_DONE
        }
    }

    internal fun onBackupPicked(uri: Uri?) {
        if (uri == null) return
        stage = Stage.WORKING
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            if (read == null) {
                title = "The backup could not be read"
                lines = listOf("The file could not be opened.")
                notes = emptyList()
                stage = Stage.RESTORE_DONE
            } else {
                bytes = read
                secret = ""
                error = ""
                stage = Stage.PASSWORD
            }
        }
    }

    internal fun openBackup() {
        val data = bytes ?: return
        stage = Stage.WORKING
        scope.launch {
            try {
                parsed = viewModel.openBackup(data, secret)
                stage = Stage.PREVIEW
            } catch (e: WrongSecretException) {
                error = e.message.orEmpty()
                stage = Stage.PASSWORD
            } catch (e: BackupFormatException) {
                error = e.message.orEmpty()
                stage = Stage.PASSWORD
            } catch (e: Exception) {
                error = "The backup could not be read."
                stage = Stage.PASSWORD
            }
        }
    }

    internal fun restore() {
        val backup = parsed ?: return
        stage = Stage.WORKING
        scope.launch {
            try {
                val report = viewModel.restoreBackup(backup)
                title = "Backup restored"
                lines = listOf("${report.calendars} calendars, ${report.events} events, ${report.accounts} CalDAV accounts and ${report.settings} settings.")
                notes = report.notes
            } catch (e: Exception) {
                title = "Restoring failed"
                lines = listOf(e.message ?: "Something went wrong.")
                notes = emptyList()
            }
            secret = ""
            parsed = null
            bytes = null
            stage = Stage.RESTORE_DONE
        }
    }
}

@Composable
fun rememberBackupFlow(viewModel: CalendarViewModel): BackupFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember(viewModel) { BackupFlow(viewModel, scope, context) }
    flow.saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { flow.onSaveTarget(it) }
    flow.picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { flow.onBackupPicked(it) }
    return flow
}

@Composable
fun BackupDialogs(flow: BackupFlow) {
    when (flow.stage) {
        Stage.NONE -> Unit
        Stage.OPTIONS -> OptionsDialog(flow)
        Stage.WORKING -> GlassAlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("One moment...", modifier = Modifier.padding(start = 12.dp))
                }
            }
        )
        Stage.PASSWORD -> PasswordDialog(flow)
        Stage.PREVIEW -> PreviewDialog(flow)
        Stage.BACKUP_DONE, Stage.RESTORE_DONE -> GlassAlertDialog(
            onDismissRequest = { flow.clear() },
            title = { Text(flow.title) },
            text = { DialogText(flow.lines, flow.notes, "Not included or not taken over:") },
            confirmButton = { TextButton(onClick = { flow.clear() }) { Text("OK") } }
        )
    }
}

private fun pinOk(value: String) = value.length == 6 && value.all { it in '0'..'9' }

@Composable
private fun OptionsDialog(flow: BackupFlow) {
    val options = flow.options
    val secretsIncluded = options.caldav || options.links
    val secretOk = if (flow.usePin) pinOk(flow.secret) else flow.secret.length >= 8
    val valid = options.any() && flow.secret == flow.secretAgain && secretOk
    GlassAlertDialog(
        onDismissRequest = { flow.clear() },
        wide = true,
        title = { Text("Create a backup") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OptionRow("Calendars and events", "The calendars on this phone and their events.", options.calendars) { flow.options = options.copy(calendars = it) }
                OptionRow("CalDAV accounts", "With the password, so you do not have to connect again.", options.caldav) { flow.options = options.copy(caldav = it) }
                OptionRow("Link calendars", "With the address, which can hold a secret.", options.links) { flow.options = options.copy(links = it) }
                OptionRow("Settings", "Theme, week start, dashboard and so on. Not the app lock.", options.settings) { flow.options = options.copy(settings = it) }
                Text("Protect the file with", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Password", !flow.usePin) { flow.usePin = false; flow.secret = ""; flow.secretAgain = "" }
                    GlassChip("6-digit PIN", flow.usePin) { flow.usePin = true; flow.secret = ""; flow.secretAgain = "" }
                }
                GlassTextField(
                    value = flow.secret,
                    onValueChange = { flow.secret = if (flow.usePin) it.filter { c -> c in '0'..'9' }.take(6) else it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text(if (flow.usePin) "PIN (6 digits)" else "Password (at least 8 characters)") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (flow.usePin) KeyboardType.NumberPassword else KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                GlassTextField(
                    value = flow.secretAgain,
                    onValueChange = { flow.secretAgain = if (flow.usePin) it.filter { c -> c in '0'..'9' }.take(6) else it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Again") },
                    keyboardOptions = KeyboardOptions(keyboardType = if (flow.usePin) KeyboardType.NumberPassword else KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (flow.usePin) {
                    Text(
                        "A PIN of 6 digits has only 1,000,000 possibilities, and anyone who has the file can try them all." +
                            if (secretsIncluded) " This backup holds passwords or addresses, so a password is much safer." else "",
                        fontSize = 12.sp,
                        color = AppColors.DestructiveText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Text(
                    "If you lose the password or PIN, nobody can open the backup, and it cannot be recovered.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                if (!options.any()) {
                    Text("Choose at least one part.", fontSize = 12.sp, color = AppColors.DestructiveText, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { flow.chooseFile() }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = { flow.clear() }) { Text("Cancel") } }
    )
}

@Composable
private fun OptionRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PasswordDialog(flow: BackupFlow) {
    GlassAlertDialog(
        onDismissRequest = { flow.clear() },
        wide = true,
        title = { Text("Restore a backup") },
        text = {
            Column {
                Text("Enter the password or PIN of this backup.", fontSize = 14.sp)
                GlassTextField(
                    value = flow.secret,
                    onValueChange = { flow.secret = it; flow.error = "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("Password or PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (flow.error.isNotEmpty()) {
                    Text(flow.error, fontSize = 14.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(enabled = flow.secret.isNotEmpty(), onClick = { flow.openBackup() }) { Text("Open") } },
        dismissButton = { TextButton(onClick = { flow.clear() }) { Text("Cancel") } }
    )
}

@Composable
private fun PreviewDialog(flow: BackupFlow) {
    val backup = flow.parsed ?: return
    val doc = backup.document
    val made = if (doc.created > 0) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochSecond(doc.created).atZone(ZoneId.systemDefault()))
    } else null
    val lines = ArrayList<String>()
    lines.add("Made with version ${doc.app}" + (made?.let { " on $it" } ?: "") + ". This app is version ${BuildConfig.VERSION_NAME}.")
    if (doc.app != BuildConfig.VERSION_NAME) {
        lines.add("This backup comes from another version. As much as possible is taken over, and what is not is listed below.")
    }
    val replaced = ArrayList<String>()
    if (BackupSection.CALENDARS in doc.sections) {
        val n = doc.calendars.count { it.kind == BackupKind.LOCAL }
        lines.add("Calendars on this phone: $n.")
        replaced.add("the calendars on this phone")
    }
    if (BackupSection.CALDAV in doc.sections) {
        lines.add("CalDAV accounts: ${doc.accounts.size}, with ${doc.calendars.count { it.kind == BackupKind.CALDAV }} calendars.")
        replaced.add("the CalDAV accounts")
    }
    if (BackupSection.LINKS in doc.sections) {
        lines.add("Link calendars: ${doc.calendars.count { it.kind == BackupKind.LINK }}.")
        replaced.add("the link calendars")
    }
    lines.add("Events: ${doc.events.size}.")
    if (BackupSection.SETTINGS in doc.sections) {
        lines.add("Settings: ${doc.settings.size}. The app lock and the screenshot setting are not restored.")
        replaced.add("the settings")
    }
    if (replaced.isEmpty()) {
        lines.add("There is nothing in this backup that can be restored.")
    } else {
        lines.add("Restoring replaces ${replaced.joinToString(", ")} in this app. This cannot be undone.")
    }
    GlassAlertDialog(
        onDismissRequest = { flow.clear() },
        wide = true,
        title = { Text("Replace with this backup?") },
        text = { DialogText(lines, backup.notes, "Not supported by this version:") },
        confirmButton = {
            TextButton(enabled = replaced.isNotEmpty(), onClick = { flow.restore() }) {
                Text("Replace", color = AppColors.DestructiveText)
            }
        },
        dismissButton = { TextButton(onClick = { flow.clear() }) { Text("Cancel") } }
    )
}

@Composable
private fun DialogText(lines: List<String>, notes: List<String>, notesTitle: String) {
    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
        lines.forEach { Text(it, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(notesTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            notes.forEach { Text("• $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp)) }
        }
    }
}
