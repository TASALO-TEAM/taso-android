package com.tasalo.android.data

import com.tasalo.android.data.local.CacheStore
import com.tasalo.android.data.parse.Parsers
import com.tasalo.android.data.remote.TasaloApi
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.domain.QuoteRule
import com.tasalo.android.domain.Snapshot
import com.tasalo.android.domain.Source
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException

/**
 * `messages` es opcional: con una API sin el endpoint (404) falla en silencio y no cuenta para `allOk`,
 * así no aparecen avisos de error por algo que la app puede no necesitar.
 */
data class RefreshResult(val rates: Boolean, val fuel: Boolean, val year: Boolean, val messages: Boolean = true) {
    val anyOk: Boolean get() = rates || fuel || year
    val allOk: Boolean get() = rates && fuel && year
}

private const val MESSAGES_LIMIT = 20

class TasaloRepository(
    private val cache: CacheStore,
    private val apiFor: (baseUrl: String) -> TasaloApi,
    private val baseUrl: suspend () -> String,
    private val now: () -> Instant = { Instant.now() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    private val retryDelayMs: Long = 800,
) {
    val raw: Flow<CacheStore.Raw> = cache.raw

    suspend fun snapshot(at: Instant = now()): Snapshot = Snapshots.from(cache.current(), at, zone())

    /**
     * Lanza /latest, /year/state y /fuel en paralelo con fallo independiente
     * (equivale al Promise.allSettled de la extensión): si uno falla, los otros se guardan igual.
     */
    suspend fun refreshAll(): RefreshResult = coroutineScope {
        val api = apiFor(baseUrl())
        val rates = async { safe { refreshRates(api) } }
        val year = async { safe { refreshYear(api) } }
        val fuel = async { safe { refreshFuel(api) } }
        val messages = async { safe { refreshMessages(api) } }
        RefreshResult(rates = rates.await(), fuel = fuel.await(), year = year.await(), messages = messages.await())
    }

    private suspend fun refreshRates(api: TasaloApi): Boolean {
        val text = fetch { api.latest().string() }
        if (text != null && Parsers.latest(text) != null) {
            cache.saveLatest(text, now())
            return true
        }
        val combined = fallbackLatest(api) ?: return false
        cache.saveLatest(combined, now())
        return true
    }

    /** Si /latest falla, pide las fuentes por separado y combina las que respondan. */
    private suspend fun fallbackLatest(api: TasaloApi): String? = coroutineScope {
        val eltoque = async { fetch { api.eltoque().string() }?.let(Parsers::ratesObject) }
        val bcc = async { fetch { api.bcc().string() }?.let(Parsers::ratesObject) }
        val cadeca = async { fetch { api.cadeca().string() }?.let(Parsers::ratesObject) }
        val qvapay = async { fetch { api.qvapay().string() }?.let(Parsers::ratesObject) }
        val results: Map<Source, JsonObject?> = mapOf(
            Source.ELTOQUE to eltoque.await(),
            Source.BCC to bcc.await(),
            Source.QVAPAY to qvapay.await(),
            Source.CADECA to cadeca.await(),
        )
        if (results.values.all { it == null }) return@coroutineScope null
        val json = buildJsonObject {
            put("ok", true)
            putJsonObject("data") {
                for ((source, rates) in results) if (rates != null) put(source.id, rates)
            }
        }.toString()
        json.takeIf { Parsers.latest(it) != null }
    }

    private suspend fun refreshFuel(api: TasaloApi): Boolean {
        val text = fetch { api.fuel().string() } ?: return false
        if (Parsers.fuel(text) == null) return false
        cache.saveFuel(text, now())
        return true
    }

    private suspend fun refreshMessages(api: TasaloApi): Boolean {
        val text = fetch { api.appMessages(MESSAGES_LIMIT).string() } ?: return false
        if (Parsers.messages(text) == null) return false
        cache.saveMessages(text, now())
        return true
    }

    private suspend fun refreshYear(api: TasaloApi): Boolean {
        val text = fetch { api.yearState().string() } ?: return false
        val parsed = Parsers.year(text) ?: return false
        val stored = cache.current()
        val today = now().atZone(zone()).toLocalDate()
        val (quote, quoteDate) = QuoteRule.resolve(stored.quote, stored.quoteDate, parsed.quote, today)
        cache.saveYear(text, now(), quote, quoteDate)
        return true
    }

    /** Un reintento con backoff corto ante error de red o 5xx; los 4xx no se reintentan. */
    private suspend fun fetch(call: suspend () -> String): String? {
        try {
            return withRetry(call)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.w("Red", "petición falló", e)
            return null
        }
    }

    private suspend fun <T> withRetry(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() in 400..499) throw e
        } catch (e: IOException) {
            // se reintenta abajo
        }
        delay(retryDelayMs)
        return block()
    }

    private suspend fun safe(block: suspend () -> Boolean): Boolean =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticLog.e("Datos", "actualización falló", e)
            false
        }
}
