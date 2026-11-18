package org.nooklin.calendar.ui.calendar

import org.nooklin.calendar.ui.common.touchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import org.nooklin.calendar.ui.common.GlassTextField
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.nooklin.calendar.data.db.CalendarEntity
import org.nooklin.calendar.data.local.ContrastColor
import org.nooklin.calendar.ui.common.GlassPill
import androidx.compose.foundation.shape.RoundedCornerShape

internal val CALENDAR_COLORS: List<Int> = ContrastColor.entries.mapNotNull { it.color?.toArgb() }

/**
 * The calendars of the phone. A tap on a calendar shows or hides it in all views and widgets, the pencil changes its
 * name and colour or removes it, and Add calendar makes a new local calendar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarsSheet(
    calendars: List<CalendarEntity>,
    onToggle: (CalendarEntity) -> Unit,
    onAdd: (String, Int) -> Unit,
    onUpdate: (CalendarEntity, String, Int) -> Unit,
    onDelete: (CalendarEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<CalendarEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
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
                .padding(bottom = 16.dp)
        ) {
            Text(
                "Calendars",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            calendars.forEach { calendar ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 24.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = if (calendar.visible) "Hide" else "Show") { onToggle(calendar) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val color = Color(calendar.color)
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .then(
                                    if (calendar.visible) Modifier.background(color)
                                    else Modifier.border(2.dp, color, CircleShape)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (calendar.visible) {
                                Icon(Icons.Filled.Check, contentDescription = "Shown", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(calendar.name, modifier = Modifier.padding(start = 16.dp))
                    }
                    IconButton(onClick = { editing = calendar }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${calendar.name}")
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "Add calendar") { adding = true }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Add calendar", modifier = Modifier.padding(start = 16.dp))
            }
        }
    }

    if (adding) {
        CalendarDialog(
            initial = null,
            defaultColor = CALENDAR_COLORS[calendars.size % CALENDAR_COLORS.size],
            canDelete = false,
            onSave = { name, color -> adding = false; onAdd(name, color) },
            onDelete = {},
            onDismiss = { adding = false }
        )
    }
    val current = editing
    if (current != null) {
        CalendarDialog(
            initial = current,
            defaultColor = current.color,
            canDelete = calendars.count { it.sourceUrl == null && it.accountId == null } > 1 ||
                current.sourceUrl != null || current.accountId != null,
            onSave = { name, color -> editing = null; onUpdate(current, name, color) },
            onDelete = { editing = null; onDelete(current) },
            onDismiss = { editing = null }
        )
    }
}

/** Name and colour of a calendar. With [initial] it edits that calendar and can remove it, without it makes a new one. */
@Composable
fun CalendarDialog(
    initial: CalendarEntity?,
    defaultColor: Int,
    canDelete: Boolean,
    onSave: (String, Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    info: String? = null
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableStateOf(defaultColor) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete && initial != null) {
        GlassAlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name}?") },
            text = { Text("All events in this calendar are deleted too. This cannot be undone.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
        return
    }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New calendar" else "Edit calendar") },
        text = {
            Column {
                GlassTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                ColorChoices(color) { color = it }
                if (info != null) {
                    Text(
                        info,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                if (initial != null && canDelete) {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Delete calendar", color = MaterialTheme.colorScheme.error)
                    }
                } else if (initial != null) {
                    Text(
                        "The last calendar on this phone cannot be deleted.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), color) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** The colours to choose from, four in a row; the chosen one has a tick. */
@Composable
fun ColorChoices(color: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(top = 16.dp)) {
        CALENDAR_COLORS.chunked(4).forEach { rowColors ->
            Row {
                rowColors.forEach { option ->
                    // The circle stays 36 dp, the part you tap is 48 dp
                    Box(
                        Modifier
                            .touchTarget()
                            .clickable(onClickLabel = "Choose colour") { onChange(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(option)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (option == color) {
                                Icon(Icons.Filled.Check, contentDescription = "Chosen", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
