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

    val Fuel: ImageVector by lazy {
        build(
            "Combustible",
            "M5 21V4a1 1 0 0 1 1-1h7a1 1 0 0 1 1 1v17",
            "M3 21h13M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V9l-3-3",
            "M8 8h3",
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

    private fun build(name: String, vararg paths: String): ImageVector =
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
