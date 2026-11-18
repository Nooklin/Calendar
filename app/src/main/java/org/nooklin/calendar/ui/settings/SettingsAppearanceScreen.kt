package org.nooklin.calendar.ui.settings

import org.nooklin.calendar.ui.common.touchTarget
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.nooklin.calendar.data.local.AppFont
import org.nooklin.calendar.data.local.AppThemeMode
import org.nooklin.calendar.data.local.ContrastColor
import org.nooklin.calendar.ui.theme.fontFamilyFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    contrastColor: ContrastColor,
    onContrastColorChange: (ContrastColor) -> Unit,
    appFont: AppFont,
    onAppFontChange: (AppFont) -> Unit,
    colorful: Boolean,
    onColorfulChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance", fontWeight = FontWeight.Bold) },
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
                "Theme and colors",
                listOf<@Composable () -> Unit>(
                    {
                        SettingsChoiceRow(
                            "Theme",
                            listOf(
                                AppThemeMode.SYSTEM to "Follow System",
                                AppThemeMode.LIGHT to "Light",
                                AppThemeMode.DARK to "Dark",
                                AppThemeMode.BLACK to "Black"
                            ),
                            themeMode,
                            onSelect = onThemeModeChange
                        )
                    },
                    { SettingsToggleRow("Colorful display", colorful, Icons.Filled.Palette, Tile.Purple, onCheckedChange = onColorfulChange) },
                    {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(ContrastColor.entries, key = { it.storageKey }) { option ->
                                ContrastColorSwatch(
                                    option = option,
                                    selected = option == contrastColor,
                                    onClick = { onContrastColorChange(option) }
                                )
                            }
                        }
                    }
                )
            )
            SettingsNote("Black uses a true black background, which saves battery on OLED screens.")
            SettingsNote("When Colorful display is off, the icons in the settings have no coloured tile.")
            SettingsNote("The row of colours tints the buttons, accents and pills of the app.")

            SettingsGroup(
                "Font",
                listOf<@Composable () -> Unit>({
                    SettingsChoiceRow(
                        "Font",
                        listOf(
                            AppFont.INTER to AppFont.INTER.label,
                            AppFont.SYSTEM_DEFAULT to AppFont.SYSTEM_DEFAULT.label,
                            AppFont.WORK_SANS to AppFont.WORK_SANS.label
                        ),
                        appFont,
                        fontFor = { fontFamilyFor(it) },
                        onSelect = onAppFontChange
                    )
                })
            )
            SettingsNote("Widgets always use the Inter font.")

            SettingsGroup("Feedback", listOf({ HapticsSetting() }))
            SettingsNote("Vibrates on the time wheels, in the view menu and on switches.")
        }
    }
}

// DEFAULT is an outlined circle, not a filled swatch: it means "no tint"
@Composable
private fun ContrastColorSwatch(option: ContrastColor, selected: Boolean, onClick: () -> Unit) {
    // The circle is 40 dp, the part you tap is 48 dp
    Box(Modifier.touchTarget().clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (option.color != null) {
                        Modifier.background(option.color)
                    } else {
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    }
                )
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = if (option.color != null) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
