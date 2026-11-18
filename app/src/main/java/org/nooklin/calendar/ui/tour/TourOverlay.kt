package org.nooklin.calendar.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import org.nooklin.calendar.ui.common.GlassPill

/** What is on the screen right now. The tour moves on when it matches what a step waits for. */
data class TourContext(
    val viewMenuOpen: Boolean,
    val editorOpen: Boolean,
    val calendarsOpen: Boolean,
    val dashboard: Boolean,
    val settingsOpen: Boolean,
    val calendarsSettingsOpen: Boolean
)

/** What the tour can do to the app: go back to the calendar, or end. */
class TourActions(
    val closeEditor: () -> Unit,
    val showCalendar: () -> Unit,
    val finish: () -> Unit
)

private enum class Place { TOP, CENTER, BOTTOM }

private class TourStep(
    val icon: ImageVector,
    val title: String,
    // A short line, or a list of points, or both. [footer] is a last line in smaller text.
    val text: String = "",
    val points: List<String> = emptyList(),
    val footer: String? = null,
    // The button that pulses. The step waits until the person taps it: [advanceWhen] says when the tap has worked.
    val target: String? = null,
    val advanceWhen: ((TourContext) -> Boolean)? = null,
    // Shown under the text for a step that waits for the person
    val hint: String? = null,
    val place: Place = Place.CENTER,
    val scrim: Float = 0.35f,
    // Done when Next is tapped, for a step that has Next
    val onNext: ((TourActions) -> Unit)? = null,
    val needsDashboard: Boolean = false
)

private const val TAP_HINT = "Tap the pulsing button to continue."

private val STEPS = listOf(
    TourStep(
        Icons.Filled.CalendarMonth, "Welcome to Nooklin Calendar",
        points = listOf(
            "Five ways to see your days",
            "A dashboard with your free evenings",
            "Sync with Nextcloud and other CalDAV servers",
            "An app lock to keep it private",
            "No Google and no tracking"
        ),
        footer = "This tour will take you through the most important features of the app and will only take 1 minute.",
        scrim = 0.6f
    ),
    TourStep(
        Icons.Filled.ViewAgenda, "Choose a view",
        "This button changes the view.",
        target = "view", advanceWhen = { it.viewMenuOpen }, hint = TAP_HINT
    ),
    TourStep(
        Icons.Filled.ViewAgenda, "Five views",
        points = listOf("Day, Week, Month and Overview", "In Month this menu also sets the month layout"),
        advanceWhen = { !it.viewMenuOpen }, hint = "Tap outside the menu to continue.", place = Place.BOTTOM
    ),
    TourStep(
        Icons.Filled.Add, "Make an event",
        "Every event starts with the plus.",
        target = "add", advanceWhen = { it.editorOpen }, hint = TAP_HINT
    ),
    TourStep(
        Icons.Filled.Add, "The event form",
        points = listOf(
            "Title, calendar, start and end",
            "A repeat and reminders, if you like",
            "A title like an earlier event fills in the details",
            "Tap Save to keep the event"
        ),
        place = Place.BOTTOM, onNext = { it.closeEditor() }
    ),
    TourStep(
        Icons.Filled.Event, "Your calendars",
        "Every calendar has its own colour.",
        target = "calendars", advanceWhen = { it.calendarsOpen }, hint = TAP_HINT
    ),
    TourStep(
        Icons.Filled.Event, "Show, hide and add",
        points = listOf("Tap a calendar to show or hide it", "Add a new calendar", "Change a name or colour, or delete one"),
        advanceWhen = { !it.calendarsOpen }, hint = "Tap outside the sheet to continue.", place = Place.TOP
    ),
    TourStep(
        Icons.Filled.Dashboard, "The dashboard",
        "This button opens the dashboard.",
        target = "dashboard", advanceWhen = { it.dashboard }, hint = TAP_HINT, needsDashboard = true
    ),
    TourStep(
        Icons.Filled.Dashboard, "Your day at a glance",
        points = listOf(
            "Is tonight free?",
            "What is next, and when do you have time?",
            "Tap a free time to make an event there",
            "Choose the cards in Settings > Dashboard"
        ),
        onNext = { it.showCalendar() }, needsDashboard = true
    ),
    TourStep(
        Icons.Filled.Settings, "Settings",
        "Everything else is in the Settings.",
        target = "settings", advanceWhen = { it.settingsOpen }, hint = TAP_HINT
    ),
    TourStep(
        Icons.Filled.Cloud, "Add more calendars",
        "Open Calendars to add more.",
        target = "settings_calendars", advanceWhen = { it.calendarsSettingsOpen }, hint = TAP_HINT, place = Place.BOTTOM
    ),
    TourStep(
        Icons.Filled.Cloud, "Links and CalDAV",
        points = listOf(
            "Follow an .ics link",
            "Connect CalDAV: Nextcloud, Fastmail, iCloud and more",
            "Only these use the internet"
        ),
        place = Place.BOTTOM, onNext = { it.showCalendar() }
    ),
    TourStep(
        Icons.AutoMirrored.Filled.Help, "That is all",
        points = listOf(
            "Help and Support explains more",
            "Privacy and Security has an app lock",
            "Settings > Tour shows this tour again"
        ),
        scrim = 0.6f
    )
)

/**
 * The tour of the first start. A card explains one step at a time. For most steps a button pulses and the person has
 * to tap it: the tour then moves on by itself when the screen it opens is there. [actions] ends the tour.
 */
@Composable
fun TourOverlay(context: TourContext, dashboardAvailable: Boolean, actions: TourActions) {
    val steps = remember(dashboardAvailable) { STEPS.filter { dashboardAvailable || !it.needsDashboard } }
    var storedIndex by rememberSaveable { mutableStateOf(0) }
    val index = storedIndex.coerceIn(0, steps.lastIndex)
    val step = steps[index]
    val last = index == steps.lastIndex
    val waiting = step.advanceWhen != null

    // Back ends the tour; the screens stay as they are
    BackHandler { actions.finish() }

    // A step that waits for the person moves on as soon as the screen is what it waited for
    LaunchedEffect(index, context) {
        if (step.advanceWhen?.invoke(context) == true && !last) storedIndex = index + 1
    }

    val pulse by rememberInfiniteTransition(label = "tour pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "tour pulse value"
    )
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // The place of the pulsing button, in the coordinates of this layer
    val hole: Rect? = step.target?.let { TourTargets.bounds[it] }
        ?.translate(-origin.x, -origin.y)
        ?.inflate(with(density) { 6.dp.toPx() })

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                size = Offset(it.size.width.toFloat(), it.size.height.toFloat())
            }
    ) {
        // The dark layer with a hole at the button, and a ring that grows out of the hole
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(Color.Black.copy(alpha = step.scrim))
            if (hole != null) {
                val progress = pulse
                val corner = CornerRadius(minOf(hole.width, hole.height) / 2f)
                drawRoundRect(Color.Black, hole.topLeft, hole.size, corner, blendMode = BlendMode.Clear)
                val ring = hole.inflate(progress * 16.dp.toPx())
                drawRoundRect(
                    Color.White.copy(alpha = 1f - progress),
                    ring.topLeft,
                    ring.size,
                    CornerRadius(minOf(ring.width, ring.height) / 2f),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        // Everything is blocked, except the hole: the person taps the real button there
        if (hole == null) {
            Blocker(0f, 0f, size.x, size.y)
        } else {
            Blocker(0f, 0f, size.x, hole.top)
            Blocker(0f, hole.bottom, size.x, size.y - hole.bottom)
            Blocker(0f, hole.top, hole.left, hole.height)
            Blocker(hole.right, hole.top, size.x - hole.right, hole.height)
        }

        val alignment = when (step.place) {
            Place.TOP -> Alignment.TopCenter
            Place.CENTER -> Alignment.Center
            Place.BOTTOM -> Alignment.BottomCenter
        }
        val edge = when (step.place) {
            Place.TOP -> Modifier.statusBarsPadding().padding(top = 16.dp)
            Place.CENTER -> Modifier
            Place.BOTTOM -> Modifier.navigationBarsPadding().padding(bottom = 24.dp)
        }
        Column(
            Modifier
                .align(alignment)
                .then(edge)
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(GlassPill.adaptivePopupBackground)
                .border(GlassPill.adaptiveBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(step.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                step.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            if (step.text.isNotEmpty()) {
                Text(
                    step.text,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (step.points.isNotEmpty()) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    step.points.forEach { point ->
                        Row(verticalAlignment = Alignment.Top) {
                            Text("\u2022", fontSize = 17.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(20.dp))
                            Text(point, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (step.footer != null) {
                Spacer(Modifier.height(14.dp))
                Text(step.footer, fontSize = 14.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (step.hint != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(step.hint, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == index) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (i == index) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (last) Spacer(Modifier.size(1.dp)) else TextButton(onClick = actions.finish) { Text("Skip") }
                // A step that waits for a tap has no Next: the tap is the way on
                if (!waiting) {
                    Button(onClick = {
                        step.onNext?.invoke(actions)
                        if (last) actions.finish() else storedIndex = index + 1
                    }) { Text(if (last) "Done" else "Next") }
                }
            }
        }
    }
}

/** A layer that takes every tap, so the screen under it does not react. */
@Composable
private fun Blocker(x: Float, y: Float, width: Float, height: Float) {
    if (width <= 0f || height <= 0f) return
    val density = LocalDensity.current
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(with(density) { width.toDp() }, with(density) { height.toDp() })
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    )
}
