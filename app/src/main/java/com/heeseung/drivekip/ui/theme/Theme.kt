package com.heeseung.drivekip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GreenPrimary = Color(0xFF16A34A)       // Emerald 600
val GreenOnPrimary = Color(0xFFFFFFFF)
val GreenPrimaryContainer = Color(0xFFDCFCE7) // Emerald 100
val GreenOnPrimaryContainer = Color(0xFF14532D) // Emerald 900

val GreenSecondary = Color(0xFF0F766E)     // Teal 700
val GreenSecondaryContainer = Color(0xFFCCFBF1)
val GreenOnSecondaryContainer = Color(0xFF134E4A)

val GreenBackground = Color(0xFFF9FAF9)
val GreenSurface = Color(0xFFFFFFFF)
val GreenSurfaceVariant = Color(0xFFF0FDF4)
val GreenOnSurfaceVariant = Color(0xFF374151)
val GreenOutline = Color(0xFFB4E3B4)

private val LightColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenPrimaryContainer,
    onPrimaryContainer = GreenOnPrimaryContainer,
    secondary = GreenSecondary,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = GreenOnSecondaryContainer,
    background = GreenBackground,
    surface = GreenSurface,
    surfaceVariant = GreenSurfaceVariant,
    onSurfaceVariant = GreenOnSurfaceVariant,
    outline = GreenOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    primaryContainer = Color(0xFF166534),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondary = Color(0xFF2DD4BF),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF1E3A2B),
    onSurfaceVariant = Color(0xFFE2E8F0)
)

@Composable
fun DriveKipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
