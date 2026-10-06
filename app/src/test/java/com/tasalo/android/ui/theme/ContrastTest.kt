package com.tasalo.android.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Protege la legibilidad de la paleta "Quiet Glass" en los dos modos (WCAG 2.x):
 * texto >= 4,5:1 y elementos de interfaz (bordes, iconos) >= 3:1. Una paleta bonita que no se lee
 * es lo que pasó en la 0.6.0, y esta prueba evita repetirlo al tocar colores.
 */
class ContrastTest {

    private fun lin(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: Color): Double = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)

    private fun ratio(fg: Color, bg: Color): Double {
        val a = luminance(fg.compositeOver(bg))
        val b = luminance(bg)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private class Audit(private val mode: String) {
        val failures = mutableListOf<String>()
        fun text(name: String, fg: Color, bg: Color, ratio: Double) {
            if (ratio < 4.5) failures += "$mode: texto «$name» ${"%.2f".format(ratio)}:1 (mínimo 4,5)"
        }
        fun ui(name: String, ratio: Double) {
            if (ratio < 3.0) failures += "$mode: elemento «$name» ${"%.2f".format(ratio)}:1 (mínimo 3)"
        }
    }

    private fun audit(mode: String, s: ColorScheme, glass: GlassTokens, dark: Boolean): List<String> {
        val a = Audit(mode)
        val card = glass.surfaceGlass.compositeOver(s.background)
        fun t(name: String, fg: Color, bg: Color) = a.text(name, fg, bg, ratio(fg, bg))

        t("texto sobre fondo", s.onBackground, s.background)
        t("texto sobre tarjeta", s.onBackground, card)
        t("texto secundario sobre fondo", s.onSurfaceVariant, s.background)
        t("texto secundario sobre tarjeta", s.onSurfaceVariant, card)
        t("acento (enlaces, títulos) sobre fondo", s.primary, s.background)
        t("acento sobre tarjeta", s.primary, card)
        t("texto sobre botón primario", s.onPrimary, s.primary)
        t("texto sobre indicador de la barra", s.onSecondaryContainer, s.secondaryContainer)
        t("texto sobre contenedor de acento", s.onPrimaryContainer, s.primaryContainer)
        t("error sobre fondo", s.error, s.background)
        t("texto sobre contenedor de error", s.onErrorContainer, s.errorContainer)
        t("texto en diálogos", s.onSurface, s.surfaceContainerHigh)
        t("texto secundario en diálogos", s.onSurfaceVariant, s.surfaceContainerHigh)

        // Subida/bajada/neutro: se pintan sobre la tarjeta y sobre el fondo; con colores invertidos son los mismos.
        val c = changeColorsFor(dark, invert = false)
        listOf("sube" to c.up, "baja" to c.down, "sin cambio" to c.neutral).forEach { (n, col) ->
            t("«$n» sobre tarjeta", col, card)
            t("«$n» sobre fondo", col, s.background)
        }

        a.ui("borde/outline sobre fondo", ratio(s.outline, s.background))
        return a.failures
    }

    @Test
    fun modoOscuroSeLee() {
        val fails = audit("oscuro", DarkColors, DarkGlass, dark = true)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test
    fun modoClaroSeLee() {
        val fails = audit("claro", LightColors, LightGlass, dark = false)
        assertTrue(fails.joinToString("\n"), fails.isEmpty())
    }

    @Test
    fun invertirColoresSoloIntercambiaSubeYBaja() {
        for (dark in listOf(true, false)) {
            val normal = changeColorsFor(dark, invert = false)
            val inv = changeColorsFor(dark, invert = true)
            assertTrue(normal.up == inv.down && normal.down == inv.up && normal.neutral == inv.neutral)
        }
    }
}
