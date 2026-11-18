package org.nooklin.calendar.quickadd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import org.nooklin.calendar.ui.common.GlassChip
import org.nooklin.calendar.ui.common.GlassPill
import org.nooklin.calendar.ui.common.GlassTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.calendar.data.CalendarRepository
import org.nooklin.calendar.data.EventDraft
import org.nooklin.calendar.data.Scope
import org.nooklin.calendar.data.db.CalendarDatabase
import org.nooklin.calendar.data.local.AppPreferences
import org.nooklin.calendar.data.local.AppThemeMode
import org.nooklin.calendar.ui.theme.DesignStyle
import org.nooklin.calendar.ui.theme.NooklinTheme

/**
 * A small window over whatever is on the screen: a title, today or tomorrow, and save. The event starts at the next
 * full hour today, or at 09:00 tomorrow, and lasts an hour. For more, use the editor in the app.
 */
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        val preferences = AppPreferences(app)
        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(AppThemeMode.SYSTEM)
            val contrastColor by preferences.contrastColor.collectAsStateWithLifecycle(org.nooklin.calendar.data.local.ContrastColor.DEFAULT)
            val appFont by preferences.appFont.collectAsStateWithLifecycle(org.nooklin.calendar.data.local.AppFont.INTER)
            val systemDark = isSystemInDarkTheme()
            val dark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> systemDark
                else -> true
            }
            NooklinTheme(
                style = DesignStyle.IOS,
                darkTheme = dark,
                contrastColor = contrastColor,
                appFont = appFont,
                blackMode = themeMode == AppThemeMode.BLACK
            ) {
                QuickAddContent(onDone = { finish() })
            }
        }
    }
}

@Composable
private fun QuickAddContent(onDone: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var title by rememberSaveable { mutableStateOf("") }
    var tomorrow by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun save() {
        if (title.isBlank() || saving) return
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                TimeZone.setDefault(null)
                val zone = ZoneId.systemDefault()
                val now = LocalDateTime.now(zone)
                val start = if (tomorrow) now.toLocalDate().plusDays(1).atTime(9, 0)
                else now.toLocalDate().atTime((now.hour + 1).coerceAtMost(22), 0)
                val calendars = CalendarDatabase.get(context).calendarDao().getAll()
                val local = calendars.filter { it.sourceUrl == null }
                val calendar = local.firstOrNull { it.visible } ?: local.firstOrNull()
                if (calendar != null) {
                    CalendarRepository(context).save(
                        EventDraft(
                            calendarId = calendar.id, title = title.trim(), location = "", notes = "",
                            allDay = false, zoneId = zone.id, start = start, end = start.plusHours(1),
                            rrule = null, reminders = emptyList()
                        ),
                        null, Scope.ALL
                    )
                }
            }
            onDone()
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.Center) {
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = GlassPill.adaptivePopupBackground,
            modifier = Modifier.fillMaxWidth().padding(24.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Quick add", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                GlassTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(top = 8.dp)
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Today", selected = !tomorrow) { tomorrow = false }
                    GlassChip("Tomorrow", selected = tomorrow) { tomorrow = true }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDone) { Text("Cancel") }
                    TextButton(onClick = { save() }, enabled = title.isNotBlank() && !saving) { Text("Save") }
                }
            }
        }
    }
}
