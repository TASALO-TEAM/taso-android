package com.tasalo.android.data.parse

import com.tasalo.android.domain.AppMessage
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.Fuel
import com.tasalo.android.domain.FuelPrice
import com.tasalo.android.domain.Rate
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.YearApi
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Parseo tolerante (plan §2.5). Trabaja sobre JsonElement en lugar de DTOs rígidos:
 * cualquier campo raro se ignora y un JSON corrupto devuelve null, nunca lanza.
 */
object Parsers {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun root(text: String): JsonObject? =
        try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        }

    private fun isOk(o: JsonObject): Boolean = (o["ok"] as? JsonPrimitive)?.booleanOrNull == true

    /** Acepta número, o string con coma decimal ("365,5") y miles ("1.234,5"). */
    fun number(e: JsonElement?): Double? {
        val p = e as? JsonPrimitive ?: return null
        val raw = p.contentOrNull?.trim()?.replace(" ", "") ?: return null
        if (raw.isEmpty() || raw.equals("null", true)) return null
        val normalized = when {
            raw.contains('.') && raw.contains(',') -> raw.replace(".", "").replace(',', '.')
            raw.contains(',') -> raw.replace(',', '.')
            else -> raw
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun change(e: JsonElement?): Change =
        when ((e as? JsonPrimitive)?.contentOrNull?.lowercase()) {
            "up" -> Change.UP
            "down" -> Change.DOWN
            else -> Change.NEUTRAL
        }

    /** CADECA puede no traer `rate`: rate = sell ?: buy; sin ninguno de los tres se descarta. */
    fun rate(code: String, e: JsonElement?): Rate? {
        val o = e as? JsonObject ?: return null
        val buy = number(o["buy"])
        val sell = number(o["sell"])
        val rate = number(o["rate"]) ?: sell ?: buy ?: return null
        return Rate(code, rate, buy, sell, change(o["change"]), number(o["prev_rate"]))
    }

    fun sortRates(list: List<Rate>): List<Rate> =
        list.sortedWith(compareBy<Rate>({ Currencies.orderIndex(it.currency) }, { it.currency }))

    /** Fuente vacía, ausente o `null` = lista vacía, no error. */
    fun source(e: JsonElement?): List<Rate> {
        val o = e as? JsonObject ?: return emptyList()
        return sortRates(
            o.entries.mapNotNull { (code, value) ->
                if (code.uppercase() in Currencies.IGNORED) null else rate(code, value)
            },
        )
    }

    /** `/tasas/latest` -> ok == true && data != null; si no, null. */
    fun latest(text: String): Map<Source, List<Rate>>? {
        val r = root(text) ?: return null
        if (!isOk(r)) return null
        val data = r["data"] as? JsonObject ?: return null
        return Source.entries.associateWith { source(data[it.id]) }
    }

    /** Endpoints por fuente (`{source, rates, updated_at}` sin `ok`): devuelve el objeto `rates`. */
    fun ratesObject(text: String): JsonObject? = root(text)?.get("rates") as? JsonObject

    /** `/tasas/fuel` -> exige `rates`; devuelve solo claves conocidas, en el orden fijo de la UI. */
    fun fuel(text: String): List<FuelPrice>? {
        val rates = ratesObject(text) ?: return null
        return Fuel.ORDER.mapNotNull { key ->
            val o = rates[key] as? JsonObject ?: return@mapNotNull null
            val buy = number(o["buy"])
            val sell = number(o["sell"])
            val rate = number(o["rate"])
            var min = buy ?: rate ?: sell
            var max = sell ?: rate ?: buy
            if (min == null && max == null) return@mapNotNull null
            if (min != null && max != null && min > max) {
                val t = min; min = max; max = t
            }
            FuelPrice(key, min, max, change(o["change"]))
        }
    }

    /** `/year/state` -> exige ok == true. Solo se usan progress.* y quote.quote. */
    fun year(text: String): YearApi? {
        val r = root(text) ?: return null
        if (!isOk(r)) return null
        val progress = r["progress"] as? JsonObject
        val quote = (r["quote"] as? JsonObject)?.get("quote")
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return YearApi(
            year = number(progress?.get("year"))?.toInt(),
            percent = number(progress?.get("percent")),
            daysLeft = number(progress?.get("days_left"))?.toInt(),
            quote = quote,
        )
    }

    /** Fecha ISO de FastAPI: con zona ("...+00:00" o "Z") o sin ella (se toma como UTC). */
    fun instant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    /**
     * `/app/messages` -> exige ok == true y `data` como lista. Los mensajes sin id, título o cuerpo
     * se descartan; devuelve los más nuevos primero.
     */
    fun messages(text: String): List<AppMessage>? {
        val r = root(text) ?: return null
        if (!isOk(r)) return null
        val data = r["data"] as? JsonArray ?: return null
        return data.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val id = number(o["id"])?.toLong() ?: return@mapNotNull null
            val title = (o["title"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            val body = (o["body"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
            if (title.isEmpty() || body.isEmpty()) return@mapNotNull null
            val format = (o["format"] as? JsonPrimitive)?.contentOrNull?.lowercase()
            AppMessage(
                id = id,
                title = title,
                body = body,
                format = if (format == "markdown") "markdown" else "telegram",
                createdAt = instant((o["created_at"] as? JsonPrimitive)?.contentOrNull),
            )
        }.sortedByDescending { it.id }
    }
}
