package com.tasalo.android.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.ThemeMode

/*
 * Línea visual "Quiet Glass" v2, portada de `taso-ext`/`taso-extmf` (popup.css): un solo acento índigo,
 * fondo casi negro / gris azulado, rojo y verde desaturados. Los valores salen de las variables CSS
 * de la extensión (--bg, --text, --accent, --up, --down...). Las superficies de la extensión son
 * blanco/tinta con 3-6 % de opacidad; aquí se precalculan sobre el fondo para el esquema de Material.
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6C81FF),
    onPrimary = Color(0xFF0A0A10),
    primaryContainer = Color(0xFF181B31),
    onPrimaryContainer = Color(0xFFD5DBFF),
    secondary = Color(0xFF6C81FF),
    onSecondary = Color(0xFF0A0A10),
    secondaryContainer = Color(0xFF181B31),
    onSecondaryContainer = Color(0xFFD5DBFF),
    background = Color(0xFF0A0A10),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF111117),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF16161C),
    onSurfaceVariant = Color(0xFF8D8DA3),
    surfaceTint = Color(0xFF6C81FF),
    surfaceDim = Color(0xFF0A0A10),
    surfaceBright = Color(0xFF1D1D26),
    surfaceContainerLowest = Color(0xFF0A0A10),
    surfaceContainerLow = Color(0xFF0F0F16),
    surfaceContainer = Color(0xFF131319),
    surfaceContainerHigh = Color(0xFF181820),
    surfaceContainerHighest = Color(0xFF1D1D26),
    outline = Color(0xFF55556A),
    outlineVariant = Color(0xFF26262F),
    error = Color(0xFFF2555F),
    onError = Color(0xFF0A0A10),
    errorContainer = Color(0xFF3A1A20),
    onErrorContainer = Color(0xFFFFD9DC),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A63E0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8DCEE),
    onPrimaryContainer = Color(0xFF1F2F8A),
    secondary = Color(0xFF4A63E0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8DCEE),
    onSecondaryContainer = Color(0xFF1F2F8A),
    background = Color(0xFFE8EAF0),
    onBackground = Color(0xFF2B2C3A),
    surface = Color(0xFFE0E2E8),
    onSurface = Color(0xFF2B2C3A),
    surfaceVariant = Color(0xFFDBDDE4),
    onSurfaceVariant = Color(0xFF5E5F72),
    surfaceTint = Color(0xFF4A63E0),
    surfaceDim = Color(0xFFDADCE3),
    surfaceBright = Color(0xFFF6F7FA),
    surfaceContainerLowest = Color(0xFFF5F6FA),
    surfaceContainerLow = Color(0xFFEFF0F5),
    surfaceContainer = Color(0xFFEBECF2),
    surfaceContainerHigh = Color(0xFFF1F2F7),
    surfaceContainerHighest = Color(0xFFF6F7FA),
    outline = Color(0xFF8D8EA0),
    outlineVariant = Color(0xFFCBCDD8),
    error = Color(0xFFD13A4B),
    onError = Color.White,
    errorContainer = Color(0xFFF8D9DD),
    onErrorContainer = Color(0xFF5C0F1B),
)

/** Tokens de "cristal" que Material no tiene: bordes finos, borde de acento, acento suave, cristal de tarjeta. */
@Immutable
data class GlassTokens(
    val border: Color,
    val borderAccent: Color,
    val accentSoft: Color,
    val surfaceGlass: Color,
)

private val DarkGlass = GlassTokens(
    border = Color(0x12FFFFFF),
    borderAccent = Color(0x596C81FF),
    accentSoft = Color(0x246C81FF),
    surfaceGlass = Color(0x08FFFFFF),
)

private val LightGlass = GlassTokens(
    border = Color(0x171E2030),
    borderAccent = Color(0x474A63E0),
    accentSoft = Color(0x1A4A63E0),
    surfaceGlass = Color(0x0A1E2030),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

data class ChangeColors(val up: Color, val down: Color, val neutral: Color) {
    fun of(change: Change): Color = when (change) {
        Change.UP -> up
        Change.DOWN -> down
        Change.NEUTRAL -> neutral
    }
}

val LocalChangeColors = staticCompositionLocalOf {
    ChangeColors(Color(0xFFF2555F), Color(0xFF34D399), Color(0xFF86869C))
}

@Composable
fun TasaloTheme(mode: ThemeMode, invertColors: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = if (dark) DarkColors else LightColors
    // Convención de la extensión: sube = rojo, baja = verde (configurable). Valores --up / --down / --neutral.
    val red = if (dark) Color(0xFFF2555F) else Color(0xFFD13A4B)
    val green = if (dark) Color(0xFF34D399) else Color(0xFF1A9C6B)
    val colors = ChangeColors(
        up = if (invertColors) green else red,
        down = if (invertColors) red else green,
        neutral = if (dark) Color(0xFF86869C) else Color(0xFF6B6B80),
    )
    CompositionLocalProvider(
        LocalChangeColors provides colors,
        LocalGlass provides (if (dark) DarkGlass else LightGlass),
    ) {
        MaterialTheme(colorScheme = scheme, typography = TasaloTypography, shapes = TasaloShapes, content = content)
    }
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.AUTO -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

/**
 * Fondo de la app: color base más un único halo índigo discreto arriba a la izquierda, como el
 * `body::before` de la extensión (radial-gradient ... var(--accent-soft)).
 */
@Composable
fun Modifier.quietGlassBackground(): Modifier {
    val base = MaterialTheme.colorScheme.background
    val halo = LocalGlass.current.accentSoft
    return this
        .background(base)
        .drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(halo, Color.Transparent),
                    center = Offset(size.width * 0.08f, -size.height * 0.05f),
                    radius = size.width * 0.95f,
                ),
            )
        }
}
