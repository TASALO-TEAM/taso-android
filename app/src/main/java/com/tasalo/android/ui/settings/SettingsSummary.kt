package com.tasalo.android.ui.settings

import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.ThemeMode

/** Titulos de las categorias de Ajustes (tambien sirven de clave para la que esta abierta). */
object SettingsCategories {
    const val APPEARANCE = "Apariencia"
    const val RATES = "Tasas"
    const val UPDATES = "Actualizaciones"
    const val ADVANCED = "Diagn\u00F3stico y avanzado"
}

/**
 * Linea de resumen que se ve en cada categoria cerrada. Funcion pura (sin Android) para probarla en JVM.
 * `newVersion` es la version publicada mas reciente cuando hay una por instalar; null si no la hay.
 */
fun settingsSummaries(settings: AppSettings, version: String, newVersion: String?): Map<String, String> {
    val theme = when (settings.theme) {
        ThemeMode.AUTO -> "Auto"
        ThemeMode.DARK -> "Oscuro"
        ThemeMode.LIGHT -> "Claro"
    }
    val view = if (settings.ratesListView) "Lista" else "Tarjetas"
    val colors = if (settings.invertColors) "Sube en verde" else "Sube en rojo"
    val refresh = if (settings.refreshMinutes == 0) "solo manual" else "actualiza cada ${settings.refreshMinutes} min"
    val installed = "Versi\u00F3n $version instalada" + if (newVersion != null) " \u00B7 nueva $newVersion" else ""
    val crash = if (settings.crashPrompt) "s\u00ED" else "no"
    return mapOf(
        SettingsCategories.APPEARANCE to "$theme \u00B7 $view \u00B7 $colors",
        SettingsCategories.RATES to "${settings.defaultSource.title} \u00B7 $refresh",
        SettingsCategories.UPDATES to installed,
        SettingsCategories.ADVANCED to "Informe de fallos: $crash \u00B7 URL de la API",
    )
}
