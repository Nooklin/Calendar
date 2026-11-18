package org.nooklin.calendar.ui.settings

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import org.nooklin.calendar.data.local.LockDelay
import org.nooklin.calendar.ui.lock.AppLock

/** App lock, screenshots and reminders on the lock screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPrivacyScreen(
    appLock: Boolean,
    onAppLockChange: (Boolean) -> Unit,
    lockDelay: LockDelay,
    onLockDelayChange: (LockDelay) -> Unit,
    allowScreenshots: Boolean,
    onAllowScreenshotsChange: (Boolean) -> Unit,
    lockScreenNotifications: Boolean,
    onLockScreenNotificationsChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var unavailable by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy and Security", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup(
                "App lock",
                buildList<@Composable () -> Unit> {
                    add {
                        SettingsToggleRow("Lock the app", appLock, Icons.Filled.Lock, Tile.Red) { on ->
                            val activity = context as? Activity ?: return@SettingsToggleRow
                            val available = AppLock.available(context)
                            unavailable = !available && on
                            // Without a screen lock there is nothing to ask for, so turning the lock off must stay possible
                            if (!available) {
                                if (!on) onAppLockChange(false)
                                return@SettingsToggleRow
                            }
                            AppLock.authenticate(activity, if (on) "Turn on the app lock" else "Turn off the app lock") { passed ->
                                if (passed) onAppLockChange(on)
                            }
                        }
                    }
                    if (appLock) add {
                        SettingsChoiceRow("Lock again", LockDelay.entries.map { it to it.label }, lockDelay, onSelect = onLockDelayChange)
                    }
                }
            )
            SettingsNote(
                if (unavailable) "Set a screen lock or a fingerprint or face on your phone first."
                else "Asks for your fingerprint, face or the screen lock of your phone."
            )
            SettingsGroup(
                "Screenshots",
                listOf({
                    SettingsToggleRow("Allow screenshots", allowScreenshots, Icons.Filled.Visibility, Tile.Purple, onCheckedChange = onAllowScreenshotsChange)
                })
            )
            SettingsNote("Off blocks screenshots and recordings, and the picture in the recent apps.")
            SettingsGroup(
                "Reminders",
                listOf({
                    SettingsToggleRow(
                        "Show on the lock screen", lockScreenNotifications, Icons.Filled.Notifications, Tile.Orange,
                        onCheckedChange = onLockScreenNotificationsChange
                    )
                })
            )
            SettingsNote("Off hides the title on the lock screen, widgets always show your events.")
        }
    }
}
