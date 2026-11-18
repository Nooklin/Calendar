package org.nooklin.calendar.ui.settings

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
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import org.nooklin.calendar.ui.common.GlassTextField
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.nooklin.calendar.data.SubscribeResult
import org.nooklin.calendar.ui.calendar.ColorChoices

private enum class Phase { IDLE, CHECKING, FAILED }

/**
 * Follows a web calendar by its link. The link is tried when Subscribe is pressed: while it is checked the dialog says
 * so, a link that does not work says why and stays open, a link that works closes the dialog.
 */
@Composable
fun SubscribeDialog(
    defaultColor: Int,
    onSubscribe: suspend (url: String, name: String, color: Int) -> SubscribeResult,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(defaultColor) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var failure by remember { mutableStateOf("") }
    val busy = phase == Phase.CHECKING

    GlassAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Subscribe to a link") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                GlassTextField(
                    value = url,
                    onValueChange = { url = it; if (phase == Phase.FAILED) phase = Phase.IDLE },
                    label = { Text("Link (https:// or webcal://)") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.padding(top = 8.dp)
                )
                ColorChoices(color) { color = it }
                when (phase) {
                    Phase.CHECKING -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Checking the link...", modifier = Modifier.padding(start = 12.dp))
                    }
                    Phase.FAILED -> Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Text(failure, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                    }
                    Phase.IDLE -> Text(
                        "The calendar is read only. It is refreshed when the app starts, at most once an hour, and with the refresh button.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !busy,
                onClick = {
                    phase = Phase.CHECKING
                    scope.launch {
                        when (val result = onSubscribe(url, name, color)) {
                            is SubscribeResult.Ok -> onDismiss()
                            is SubscribeResult.Failed -> {
                                failure = result.reason
                                phase = Phase.FAILED
                            }
                        }
                    }
                }
            ) { Text("Subscribe") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } }
    )
}
