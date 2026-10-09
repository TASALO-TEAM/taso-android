package com.tasalo.android.data

import com.tasalo.android.data.parse.HistoryParsers
import com.tasalo.android.data.remote.TasaloApi
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.domain.PriceHistory
import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.Source
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado de pedir una serie: `error` = no se pudo actualizar (si hay `points`, son los guardados). */
data class SeriesResult(val points: List<PricePoint>, val error: Boolean)

/**
 * Historial de precios. Solo consume la API (sin scraping) y guarda en el móvil únicamente series ya reducidas
 * a un precio por día, así que ocupa poco. Dos peticiones distintas, para que la lista de tarjetas siga fluida:
 *  - `summary()`: una sola llamada liviana (30 días, todas las tarjetas) para el fondo.
 *  - `series()`: el historial largo (6 meses) de UNA moneda, solo al abrir el detalle.
 */
class HistoryRepository(
    private val apiFor: (baseUrl: String) -> TasaloApi,
    private val baseUrl: suspend () -> String,
    private val dir: File,
    private val now: () -> Instant = { Instant.now() },
) {
    /** Si el endpoint de resumen no existe (404), no se reintenta en cada refresco de esta sesión. */
    @Volatile private var summaryUnavailable = false

    private fun file(name: String) = File(dir, name)

    private suspend fun readFresh(name: String, ttl: Duration, allowStale: Boolean): Map<String, List<PricePoint>>? =
        withContext(Dispatchers.IO) {
            val f = file(name)
            if (!f.exists()) return@withContext null
            val age = Duration.between(Instant.ofEpochMilli(f.lastModified()), now())
            if (!allowStale && age > ttl) return@withContext null
            runCatching { PriceHistory.decode(f.readText()) }.getOrNull()
        }

    private suspend fun write(name: String, series: Map<String, List<PricePoint>>) {
        withContext(Dispatchers.IO) {
            runCatching {
                dir.mkdirs()
                file(name).writeText(PriceHistory.encode(series))
            }.onFailure { DiagnosticLog.w("Historial", "no se pudo guardar $name", it) }
        }
    }

    /** Último precio diario de cada fuente y moneda (clave `FUENTE:MONEDA`). Vacío si la API aún no lo ofrece. */
    suspend fun summary(force: Boolean = false): Map<String, List<PricePoint>> {
        if (!force) readFresh(SUMMARY_FILE, SUMMARY_TTL, allowStale = false)?.let { return it }
        val stale = readFresh(SUMMARY_FILE, SUMMARY_TTL, allowStale = true).orEmpty()
        if (summaryUnavailable && !force) return stale
        return try {
            val text = apiFor(baseUrl()).historySummary(SUMMARY_DAYS).string()
            val parsed = HistoryParsers.summary(text)
            if (parsed != null) {
                write(SUMMARY_FILE, parsed)
                parsed
            } else {
                stale
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 404) summaryUnavailable = true
            DiagnosticLog.w("Historial", "resumen no disponible (HTTP ${e.code()})", e)
            stale
        } catch (e: Exception) {
            DiagnosticLog.w("Historial", "resumen falló", e)
            stale
        }
    }

    /** Historial de 6 meses de una moneda. Primero el endpoint diario propuesto; si no existe, el actual. */
    suspend fun series(source: Source, currency: String, force: Boolean = false): SeriesResult {
        val name = "serie_${source.name}_${currency.filter { it.isLetterOrDigit() }}.csv"
        val key = "${source.name}:$currency"
        if (!force) readFresh(name, SERIES_TTL, allowStale = false)?.get(key)?.let { return SeriesResult(it, error = false) }
        val stale = readFresh(name, SERIES_TTL, allowStale = true)?.get(key).orEmpty()
        val api = apiFor(baseUrl())
        val fetched = fromDaily(api, source, currency) ?: fromRaw(api, source, currency)
        return if (fetched != null) {
            write(name, mapOf(key to fetched))
            SeriesResult(fetched, error = false)
        } else {
            SeriesResult(stale, error = true)
        }
    }

    private suspend fun fromDaily(api: TasaloApi, source: Source, currency: String): List<PricePoint>? =
        try {
            HistoryParsers.daily(api.historyDaily(source.id, currency, SERIES_DAYS).string())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 404 es lo esperado hasta que exista el endpoint; se pasa al plan B sin ruido.
            null
        }

    private suspend fun fromRaw(api: TasaloApi, source: Source, currency: String): List<PricePoint>? =
        try {
            val raw = HistoryParsers.raw(api.historyRaw(source.id, currency, SERIES_DAYS).string(), source)
            raw?.let { withContext(Dispatchers.Default) { PriceHistory.daily(it) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.w("Historial", "serie de $source/$currency falló", e)
            null
        }

    companion object {
        const val SUMMARY_DAYS = 30
        const val SERIES_DAYS = 180
        private const val SUMMARY_FILE = "resumen.csv"
        private val SUMMARY_TTL: Duration = Duration.ofMinutes(30)
        private val SERIES_TTL: Duration = Duration.ofMinutes(30)
    }
}
