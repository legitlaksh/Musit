package com.musit.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PurplePrimary = Color(0xFF9D4EDD)
private val PurpleSecondary = Color(0xFFBB86FC)
private val PurpleContainer = Color(0xFF4A2B7A)
private val OnPurpleContainer = Color(0xFFE9D5FF)
private val DarkBackground = Color(0xFF121212)
private val DarkSurface = Color(0xFF1E1E1E)
private val DarkSurfaceVariant = Color(0xFF2A2A2A)
private val OnDark = Color(0xFFEDEDED)
private val OnDarkVariant = Color(0xFFB0B0B0)

/**
 * Fixed branded dark theme for musit. Deliberately NOT dynamic —
 * the purple identity stays constant on every device.
 */
private val MusitColorScheme = darkColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = OnPurpleContainer,
    secondary = PurpleSecondary,
    onSecondary = Color.Black,
    secondaryContainer = PurpleContainer,
    onSecondaryContainer = OnPurpleContainer,
    background = DarkBackground,
    onBackground = OnDark,
    surface = DarkSurface,
    onSurface = OnDark,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkVariant
)

@Composable
fun MusitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MusitColorScheme,
        typography = Typography(),
        content = content
    )
}
