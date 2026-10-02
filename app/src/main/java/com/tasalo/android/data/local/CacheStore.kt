package com.tasalo.android.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Caché en DataStore: JSON crudo por recurso + timestamp del último fetch exitoso de la app.
 * Los widgets y la UI leen de aquí; nunca llaman a la red (plan §3).
 */
class CacheStore(private val store: DataStore<Preferences>) {

    data class Raw(
        val latestJson: String? = null,
        val latestAt: Instant? = null,
        val fuelJson: String? = null,
        val fuelAt: Instant? = null,
        val yearJson: String? = null,
        val yearAt: Instant? = null,
        val quote: String? = null,
        val quoteDate: LocalDate? = null,
        val releasesJson: String? = null,
        val releasesAt: Instant? = null,
    )

    private object Keys {
        val LATEST_JSON = stringPreferencesKey("latest_json")
        val LATEST_AT = longPreferencesKey("latest_at")
        val FUEL_JSON = stringPreferencesKey("fuel_json")
        val FUEL_AT = longPreferencesKey("fuel_at")
        val YEAR_JSON = stringPreferencesKey("year_json")
        val YEAR_AT = longPreferencesKey("year_at")
        val QUOTE = stringPreferencesKey("quote_text")
        val QUOTE_DATE = stringPreferencesKey("quote_date")
        val RELEASES_JSON = stringPreferencesKey("releases_json")
        val RELEASES_AT = longPreferencesKey("releases_at")
    }

    val raw: Flow<Raw> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            Raw(
                latestJson = p[Keys.LATEST_JSON],
                latestAt = p[Keys.LATEST_AT]?.let(Instant::ofEpochMilli),
                fuelJson = p[Keys.FUEL_JSON],
                fuelAt = p[Keys.FUEL_AT]?.let(Instant::ofEpochMilli),
                yearJson = p[Keys.YEAR_JSON],
                yearAt = p[Keys.YEAR_AT]?.let(Instant::ofEpochMilli),
                quote = p[Keys.QUOTE],
                quoteDate = p[Keys.QUOTE_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                releasesJson = p[Keys.RELEASES_JSON],
                releasesAt = p[Keys.RELEASES_AT]?.let(Instant::ofEpochMilli),
            )
        }

    suspend fun current(): Raw = raw.first()

    suspend fun saveLatest(json: String, at: Instant) {
        store.edit {
            it[Keys.LATEST_JSON] = json
            it[Keys.LATEST_AT] = at.toEpochMilli()
        }
    }

    suspend fun saveReleases(json: String, at: Instant) {
        store.edit {
            it[Keys.RELEASES_JSON] = json
            it[Keys.RELEASES_AT] = at.toEpochMilli()
        }
    }

    suspend fun saveFuel(json: String, at: Instant) {
        store.edit {
            it[Keys.FUEL_JSON] = json
            it[Keys.FUEL_AT] = at.toEpochMilli()
        }
    }

    suspend fun saveYear(json: String, at: Instant, quote: String?, quoteDate: LocalDate?) {
        store.edit {
            it[Keys.YEAR_JSON] = json
            it[Keys.YEAR_AT] = at.toEpochMilli()
            if (quote != null && quoteDate != null) {
                it[Keys.QUOTE] = quote
                it[Keys.QUOTE_DATE] = quoteDate.toString()
            }
        }
    }
}
