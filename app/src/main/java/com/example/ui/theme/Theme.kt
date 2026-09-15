package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemeController {
    var currentThemeMode by mutableStateOf(ThemeMode.DARK)
}

val LocalThemeController = compositionLocalOf { ThemeController }

private val DarkColorScheme = darkColorScheme(
    primary = Amber60,
    onPrimary = Color(0xFF2B1A00),
    primaryContainer = Color(0xFF3D2A08),
    onPrimaryContainer = Amber80,
    secondary = Teal60,
    onSecondary = Color(0xFF00201B),
    secondaryContainer = Color(0xFF0F332C),
    onSecondaryContainer = Teal80,
    tertiary = Teal80,
    background = DarkBackground,
    onBackground = Color(0xFFEDEBE7),
    surface = DarkSurface,
    onSurface = Color(0xFFEDEBE7),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Graphite80,
    outline = DarkBorder,
    error = StatusStopped
)

private val LightColorScheme = lightColorScheme(
    primary = Amber40,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFCE4BB),
    onPrimaryContainer = Color(0xFF2B1A00),
    secondary = Teal40,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EFE9),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Teal40,
    background = LightBackground,
    onBackground = Color(0xFF1B1A17),
    surface = LightSurface,
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Graphite40,
    outline = LightBorder,
    error = StatusStopped
)

// Deliberately uneven corner radii — a control-panel language rather than the
// "one radius on everything" SaaS-card default. Sharp for dense/technical
// surfaces, a touch softer only where content needs breathing room.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

/** Subtle brand glow used behind live/online indicators. Use sparingly. */
fun brandGlow(color: Color): Brush = Brush.radialGradient(
    colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0f))
)

@Composable
fun DhilipHomeTheme(
    themeMode: ThemeMode = ThemeController.currentThemeMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalThemeController provides ThemeController) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
