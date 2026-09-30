package com.tasalo.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.ThemeMode

/** Paleta portada de la extensión (plan §6). */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5B8AFF),
    onPrimary = Color(0xFF09091E),
    primaryContainer = Color(0xFF1E2A5A),
    onPrimaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF09091E),
    onBackground = Color(0xFFE8EAF3),
    surface = Color(0xFF12122B),
    onSurface = Color(0xFFE8EAF3),
    surfaceVariant = Color(0xFF1B1B3A),
    onSurfaceVariant = Color(0xFFB4B8D0),
    outline = Color(0xFF4A4D78),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3A1A24),
    onErrorContainer = Color(0xFFFFD9D9),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B6EE8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E0FF),
    onPrimaryContainer = Color(0xFF0B1F5C),
    background = Color(0xFFE8EAF3),
    onBackground = Color(0xFF1A1B2E),
    surface = Color(0xFFF3F4FA),
    onSurface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFFDADDEB),
    onSurfaceVariant = Color(0xFF444862),
    outline = Color(0xFF8A8FAE),
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

data class ChangeColors(val up: Color, val down: Color, val neutral: Color) {
    fun of(change: Change): Color = when (change) {
        Change.UP -> up
        Change.DOWN -> down
        Change.NEUTRAL -> neutral
    }
}

val LocalChangeColors = staticCompositionLocalOf {
    ChangeColors(Color(0xFFFF6B6B), Color(0xFF4ADE80), Color.Gray)
}

@Composable
fun TasaloTheme(mode: ThemeMode, invertColors: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = if (dark) DarkColors else LightColors
    // Convención de la extensión: sube = rojo, baja = verde (configurable).
    val red = if (dark) Color(0xFFFF6B6B) else Color(0xFFDC2626)
    val green = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A)
    val colors = ChangeColors(
        up = if (invertColors) green else red,
        down = if (invertColors) red else green,
        neutral = scheme.onSurfaceVariant,
    )
    CompositionLocalProvider(LocalChangeColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}
