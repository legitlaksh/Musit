package com.musit.player.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musit.player.data.store.AudioQuality
import com.musit.player.ui.components.SectionHeader
import com.musit.player.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val audioQuality by viewModel.audioQuality.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var themeDialogOpen by remember { mutableStateOf(false) }
    var qualityDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp)
        )

        SectionHeader(title = "Appearance")
        SettingsRow(
            title = "Theme",
            subtitle = themeMode.name.lowercase().replaceFirstChar { it.uppercase() },
            onClick = { themeDialogOpen = true }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        SectionHeader(title = "Playback")
        SettingsRow(
            title = "Audio quality",
            subtitle = when (audioQuality) {
                AudioQuality.LOW -> "Low — smaller streams, less data"
                AudioQuality.MEDIUM -> "Medium — balanced"
                AudioQuality.HIGH -> "High — best available"
            },
            onClick = { qualityDialogOpen = true }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        SectionHeader(title = "Storage")
        SettingsRow(
            title = "Clear search history",
            subtitle = "Removes all recent searches",
            onClick = {
                viewModel.clearSearchHistory()
                scope.launch { snackbarHostState.showSnackbar("Search history cleared") }
            }
        )
        SettingsRow(
            title = "Clear image cache",
            subtitle = "Frees space used by artwork images",
            onClick = {
                viewModel.clearImageCache {
                    scope.launch { snackbarHostState.showSnackbar("Image cache cleared") }
                }
            }
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))

        SectionHeader(title = "About")
        SettingsRow(
            title = "Version",
            subtitle = viewModel.appVersion(),
            onClick = {}
        )
        Text(
            text = "musit streams publicly available music through an unofficial " +
                "client. No accounts, no tracking, no ads. Online content is " +
                "never presented as downloaded — local files live under " +
                "Library → On this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (themeDialogOpen) {
        SingleChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            selectedIndex = ThemeMode.entries.indexOf(themeMode),
            onDismiss = { themeDialogOpen = false },
            onSelect = { index ->
                viewModel.setThemeMode(ThemeMode.entries[index])
                themeDialogOpen = false
            }
        )
    }

    if (qualityDialogOpen) {
        SingleChoiceDialog(
            title = "Audio quality",
            options = listOf("Low", "Medium", "High"),
            selectedIndex = AudioQuality.entries.indexOf(audioQuality),
            onDismiss = { qualityDialogOpen = false },
            onSelect = { index ->
                viewModel.setAudioQuality(AudioQuality.entries[index])
                qualityDialogOpen = false
            }
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (index == selectedIndex) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
