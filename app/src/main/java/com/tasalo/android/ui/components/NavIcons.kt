package com.tasalo.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Iconos de la barra inferior: los de la maqueta (trazo de 2, extremos redondeados, cuadricula de 24).
 * El color real lo pone el `tint` del Icon; el negro de aqui solo es el trazo base.
 */
object NavIcons {
    val Rates: ImageVector by lazy { build("Tasas", "M3 17l6-6 4 4 8-8", "M15 7h6v6") }

    /** Billete: rectangulo redondeado, circulo central y dos puntos laterales. */
    val Cash: ImageVector by lazy {
        build(
            "Efectivo",
            roundRect(2.0, 6.0, 20.0, 12.0, 2.0),
            "M15 12a3 3 0 1 1-6 0a3 3 0 1 1 6 0",
            "M6 12h.01M18 12h.01",
        )
    }

    val Calculator: ImageVector by lazy {
        build(
            "Calculadora",
            "M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z",
            "M8 7h8M8 12h2M14 12h2M8 16h2M14 16h2",
        )
    }

    val Blog: ImageVector by lazy { build("Blog", "M6 3h9l4 4v14H6z", "M9 12h7M9 16h7M9 8h3") }

    val Settings: ImageVector by lazy {
        build(
            "Ajustes",
            "M4 7h10M18 7h2M4 17h2M10 17h10",
            "M18 7a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
            "M10 17a2 2 0 1 1-4 0a2 2 0 1 1 4 0",
        )
    }

    val Appearance: ImageVector by lazy {
        build("Apariencia", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 3v18", "M12 7a5 5 0 0 1 0 10")
    }

    val Download: ImageVector by lazy { build("Actualizaciones", "M12 4v12M7 11l5 5 5-5M5 20h14") }

    val Terminal: ImageVector by lazy { build("Diagn\u00F3stico", "M4 6l5 6-5 6M12 18h8") }

    val Chevron: ImageVector by lazy { build("Desplegar", "M6 9l6 6 6-6") }

    /** Rectangulo redondeado como trazo SVG (x, y, ancho, alto, radio). */
    internal fun roundRect(x: Double, y: Double, w: Double, h: Double, r: Double): String =
        "M${x + r} ${y}h${w - 2 * r}a$r $r 0 0 1 $r $r" +
            "v${h - 2 * r}a$r $r 0 0 1 -$r $r" +
            "h-${w - 2 * r}a$r $r 0 0 1 -$r -$r" +
            "v-${h - 2 * r}a$r $r 0 0 1 $r -$r" +
            "z"

    internal fun build(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
