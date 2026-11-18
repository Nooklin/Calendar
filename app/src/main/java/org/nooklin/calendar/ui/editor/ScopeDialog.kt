package org.nooklin.calendar.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.nooklin.calendar.data.Scope

/** Asks which events of a repeating series a change or a delete is about. */
@Composable
fun ScopeDialog(title: String, destructive: Boolean, onPick: (Scope) -> Unit, onDismiss: () -> Unit) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = { onPick(Scope.THIS) }) { Text(if (destructive) "Delete only this event" else "Only this event") }
                TextButton(onClick = { onPick(Scope.FOLLOWING) }) { Text(if (destructive) "Delete this and following events" else "This and following events") }
                TextButton(onClick = { onPick(Scope.ALL) }) { Text(if (destructive) "Delete all events" else "All events") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
