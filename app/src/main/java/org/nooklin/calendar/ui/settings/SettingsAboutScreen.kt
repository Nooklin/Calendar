package org.nooklin.calendar.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nooklin.calendar.BuildConfig
import org.nooklin.calendar.ui.common.GlassAlertDialog

// The same layout as the About screen of Nooklin Photos: version and changelog, then GitHub, privacy and terms
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAboutScreen(onOpenChangelog: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
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
                null,
                listOf(
                    { SettingsValueRow("Version", BuildConfig.VERSION_NAME) },
                    { SettingsNavRow("Changelog", Icons.Filled.History, Tile.Gray) { onOpenChangelog() } }
                )
            )
            Spacer(Modifier.height(8.dp))
            SettingsGroup(
                null,
                listOf(
                    {
                        SettingsNavRow("GitHub", Icons.Filled.Code, Tile.Gray) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Nooklin/Calendar")))
                            } catch (e: ActivityNotFoundException) {
                                // No browser installed, so there is nothing to open
                            }
                        }
                    },
                    { SettingsNavRow("Privacy", Icons.Filled.PrivacyTip, Tile.Red) { showPrivacy = true } },
                    { SettingsNavRow("Terms", Icons.Filled.Description, Tile.Gray) { showTerms = true } }
                )
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showPrivacy) {
        GlassAlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PRIVACY_LINES.forEach { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } }
        )
    }
    if (showTerms) {
        GlassAlertDialog(
            onDismissRequest = { showTerms = false },
            title = { Text("Terms") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TERMS_LINES.forEach { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { showTerms = false }) { Text("OK") } }
        )
    }
}

private val PRIVACY_LINES = listOf(
    "Your calendars and events are stored in a database on this phone and nowhere else.",
    "The app uses the internet only for the calendars you add by a link and for the CalDAV account you connect, and only with those addresses. A CalDAV password and the address of a link calendar are stored encrypted on this phone, and sent only to the server you entered.",
    "You can lock the app, block screenshots and hide reminders on the lock screen in Privacy and Security.",
    "It asks for four permissions: showing notifications, setting an alarm at the exact minute and setting the alarms again after the phone restarts (all for reminders), and the internet for links and CalDAV.",
    "There is no analytics, no crash reporting, no telemetry and no Google Play Services."
)

private val TERMS_LINES = listOf(
    "Nooklin Calendar is free software under the GNU General Public License version 3 (GPL-3.0). You may use, study, change and share it under the terms of that license.",
    "It comes without any warranty, as the license says.",
    "Questions: info@nooklin.org."
)
