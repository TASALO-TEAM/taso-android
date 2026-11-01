package com.tasalo.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.tasalo.android.domain.Palette

/*
 * Paletas elegibles en Ajustes. Los valores salen de `taso-inv/web/css/tokens.css` (un bloque por paleta y modo);
 * la Clasica no esta aqui: es la de Theme.kt (DarkColors/LightColors), que ya existia y no cambia.
 * `accent` es el color para texto y bordes (en claro, la variante oscura que llega a 4,5:1); `brand` solo tine el
 * relleno suave y en claro puede ser mas vivo. ContrastTest revisa todas las paletas en los dos modos.
 */
internal class PaletteTokens(
    val bg: Color, val surface: Color, val card: Color, val cardHigh: Color,
    val accent: Color, val brand: Color, val onAccent: Color,
    val text: Color, val text2: Color, val outline: Color,
    val ok: Color, val bad: Color, val soft: Float,
)

private fun pal(
    bg: Long, surface: Long, card: Long, cardHigh: Long, accent: Long, brand: Long, onAccent: Long,
    text: Long, text2: Long, outline: Long, ok: Long, bad: Long, soft: Float,
) = PaletteTokens(
    Color(0xFF000000 or bg), Color(0xFF000000 or surface), Color(0xFF000000 or card), Color(0xFF000000 or cardHigh),
    Color(0xFF000000 or accent), Color(0xFF000000 or brand), Color(0xFF000000 or onAccent),
    Color(0xFF000000 or text), Color(0xFF000000 or text2), Color(0xFF000000 or outline),
    Color(0xFF000000 or ok), Color(0xFF000000 or bad), soft,
)

/** bg, surface, card, cardHigh, accent, brand, onAccent, text, text2, outline, ok, bad, alfa del relleno suave. Oscuro y claro. */
private val TOKENS: Map<Palette, Pair<PaletteTokens, PaletteTokens>> = mapOf(
    Palette.SOFT to Pair(
        pal(0x14130F, 0x1A1814, 0x211F1A, 0x2A2721, 0xF0C94A, 0xF0C94A, 0x1F1A06, 0xF1EEE4, 0xB1AB9C, 0x7A7566, 0x34D399, 0xF2555F, 0.14f),
        pal(0xEDEBE6, 0xF3F1EC, 0xFBF9F4, 0xFFFEFB, 0x6B5000, 0xF2C94C, 0x1F1D18, 0x1F1D18, 0x5E5A50, 0x857F70, 0x13724F, 0xB52A39, 0.22f),
    ),
    Palette.OCEANO to Pair(
        pal(0x06141B, 0x0B1A23, 0x11212D, 0x253745, 0xCCD0CF, 0xCCD0CF, 0x06141B, 0xCCD0CF, 0x9BA8AB, 0x6B7F8E, 0x34D399, 0xF2555F, 0.12f),
        pal(0xD9DDDC, 0xCFD4D3, 0xE7EAE9, 0xF1F3F2, 0x253745, 0x253745, 0xFFFFFF, 0x06141B, 0x34454F, 0x4A5C6A, 0x0C5F40, 0xA82434, 0.1f),
    ),
    Palette.AMBER to Pair(
        pal(0x272220, 0x2E2825, 0x362F2B, 0x433B35, 0xCE9C7D, 0xCE9C7D, 0x1E1510, 0xEBEFEE, 0xCCB499, 0x867668, 0x34D399, 0xF46B74, 0.14f),
        pal(0xEBEFEE, 0xE0E4E2, 0xF4F6F5, 0xFBFCFB, 0x945535, 0xBB6C43, 0x1A110C, 0x4A413C, 0x6A5C52, 0x8D8883, 0x13724F, 0xB52A39, 0.1f),
    ),
    Palette.CALCITE to Pair(
        pal(0x202326, 0x272B2F, 0x2F3438, 0x3C4044, 0xFD8C5A, 0xFD8C5A, 0x1E1008, 0xDDDCDB, 0xEDBF9B, 0x917966, 0x34D399, 0xF4727A, 0.14f),
        pal(0xDDDCDB, 0xD2D1CF, 0xE9E8E7, 0xF4F3F2, 0x8E4524, 0xFD7B41, 0x1E1008, 0x3C4044, 0x555A5F, 0x7B7C7F, 0x116747, 0xAA2736, 0.12f),
    ),
    Palette.SAPPHIRE to Pair(
        pal(0x0F1C23, 0x14252E, 0x1A2F3A, 0x223B48, 0xE5AEA9, 0xE5AEA9, 0x1A0F0E, 0xF5F5F5, 0xA6A9D0, 0x6F768C, 0x34D399, 0xF3636C, 0.14f),
        pal(0xEEF0F0, 0xE3E6E6, 0xF7F8F8, 0xFFFFFF, 0x35627A, 0x35627A, 0xFFFFFF, 0x17303C, 0x475A63, 0x808B8F, 0x13724F, 0xB52A39, 0.12f),
    ),
    Palette.OPALINE to Pair(
        pal(0x141416, 0x1B1B1E, 0x232326, 0x2D2D31, 0xFF634A, 0xFF634A, 0x1E0A06, 0xF4F4F6, 0xB9B9BE, 0x6F6F72, 0x34D399, 0xF2555F, 0.14f),
        pal(0xECECEE, 0xE2E2E4, 0xF4F4F6, 0xFFFFFF, 0xAB4232, 0xFF634A, 0x1E0A06, 0x1F2023, 0x54555A, 0x87888C, 0x13724F, 0xB52A39, 0.1f),
    ),
    Palette.LEMON to Pair(
        pal(0x10202A, 0x162A36, 0x1D3340, 0x2C4C5C, 0xF3E308, 0xF3E308, 0x121E26, 0xEEF1F2, 0xB8BFC1, 0x707B81, 0x34D399, 0xF46B74, 0.14f),
        pal(0xE8ECED, 0xDCE1E3, 0xF1F4F5, 0xFAFBFB, 0x2C4C5C, 0xF3E308, 0x1B2F3A, 0x1B2F3A, 0x40596A, 0x7B8891, 0x13714E, 0xB52A39, 0.12f),
    ),
    Palette.ARCTIC to Pair(
        pal(0x111D26, 0x172732, 0x1D3140, 0x243C4C, 0x7FA8C2, 0x7FA8C2, 0x0B1820, 0xF4FCFB, 0xACBCBF, 0x6C7A7F, 0x34D399, 0xF46972, 0.14f),
        pal(0xE6EFEF, 0xDAE5E6, 0xF4FCFB, 0xFFFFFF, 0x3F6985, 0x5289AD, 0x0E1B24, 0x243C4C, 0x466070, 0x7C8B93, 0x13724F, 0xB52A39, 0.1f),
    ),
)

/** null = Clasica (usa Theme.kt tal cual). */
internal fun paletteTokens(palette: Palette, dark: Boolean): PaletteTokens? =
    TOKENS[palette]?.let { if (dark) it.first else it.second }

private fun contrast(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Primer color de [options] que se lee (4,5:1 o mas) sobre [bg]; si ninguno llega, el de mas contraste. */
private fun readableOn(bg: Color, options: List<Color>): Color =
    options.firstOrNull { contrast(it, bg) >= 4.5f } ?: options.maxBy { contrast(it, bg) }

/** Acerca [c] a blanco (oscuro) o negro (claro) solo lo justo para que se lea (4,6:1, con margen) sobre todos los [bgs]. */
private fun readable(c: Color, dark: Boolean, bgs: List<Color>): Color {
    val target = if (dark) Color.White else Color.Black
    var out = c
    var i = 0
    while (i < 20 && bgs.any { contrast(out, it) < 4.6f }) { i++; out = lerp(c, target, i * 0.05f) }
    return out
}

/** Sube/baja/neutro son fijos por modo; con otros fondos algunos no llegaban a 4,5:1, asi que se ajustan al fondo y a la tarjeta de la paleta. */
internal fun ChangeColors.readableOn(dark: Boolean, bg: Color, card: Color): ChangeColors {
    val bgs = listOf(bg, card)
    return ChangeColors(readable(up, dark, bgs), readable(down, dark, bgs), readable(neutral, dark, bgs))
}

private fun schemeOf(t: PaletteTokens, dark: Boolean): ColorScheme {
    val onAccent = readableOn(t.accent, listOf(t.onAccent, Color.White, Color.Black))
    val accentContainer = lerp(t.bg, t.accent, if (dark) 0.18f else 0.14f)
    val errorContainer = lerp(t.bg, t.bad, 0.18f)
    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = t.accent,
        onPrimary = onAccent,
        primaryContainer = accentContainer,
        onPrimaryContainer = t.text,
        secondary = t.accent,
        onSecondary = onAccent,
        secondaryContainer = accentContainer,
        onSecondaryContainer = t.text,
        background = t.bg,
        onBackground = t.text,
        surface = t.surface,
        onSurface = t.text,
        surfaceVariant = t.card,
        onSurfaceVariant = t.text2,
        surfaceTint = t.accent,
        surfaceDim = t.bg,
        surfaceBright = t.cardHigh,
        surfaceContainerLowest = t.bg,
        surfaceContainerLow = t.surface,
        surfaceContainer = t.card,
        surfaceContainerHigh = t.cardHigh,
        surfaceContainerHighest = t.cardHigh,
        outline = t.outline,
        outlineVariant = lerp(t.bg, t.outline, 0.3f),
        error = t.bad,
        onError = readableOn(t.bad, listOf(t.bg, Color.White, Color.Black)),
        errorContainer = errorContainer,
        onErrorContainer = t.text,
    )
}

private fun glassOf(t: PaletteTokens, dark: Boolean) = GlassTokens(
    border = t.text.copy(alpha = if (dark) 0.10f else 0.12f),
    borderAccent = t.accent.copy(alpha = if (dark) 0.35f else 0.30f),
    accentSoft = t.brand.copy(alpha = t.soft),
    surfaceGlass = t.text.copy(alpha = if (dark) 0.05f else 0.04f),
)

internal fun colorSchemeFor(palette: Palette, dark: Boolean): ColorScheme =
    paletteTokens(palette, dark)?.let { schemeOf(it, dark) } ?: if (dark) DarkColors else LightColors

internal fun glassFor(palette: Palette, dark: Boolean): GlassTokens =
    paletteTokens(palette, dark)?.let { glassOf(it, dark) } ?: if (dark) DarkGlass else LightGlass
