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
 * Línea visual "Quiet Glass" v3 (0.10.0): un solo acento turquesa sobre fondo azul noche, tarjetas de cristal
 * y rojo/verde desaturados para sube/baja (la convención sigue siendo configurable). Los valores salen de las variables CSS
 * de la extensión (--bg, --text, --accent, --up, --down...). Las superficies de la extensión son
 * blanco/tinta con 3-6 % de opacidad; aquí se precalculan sobre el fondo para el esquema de Material.
 */
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF3DD6C0),
    onPrimary = Color(0xFF06201C),
    primaryContainer = Color(0xFF123A38),
    onPrimaryContainer = Color(0xFFC9F5EE),
    secondary = Color(0xFF3DD6C0),
    onSecondary = Color(0xFF06201C),
    secondaryContainer = Color(0xFF123A38),
    onSecondaryContainer = Color(0xFFC9F5EE),
    background = Color(0xFF0E1621),
    onBackground = Color(0xFFE8EEF5),
    surface = Color(0xFF121C28),
    onSurface = Color(0xFFE8EEF5),
    surfaceVariant = Color(0xFF16212F),
    onSurfaceVariant = Color(0xFF9FB0C3),
    surfaceTint = Color(0xFF3DD6C0),
    surfaceDim = Color(0xFF0E1621),
    surfaceBright = Color(0xFF1E2B3A),
    surfaceContainerLowest = Color(0xFF0E1621),
    surfaceContainerLow = Color(0xFF101A25),
    surfaceContainer = Color(0xFF131E2B),
    surfaceContainerHigh = Color(0xFF1C2937),
    surfaceContainerHighest = Color(0xFF22313F),
    outline = Color(0xFF5F7389),
    outlineVariant = Color(0xFF223246),
    error = Color(0xFFF2555F),
    onError = Color(0xFF0E1621),
    errorContainer = Color(0xFF3A1A20),
    onErrorContainer = Color(0xFFFFD9DC),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF09665B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDE8),
    onPrimaryContainer = Color(0xFF0A4D45),
    secondary = Color(0xFF09665B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDEDE8),
    onSecondaryContainer = Color(0xFF0A4D45),
    background = Color(0xFFE6EEF1),
    onBackground = Color(0xFF1B2A33),
    surface = Color(0xFFDDE7EB),
    onSurface = Color(0xFF1B2A33),
    surfaceVariant = Color(0xFFD6E1E6),
    onSurfaceVariant = Color(0xFF4F6470),
    surfaceTint = Color(0xFF09665B),
    surfaceDim = Color(0xFFD7E2E7),
    surfaceBright = Color(0xFFF4F8FA),
    surfaceContainerLowest = Color(0xFFF3F7F9),
    surfaceContainerLow = Color(0xFFECF2F5),
    surfaceContainer = Color(0xFFE8EFF2),
    surfaceContainerHigh = Color(0xFFEEF4F6),
    surfaceContainerHighest = Color(0xFFF4F8FA),
    outline = Color(0xFF6E818C),
    outlineVariant = Color(0xFFC2D0D7),
    error = Color(0xFFBC2C3C),
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

internal val DarkGlass = GlassTokens(
    border = Color(0x14FFFFFF),
    borderAccent = Color(0x593DD6C0),
    accentSoft = Color(0x243DD6C0),
    surfaceGlass = Color(0x0DFFFFFF),
)

internal val LightGlass = GlassTokens(
    border = Color(0x171E3040),
    borderAccent = Color(0x4709665B),
    accentSoft = Color(0x1A09665B),
    surfaceGlass = Color(0x0A1E3040),
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
    ChangeColors(Color(0xFFF2555F), Color(0xFF34D399), Color(0xFF8FA0B3))
}

/**
 * Convención de la extensión: sube = rojo, baja = verde (configurable). En oscuro son los --up / --down / --neutral
 * de la extensión. En claro se oscurecen un paso respecto a ella (#d13a4b, #1a9c6b, #6b6b80) porque, a tamaño de
 * texto normal, no llegaban a 4,5:1 sobre las tarjetas (el verde se quedaba en 2,7:1); aquí cumplen WCAG AA.
 */
internal fun changeColorsFor(dark: Boolean, invert: Boolean): ChangeColors {
    val red = if (dark) Color(0xFFF2555F) else Color(0xFFBC2C3C)
    val green = if (dark) Color(0xFF34D399) else Color(0xFF13724F)
    return ChangeColors(
        up = if (invert) green else red,
        down = if (invert) red else green,
        neutral = if (dark) Color(0xFF8FA0B3) else Color(0xFF566873),
    )
}

@Composable
fun TasaloTheme(mode: ThemeMode, invertColors: Boolean, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalChangeColors provides changeColorsFor(dark, invertColors),
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
 * Fondo de la app: color base más un único halo turquesa discreto arriba a la izquierda, como el
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
