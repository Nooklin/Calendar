package org.nooklin.calendar.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nooklin.calendar.data.caldav.CaldavService
import org.nooklin.calendar.data.caldav.ConnectResult
import org.nooklin.calendar.ui.common.GlassAlertDialog
import org.nooklin.calendar.ui.common.GlassTextField

private enum class Step { IDLE, CONNECTING, FAILED, NO_INTERNET }

/**
 * Connects a CalDAV account. The server is tried when Connect is pressed: while it is tried the dialog says so, a
 * server that does not work says why and stays open, one that works closes the dialog and its calendars appear.
 */
@Composable
fun CaldavDialog(
    onConnect: suspend (server: String, username: String, password: String, service: CaldavService) -> ConnectResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    fun hasInternet() = ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
    val scope = rememberCoroutineScope()
    var serviceKey by rememberSaveable { mutableStateOf(CaldavService.NEXTCLOUD.key) }
    val service = CaldavService.fromKey(serviceKey)
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(Step.IDLE) }
    var failure by remember { mutableStateOf("") }
    val busy = step == Step.CONNECTING

    // Back from the settings of the app: try again when the access was given
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (step == Step.NO_INTERNET && hasInternet()) step = Step.IDLE }

    GlassAlertDialog(
        wide = true,
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Connect a CalDAV account") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                // What kind of server it is decides the path that is added and what the login has to be
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaldavService.entries.forEach { option ->
                        FilterChip(
                            selected = option == service,
                            enabled = !busy,
                            onClick = {
                                // A fixed server is filled in, and taken out again when another kind is picked
                                if (server.isBlank() || CaldavService.entries.any { it.prefill == server }) server = option.prefill
                                serviceKey = option.key
                                if (step != Step.CONNECTING) step = Step.IDLE
                            },
                            label = { Text(option.label) }
                        )
                    }
                }
                Text(
                    service.hint,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
                )
                GlassTextField(
                    value = server,
                    onValueChange = { server = it; if (step == Step.FAILED) step = Step.IDLE },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server address") },
                    placeholder = { Text(service.example) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                GlassTextField(
                    value = username,
                    onValueChange = { username = it; if (step == Step.FAILED) step = Step.IDLE },
                    label = { Text(service.userLabel) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                GlassTextField(
                    value = password,
                    onValueChange = { password = it; if (step == Step.FAILED) step = Step.IDLE },
                    label = { Text(service.passwordLabel) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                when (step) {
                    Step.CONNECTING -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Connecting and downloading...", modifier = Modifier.padding(start = 12.dp))
                    }
                    Step.FAILED -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(failure, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                    Step.NO_INTERNET -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(
                            "This app has no internet access yet, so it cannot reach the server. Do you want to allow it? " +
                                "Open the settings of the app and give it network or internet access, then come back.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Step.IDLE -> Text(
                        "All calendars with events on the account are added. The password is stored encrypted on this phone.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = server.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !busy,
                onClick = {
                    if (step == Step.NO_INTERNET) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        )
                        return@TextButton
                    }
                    if (!hasInternet()) {
                        step = Step.NO_INTERNET
                        return@TextButton
                    }
                    step = Step.CONNECTING
                    scope.launch {
                        when (val result = onConnect(server, username, password, service)) {
                            is ConnectResult.Ok -> onDismiss()
                            is ConnectResult.Failed -> {
                                failure = result.reason
                                step = Step.FAILED
                            }
                        }
                    }
                }
            ) { Text(if (step == Step.NO_INTERNET) "Open settings" else "Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
