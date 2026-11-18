package org.nooklin.calendar.ui.settings

import org.nooklin.calendar.ui.theme.AppText
import org.nooklin.calendar.ui.common.GlassAlertDialog
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.nooklin.calendar.ui.theme.MinTouchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Whether the icons in the settings show on a coloured tile (Appearance > Colorful display). */
val LocalColorful = staticCompositionLocalOf { true }

// The tile colours of the iOS settings look, the same as in Nooklin Contacts
object Tile {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF30D158)
    val Orange = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF453A)
    val Purple = Color(0xFFBF5AF2)
    val Gray = Color(0xFF8E8E93)
}

/** The icon of a settings row: white on a coloured rounded tile, or the plain icon when the colourful display is off. */
@Composable
fun SettingsIcon(icon: ImageVector, tint: Color, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.38f
    if (LocalColorful.current) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(30.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(8.dp))
                .background(tint),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    } else {
        Icon(
            icon,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.padding(end = 16.dp)
        )
    }
}

// RadioButton keeps a real onClick: with null the dot didn't visually update on a device. Touches go to the innermost node, so no double toggle.
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.RadioButton) { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, fontFamily = fontFamily, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    icon: ImageVector,
    tint: Color = Tile.Blue,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val change: (Boolean) -> Unit = {
        haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onCheckedChange(it)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Switch, enabled = enabled) { change(!checked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsIcon(icon, tint, enabled)
            Text(label, color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f))
        }
        Switch(checked = checked, onCheckedChange = change, enabled = enabled)
    }
}

/** The short explanation under a group: one sentence, small and grey, as in Nooklin Photos. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = AppText.Caption,
        color = Color.Gray,
        modifier = modifier.padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

/** The heading above a group: bold, in the accent colour, as in Nooklin Photos. */
@Composable
fun SettingsHeading(title: String) {
    Text(
        title,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 28.dp, bottom = 8.dp)
    )
}

/** A row with the chosen value on the right and an arrow; a tap opens the list of choices. */
@Composable
fun <T> SettingsChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    fontFor: (T) -> FontFamily? = { null },
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(current, color = Color.Gray)
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
    if (open) {
        GlassAlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { (value, text) ->
                        SettingsRadioRow(text, value == selected, fontFamily = fontFor(value)) { onSelect(value); open = false }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Cancel") } }
        )
    }
}

/** A label on the left and a value on the right, in a settings group. */
@Composable
fun SettingsValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Text(value, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
