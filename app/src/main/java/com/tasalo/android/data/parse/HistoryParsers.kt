package com.tasalo.android.data.parse

import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.RawPoint
import com.tasalo.android.domain.Source
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Parseo tolerante del historial (mismo criterio que `Parsers`): un campo raro se ignora, un JSON corrupto
 * devuelve null y nunca lanza. Los puntos sin fecha o sin precio se descartan.
 *
 * Contrato propuesto para la API (ver docs/plans/2026-10-08-historial-precios.md):
 *  - `GET /api/v1/tasas/history/summary?days=30` -> `{ok, tz, data: {<fuente>: {<MONEDA>: [{date, rate}]}}}`
 *  - `GET /api/v1/tasas/history/daily?source=&currency=&days=` -> `{ok, tz, data: [{date, rate}]}`
 * Mientras no existan, el detalle usa el endpoint actual `GET /api/v1/tasas/history` (lecturas sueltas).
 */
object HistoryParsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun root(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun isOk(o: JsonObject) = (o["ok"] as? JsonPrimitive)?.booleanOrNull == true

    private fun str(e: JsonElement?): String? = (e as? JsonPrimitive)?.contentOrNull

    private fun point(e: JsonElement?): PricePoint? {
        val o = e as? JsonObject ?: return null
        val date = runCatching { LocalDate.parse(str(o["date"])?.take(10).orEmpty()) }.getOrNull() ?: return null
        val rate = Parsers.number(o["rate"]) ?: return null
        return PricePoint(date, rate)
    }

    private fun points(e: JsonElement?): List<PricePoint> =
        (e as? JsonArray)?.mapNotNull(::point).orEmpty().distinctBy { it.date }.sortedBy { it.date }

    /** Resumen de todas las tarjetas: clave `FUENTE:MONEDA` (ej. `ELTOQUE:USD`). */
    fun summary(text: String): Map<String, List<PricePoint>>? {
        val r = root(text) ?: return null
        if (!isOk(r)) return null
        val data = r["data"] as? JsonObject ?: return null
        val out = LinkedHashMap<String, List<PricePoint>>()
        for (source in Source.entries) {
            val currencies = data[source.id] as? JsonObject ?: continue
            for ((currency, list) in currencies) {
                val parsed = points(list)
                if (parsed.isNotEmpty()) out["${source.name}:$currency"] = parsed
            }
        }
        return out
    }

    /** Serie diaria de una fuente y moneda. */
    fun daily(text: String): List<PricePoint>? {
        val r = root(text) ?: return null
        if (!isOk(r)) return null
        if (r["data"] !is JsonArray) return null
        return points(r["data"])
    }

    /** Endpoint actual (`/history`): lecturas sueltas con `buy_rate`, `sell_rate` y `fetched_at`. */
    fun raw(text: String, source: Source): List<RawPoint>? {
        val r = root(text) ?: return null
        if (!isOk(r)) return null
        val data = r["data"] as? JsonArray ?: return null
        return data.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val at = Parsers.instant(str(o["fetched_at"])) ?: return@mapNotNull null
            val buy = Parsers.number(o["buy_rate"])
            val sell = Parsers.number(o["sell_rate"])
            // QvaPay: el precio mostrado es el promedio de compra y venta; en el resto manda la venta (o el único valor).
            val rate = if (source == Source.QVAPAY && buy != null && sell != null) (buy + sell) / 2 else sell ?: buy
            rate?.takeIf { it > 0.0 }?.let { RawPoint(at, it) }
        }
    }
}
