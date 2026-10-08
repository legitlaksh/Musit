package com.musit.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val LightBackground = Color(0xFFFFFBFE)
private val LightSurface = Color(0xFFF3EDF7)
private val LightSurfaceVariant = Color(0xFFE7E0EC)

/** Theme choices surfaced in Settings. */
enum class ThemeMode { LIGHT, DARK, SYSTEM }

/**
 * musit's branded theme. The purple identity (#9D4EDD seed) stays constant;
 * only the surfaces adapt to the chosen theme mode.
 */
private val MusitDarkScheme = darkColorScheme(
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

private val MusitLightScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D5FF),
    onPrimaryContainer = Color(0xFF4A2B7A),
    secondary = Color(0xFF7B2FC9),
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = Color(0xFF1C1B1F),
    surface = LightSurface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun MusitTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) MusitDarkScheme else MusitLightScheme,
        typography = Typography(),
        content = content
    )
}
