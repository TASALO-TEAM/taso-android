package com.tasalo.android.ui.settings

import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSummaryTest {

    @Test
    fun `apariencia resume tema, vista y colores`() {
        val s = AppSettings(theme = ThemeMode.DARK, ratesListView = false, invertColors = false)
        assertEquals(
            "Oscuro \u00B7 Tarjetas \u00B7 Sube en rojo",
            settingsSummaries(s, "1.0.0", null)[SettingsCategories.APPEARANCE],
        )
    }

    @Test
    fun `apariencia con lista, colores invertidos y tema claro`() {
        val s = AppSettings(theme = ThemeMode.LIGHT, ratesListView = true, invertColors = true)
        assertEquals(
            "Claro \u00B7 Lista \u00B7 Sube en verde",
            settingsSummaries(s, "1.0.0", null)[SettingsCategories.APPEARANCE],
        )
    }

    @Test
    fun `tasas muestra la fuente y cada cuanto se actualiza`() {
        val s = AppSettings(defaultSource = Source.ELTOQUE, refreshMinutes = 30)
        assertEquals(
            "${Source.ELTOQUE.title} \u00B7 actualiza cada 30 min",
            settingsSummaries(s, "1.0.0", null)[SettingsCategories.RATES],
        )
    }

    @Test
    fun `cero minutos significa solo manual`() {
        val s = AppSettings(refreshMinutes = 0)
        assertEquals(
            "${s.defaultSource.title} \u00B7 solo manual",
            settingsSummaries(s, "1.0.0", null)[SettingsCategories.RATES],
        )
    }

    @Test
    fun `actualizaciones avisa de la version nueva solo si existe`() {
        val s = AppSettings()
        assertEquals("Versi\u00F3n 0.12.0 instalada", settingsSummaries(s, "0.12.0", null)[SettingsCategories.UPDATES])
        assertEquals(
            "Versi\u00F3n 0.12.0 instalada \u00B7 nueva 0.13.0",
            settingsSummaries(s, "0.12.0", "0.13.0")[SettingsCategories.UPDATES],
        )
    }

    @Test
    fun `diagnostico refleja si pregunta al detectar un fallo`() {
        assertEquals(
            "Informe de fallos: s\u00ED \u00B7 URL de la API",
            settingsSummaries(AppSettings(crashPrompt = true), "1", null)[SettingsCategories.ADVANCED],
        )
        assertEquals(
            "Informe de fallos: no \u00B7 URL de la API",
            settingsSummaries(AppSettings(crashPrompt = false), "1", null)[SettingsCategories.ADVANCED],
        )
    }
}
