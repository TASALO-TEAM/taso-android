package com.tasalo.android.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

/** Iconos de las redes del equipo (los de la maqueta): trazo de 2, extremos redondeados, cuadricula de 24. */
object SocialIcons {
    val Telegram: ImageVector by lazy { NavIcons.build("Telegram", "M21 3L10 14M21 3l-7 18-4-7-7-4z") }

    val Code: ImageVector by lazy { NavIcons.build("C\u00F3digo", "M8 7l-5 5 5 5M16 7l5 5-5 5") }

    val Article: ImageVector by lazy { NavIcons.build("Blog", "M6 3h9l4 4v14H6z", "M9 12h7M9 16h7M9 8h3") }

    val Mail: ImageVector by lazy { NavIcons.build("Correo", NavIcons.roundRect(3.0, 5.0, 18.0, 14.0, 2.0), "M3 7l9 6 9-6") }
}
