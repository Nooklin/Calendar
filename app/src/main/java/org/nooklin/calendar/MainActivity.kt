package org.nooklin.calendar

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import org.nooklin.calendar.data.local.AppPreferences
import org.nooklin.calendar.ui.lock.AppLock
import org.nooklin.calendar.ui.lock.LockGate
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.nooklin.calendar.reminder.ReminderScheduler
import org.nooklin.calendar.ui.NooklinCalendarApp

private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {}
}

class MainActivity : ComponentActivity() {
    private val haptics by lazy { AppPreferences(applicationContext).hapticsEnabled }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only the first start: a turn of the screen delivers the same intent again
        if (savedInstanceState == null) {
            LaunchRequest.from(intent)
            ImportRequest.from(intent)
            // A new start always begins locked
            AppLock.reset()
        }
        watchPrivacySettings()
        // Whenever the app opens: show what became due while it was closed and set the alarm for the next reminder
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            ReminderScheduler.process(app)
            ReminderScheduler.schedule(app)
        }
        setContent {
            // With the setting off the whole app gets a feedback that does nothing
            val realHaptics = LocalHapticFeedback.current
            val hapticsOn by haptics.collectAsStateWithLifecycle(true)
            CompositionLocalProvider(LocalHapticFeedback provides if (hapticsOn) realHaptics else NoHaptics) {
                LockGate { NooklinCalendarApp() }
            }
        }
    }

    // The app lock, the screenshot setting and the picture in the recent apps follow the settings as they change
    private fun watchPrivacySettings() {
        val prefs = AppPreferences(applicationContext)
        lifecycleScope.launch {
            prefs.appLock.collect { on ->
                AppLock.enabled = on
                if (!on) AppLock.locked = false
            }
        }
        // Previews of an older version that were set by the app are removed, the list of widgets shows the fixed previews
        lifecycleScope.launch(Dispatchers.IO) { org.nooklin.calendar.widget.WidgetUpdater.clearPreviews(applicationContext) }
        lifecycleScope.launch { prefs.lockDelay.collect { AppLock.delaySeconds = it.seconds } }
        // The background sync of the followed calendars follows the settings; the widgets are refreshed every 15 minutes
        lifecycleScope.launch {
            combine(prefs.backgroundSync, prefs.backgroundSyncMinutes, prefs.backgroundSyncWifiOnly) { on, minutes, wifi -> Triple(on, minutes, wifi) }
                .collect { (on, minutes, wifi) -> org.nooklin.calendar.sync.BackgroundSync.applySync(applicationContext, on, minutes, wifi) }
        }
        org.nooklin.calendar.sync.BackgroundSync.applyWidgets(applicationContext)
        lifecycleScope.launch {
            combine(prefs.allowScreenshots, prefs.appLock) { allow, lock -> allow to lock }.collect { (allow, lock) ->
                if (allow) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                // With the app lock on, the recent apps overview does not show the calendar either
                setRecentsScreenshotEnabled(allow && !lock)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.lockIfAway()
    }

    override fun onStop() {
        super.onStop()
        // A turn of the screen and the system prompt are not leaving the app
        if (!isChangingConfigurations && !AppLock.prompting) AppLock.leftAt = SystemClock.elapsedRealtime()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchRequest.from(intent)
        ImportRequest.from(intent)
    }
}
