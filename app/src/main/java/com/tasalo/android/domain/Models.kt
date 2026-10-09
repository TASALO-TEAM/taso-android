package com.tasalo.android.domain

import java.time.Instant
import java.time.LocalDate

const val DEFAULT_BASE_URL = "https://tasalo.duckdns.org/"
const val REPO_URL = "https://github.com/TASALO-TEAM/taso-android"

enum class Source(val id: String, val title: String) {
    ELTOQUE("eltoque", "El Toque"),
    QVAPAY("qvapay", "QvaPay"),
    BCC("bcc", "BCC"),
    CADECA("cadeca", "CADECA");

    /** CADECA y QvaPay traen compra y venta por separado (en QvaPay, `rate` es el promedio). */
    val hasBuySell: Boolean get() = this == CADECA || this == QVAPAY

    companion object {
        fun fromId(value: String?): Source? =
            entries.firstOrNull { it.id.equals(value, true) || it.name.equals(value, true) }
    }
}

enum class Change { UP, DOWN, NEUTRAL }

data class Rate(
    val currency: String,
    val rate: Double,
    val buy: Double?,
    val sell: Double?,
    val change: Change,
    val prevRate: Double?,
) {
    val changePct: Double?
        get() = prevRate?.takeIf { it != 0.0 }?.let { (rate - it) / it * 100 }
}

data class RatesSnapshot(val bySource: Map<Source, List<Rate>>, val fetchedAt: Instant)

data class FuelPrice(val key: String, val min: Double?, val max: Double?, val change: Change)

data class FuelSnapshot(val items: List<FuelPrice>, val fetchedAt: Instant)

data class YearState(
    val year: Int,
    val percent: Double,
    val daysLeft: Int,
    val daysPassed: Int,
    val weeksLeft: Int,
    val quote: String?,
    val quoteDate: LocalDate?,
    val fetchedAt: Instant?,
)

data class Snapshot(val rates: RatesSnapshot?, val fuel: FuelSnapshot?, val year: YearState)

/** Mensaje del equipo para la sección Alertas (se publica desde el bot con /msapp). */
data class AppMessage(
    val id: Long,
    val title: String,
    val body: String,
    /** "telegram" (Markdown legacy de Telegram) o "markdown" (estándar). */
    val format: String,
    val createdAt: Instant?,
)

enum class ThemeMode { AUTO, DARK, LIGHT }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.AUTO,
    val defaultSource: Source = Source.ELTOQUE,
    val lastSource: Source? = null,
    val baseUrl: String = DEFAULT_BASE_URL,
    /** false = sube rojo / baja verde (convención de la extensión); true = invertido. */
    val invertColors: Boolean = false,
    /** Monedas ocultas, formato "FUENTE:MONEDA" (ej. "ELTOQUE:BTC"). */
    val hidden: Set<String> = emptySet(),
    /** Preguntar si se quiere enviar un reporte cuando se detecta un cierre inesperado. */
    val crashPrompt: Boolean = true,
    val skippedVersion: String? = null,
    val lastUpdateCheck: Long = 0L,
    /** Minutos entre refrescos en segundo plano; 0 = solo manual. */
    val refreshMinutes: Int = 30,
    val lastBackgroundRefresh: Long = 0L,
    /** Último mensaje de Alertas que el usuario ya vio (para el punto de no leídos). */
    val lastSeenMessageId: Long = 0L,
    /** Usuario de Hive elegido por la persona para la tercera pestaña del Blog (null = ninguno). */
    val blogCustomAccount: String? = null,
    /** true = Tasas en lista (una tarjeta por fila); false = cuadricula de tarjetas. */
    val ratesListView: Boolean = false,
) {
    val source: Source get() = lastSource ?: defaultSource

    fun isHidden(source: Source, currency: String) = "${source.name}:$currency" in hidden
}

data class CurrencyMeta(val name: String, val flag: String)

object Currencies {
    val PREFERRED_ORDER = listOf(
        "EUR", "USD", "MLC", "BTC", "TRX", "USDT",
        "CAD", "GBP", "CHF", "RUB", "AUD", "JPY",
        "MXN", "BRL", "COP",
    )

    val META: Map<String, CurrencyMeta> = mapOf(
        "EUR" to CurrencyMeta("Euro", "🇪🇺"),
        "USD" to CurrencyMeta("Dólar", "🇺🇸"),
        "MLC" to CurrencyMeta("MLC", "💳"),
        "BTC" to CurrencyMeta("Bitcoin", "₿"),
        "TRX" to CurrencyMeta("TRON", "⚡"),
        "USDT" to CurrencyMeta("Tether", "💵"),
        "CAD" to CurrencyMeta("Canadiense", "🇨🇦"),
        "GBP" to CurrencyMeta("Libra", "🇬🇧"),
        "CHF" to CurrencyMeta("Franco Suizo", "🇨🇭"),
        "RUB" to CurrencyMeta("Rublo Ruso", "🇷🇺"),
        "AUD" to CurrencyMeta("Australiano", "🇦🇺"),
        "JPY" to CurrencyMeta("Yen", "🇯🇵"),
        "MXN" to CurrencyMeta("Mexicano", "🇲🇽"),
        "BRL" to CurrencyMeta("Real Brasileño", "🇧🇷"),
        "COP" to CurrencyMeta("Peso Colombiano", "🇨🇴"),
    )

    /** Monedas que la API puede traer pero que no aportan nada en pantalla. */
    val IGNORED = setOf("CUP")

    fun orderIndex(code: String): Int =
        PREFERRED_ORDER.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it }
}

data class FuelMeta(val name: String, val subtype: String, val unit: String)

/** El router de la API descarta unidad/subtipo/nombre, por eso se fijan aquí (ver plan §2.4). */
object Fuel {
    val ORDER = listOf("B-94", "B-90", "B-83", "Petroleo", "Gas_LP")

    val META: Map<String, FuelMeta> = mapOf(
        "B-94" to FuelMeta("Gasolina B-94", "Especial", "CUP/L"),
        "B-90" to FuelMeta("Gasolina B-90", "Regular", "CUP/L"),
        "B-83" to FuelMeta("Gasolina B-83", "Motor", "CUP/L"),
        "Petroleo" to FuelMeta("Petróleo / Diésel", "Diésel", "CUP/L"),
        "Gas_LP" to FuelMeta("Gas licuado", "Balón", "CUP/balón"),
    )
}

/**
 * Metodos de pago de QvaPay P2P. Cada valor de la API es cuanto de ese metodo se obtiene por 1 USD de QvaPay
 * (no todos son CUP por unidad: MLC ronda 1,4 y ZELLE 1,02).
 */
object QvaPay {
    val ORDER = listOf("CUP", "MLC", "TROPIPAY", "ETECSA", "ZELLE", "CLASICA", "BOLSATM", "BANDECPREPAGO", "SBERBANK")

    val META: Map<String, CurrencyMeta> = mapOf(
        "CUP" to CurrencyMeta("Banco CUP", "🏦"),
        "MLC" to CurrencyMeta("Banco MLC", "💳"),
        "TROPIPAY" to CurrencyMeta("Tropipay", "💸"),
        "ETECSA" to CurrencyMeta("Saldo ETECSA", "📱"),
        "ZELLE" to CurrencyMeta("Zelle", "⚡"),
        "CLASICA" to CurrencyMeta("Tarjeta Clásica", "💳"),
        "BOLSATM" to CurrencyMeta("Bolsa TM", "👛"),
        "BANDECPREPAGO" to CurrencyMeta("Bandec Prepago", "💳"),
        "SBERBANK" to CurrencyMeta("Sberbank", "🏦"),
    )

    fun orderIndex(code: String): Int = ORDER.indexOf(code).let { if (it < 0) Int.MAX_VALUE else it }
}

/** Nombre e icono de una moneda segun la fuente: en QvaPay los codigos son metodos de pago, no monedas. */
fun metaFor(source: Source, code: String): CurrencyMeta? =
    if (source == Source.QVAPAY) QvaPay.META[code] ?: Currencies.META[code] else Currencies.META[code]

/** Codigos a ofrecer cuando todavia no hay datos de la fuente. */
fun Source.defaultCodes(): List<String> = if (this == Source.QVAPAY) QvaPay.ORDER else Currencies.PREFERRED_ORDER

/** Titulo en los widgets: en QvaPay aclara la unidad. */
fun Source.widgetTitle(): String = if (this == Source.QVAPAY) "$title · por 1 USD" else title
