package org.nooklin.calendar.ui.calendar

import org.nooklin.calendar.ui.theme.AppColors
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import java.time.ZoneId
import org.nooklin.calendar.data.Scope
import org.nooklin.calendar.data.model.Occurrence
import org.nooklin.calendar.data.model.Reminders
import org.nooklin.calendar.data.model.travelLabel
import org.nooklin.calendar.ui.common.GlassPill
import org.nooklin.calendar.ui.editor.ScopeDialog
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration

/** The details of one event, with the buttons to change, copy or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    occurrence: Occurrence,
    zone: ZoneId,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: (Scope) -> Unit,
    onDismiss: () -> Unit,
    readOnly: Boolean = false,
    readOnlyText: String = "This event is in a calendar that follows a link. It cannot be changed here."
) {
    var askDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassPill.adaptivePopupBackground,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(occurrence.title.ifEmpty { "(No title)" }, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(Color(occurrence.color)))
                Text(
                    occurrence.calendarName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            DetailLine("When", whenText(occurrence))
            if (occurrence.repeats) DetailLine("Repeats", "Yes, this is one of a series")
            if (occurrence.location.isNotEmpty()) LocationLine(occurrence.location)
            if (!occurrence.allDay) {
                if (occurrence.zoneId == null) {
                    DetailLine("Time zone", "Floating time, always the clock of this phone")
                } else if (occurrence.zoneId != zone.id) {
                    DetailLine("Time zone", "Made in ${occurrence.zoneId}, shown in ${zone.id}")
                }
            }
            if (occurrence.travelMinutes > 0 && !occurrence.allDay) DetailLine("Travel time", travelLabel(occurrence.travelMinutes))
            if (occurrence.notes.isNotEmpty()) DetailLine("Notes", occurrence.notes)
            if (occurrence.reminders.isNotEmpty()) {
                DetailLine("Reminders", occurrence.reminders.joinToString("\n") { Reminders.label(it, occurrence.allDay) })
            }
            Spacer(Modifier.height(16.dp))
            if (readOnly) {
                Text(
                    readOnlyText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onEdit) { Text("Edit") }
                    TextButton(onClick = onDuplicate) { Text("Duplicate") }
                    TextButton(onClick = { askDelete = true }) { Text("Delete", color = AppColors.DestructiveText) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (askDelete) {
        if (occurrence.repeats) {
            ScopeDialog(
                title = "Delete from a series",
                destructive = true,
                onPick = { askDelete = false; onDelete(it) },
                onDismiss = { askDelete = false }
            )
        } else {
            GlassAlertDialog(
                onDismissRequest = { askDelete = false },
                title = { Text("Delete this event?") },
                confirmButton = {
                    TextButton(onClick = { askDelete = false; onDelete(Scope.ALL) }) { Text("Delete", color = AppColors.DestructiveText) }
                },
                dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancel") } }
            )
        }
    }
}

@Composable
private fun LocationLine(location: String) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open in a navigation app") { openInNavigation(context, location) }
            .padding(vertical = 6.dp)
    ) {
        Text("Where", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(location, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)
    }
}

// geo: is answered by the maps and navigation apps on the phone; no map app is needed in this app
private fun openInNavigation(context: Context, location: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location)))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No navigation app found", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 16.sp)
    }
}

private fun whenText(o: Occurrence): String {
    val formatter = timeFormatter
    if (o.allDay) {
        return if (o.multiDay) "${fullDate(o.startDate)} to ${fullDate(o.lastDate)}, all day" else "${fullDate(o.startDate)}, all day"
    }
    val startTime = o.start.toLocalTime().format(formatter)
    val endTime = o.end.toLocalTime().format(formatter)
    return if (o.end.toLocalDate() == o.startDate || !o.end.isAfter(o.start)) {
        "${fullDate(o.startDate)}, $startTime to $endTime"
    } else {
        "${fullDate(o.startDate)}, $startTime\nto ${fullDate(o.end.toLocalDate())}, $endTime"
    }
}
