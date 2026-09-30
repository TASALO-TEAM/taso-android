package com.tasalo.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.DEFAULT_BASE_URL
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.UrlValidator
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Ajustes del usuario, en un DataStore separado del caché (plan §4.3). */
class SettingsStore(private val store: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DEFAULT_SOURCE = stringPreferencesKey("default_source")
        val LAST_SOURCE = stringPreferencesKey("last_source")
        val BASE_URL = stringPreferencesKey("base_url")
        val INVERT = booleanPreferencesKey("invert_colors")
        val HIDDEN = stringSetPreferencesKey("hidden_currencies")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                theme = p[Keys.THEME]?.let { v -> ThemeMode.entries.firstOrNull { it.name == v } } ?: ThemeMode.AUTO,
                defaultSource = Source.fromId(p[Keys.DEFAULT_SOURCE]) ?: Source.ELTOQUE,
                lastSource = Source.fromId(p[Keys.LAST_SOURCE]),
                baseUrl = p[Keys.BASE_URL]?.let(UrlValidator::normalize) ?: DEFAULT_BASE_URL,
                invertColors = p[Keys.INVERT] ?: false,
                hidden = p[Keys.HIDDEN] ?: emptySet(),
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[Keys.THEME] = mode.name }
    }

    /** Cambiar la fuente por defecto descarta la "última elegida" para que el cambio se note. */
    suspend fun setDefaultSource(source: Source) {
        store.edit {
            it[Keys.DEFAULT_SOURCE] = source.name
            it.remove(Keys.LAST_SOURCE)
        }
    }

    suspend fun setLastSource(source: Source) {
        store.edit { it[Keys.LAST_SOURCE] = source.name }
    }

    suspend fun setInvertColors(value: Boolean) {
        store.edit { it[Keys.INVERT] = value }
    }

    /** Devuelve false (sin guardar) si la URL no es https válida. */
    suspend fun setBaseUrl(input: String): Boolean {
        val url = UrlValidator.normalize(input) ?: return false
        store.edit { it[Keys.BASE_URL] = url }
        return true
    }

    suspend fun resetBaseUrl() {
        store.edit { it.remove(Keys.BASE_URL) }
    }

    suspend fun setCurrencyVisible(source: Source, currency: String, visible: Boolean) {
        val key = "${source.name}:$currency"
        store.edit {
            val current = it[Keys.HIDDEN] ?: emptySet()
            it[Keys.HIDDEN] = if (visible) current - key else current + key
        }
    }
}
