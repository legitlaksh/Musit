package com.musit.player.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musit.player.ui.theme.ThemeMode
import com.musit.player.data.store.AudioQuality
import kotlinx.coroutines.launch

/**
 * Settings screen - mockup style with white title, tinted headers.
 * Includes: Equalizer, Sleep timer, Cache, Privacy, Licenses, Version.
 */
@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDurationSec by viewModel.crossfadeDurationSec.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var themeDialogOpen by remember { mutableStateOf(false) }
    var qualityDialogOpen by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Appearance
        item { SectionHeader("Appearance") }
        item {
            SettingsItem(
                icon = Icons.Filled.Palette,
                title = "Theme",
                subtitle = themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = { themeDialogOpen = true }
            )
        }

        // Playback
        item { SectionHeader("Playback") }
        item {
            SettingsItem(
                icon = Icons.Filled.MusicNote,
                title = "Audio quality",
                subtitle = when (audioQuality) {
                    AudioQuality.LOW -> "Low"
                    AudioQuality.MEDIUM -> "Medium"
                    AudioQuality.HIGH -> "High"
                },
                onClick = { qualityDialogOpen = true }
            )
        }
        item {
            SettingsSwitchRow(
                icon = Icons.Filled.Loop,
                title = "Crossfade",
                subtitle = "Fade the current song out as the next fades in",
                checked = crossfadeEnabled,
                onCheckedChange = { viewModel.setCrossfadeEnabled(it) }
            )
        }
        item {
            SettingsSliderRow(
                icon = Icons.Filled.Loop,
                title = "Crossfade duration",
                valueText = "$crossfadeDurationSec s",
                value = crossfadeDurationSec.toFloat(),
                valueRange = 1f..12f,
                steps = 10,
                enabled = crossfadeEnabled,
                onValueChange = { viewModel.setCrossfadeDurationSec(it.roundToInt()) }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Equalizer,
                title = "Equalizer",
                subtitle = "Adjust bass, treble and presets",
                onClick = onEqualizerClick
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Timer,
                title = "Sleep timer",
                subtitle = "Stop playback after a set time",
                onClick = onSleepTimerClick
            )
        }

        // Storage
        item { SectionHeader("Storage") }
        item {
            SettingsItem(
                icon = Icons.Filled.Delete,
                title = "Clear image cache",
                subtitle = "Free up space used by artwork",
                onClick = {
                    viewModel.clearImageCache {
                        scope.launch { snackbarHostState.showSnackbar("Cache cleared") }
                    }
                }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Delete,
                title = "Clear search history",
                subtitle = "Remove all recent searches",
                onClick = {
                    viewModel.clearSearchHistory()
                    scope.launch { snackbarHostState.showSnackbar("Search history cleared") }
                }
            )
        }

        // Privacy
        item { SectionHeader("Privacy") }
        item {
            SettingsItem(
                icon = Icons.Filled.PrivacyTip,
                title = "Privacy policy",
                subtitle = "How we handle your data",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/legitlaksh/Musit"))
                    context.startActivity(intent)
                }
            )
        }

        // About
        item { SectionHeader("About") }
        item {
            SettingsItem(
                icon = Icons.Filled.Person,
                title = "Developer",
                subtitle = "laksh",
                onClick = {}
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.CameraAlt,
                title = "Instagram",
                subtitle = "@legit_laksh",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/legit_laksh"))
                    context.startActivity(intent)
                }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Info,
                title = "Open-source licenses",
                subtitle = "View third-party licenses",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/legitlaksh/Musit"))
                    context.startActivity(intent)
                }
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Info,
                title = "Version",
                subtitle = viewModel.appVersion(),
                onClick = {}
            )
        }
        item {
            SettingsItem(
                icon = Icons.Filled.Gavel,
                title = "Legal",
                subtitle = "musit is a free music player. Music content is provided by third-party services. musit does not host audio files. All trademarks belong to their respective owners.",
                onClick = {}
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    // Theme dialog
    if (themeDialogOpen) {
        AlertDialog(
            onDismissRequest = { themeDialogOpen = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        TextButton(
                            onClick = {
                                viewModel.setThemeMode(mode)
                                themeDialogOpen = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { themeDialogOpen = false }) { Text("Cancel") } }
        )
    }

    // Quality dialog
    if (qualityDialogOpen) {
        AlertDialog(
            onDismissRequest = { qualityDialogOpen = false },
            title = { Text("Audio quality") },
            text = {
                Column {
                    AudioQuality.entries.forEach { quality ->
                        TextButton(
                            onClick = {
                                viewModel.setAudioQuality(quality)
                                qualityDialogOpen = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(quality.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { qualityDialogOpen = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        color = Color(0xFF9D4EDD),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
                Text(valueText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), fontSize = 14.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
    }
}
