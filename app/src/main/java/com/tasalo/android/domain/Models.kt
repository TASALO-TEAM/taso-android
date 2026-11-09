package com.tasalo.android.domain

import java.time.Instant
import java.time.LocalDate

const val DEFAULT_BASE_URL = "https://tasalo.duckdns.org/"
const val REPO_URL = "https://github.com/TASALO-TEAM/taso-android"

enum class Source(val id: String, val title: String) {
    ELTOQUE("eltoque", "El Toque"),
    QVAPAY("qvapay", "QvaPay"),
    BCC("bcc", "BCC");

    /** QvaPay trae compra y venta por separado (`rate` es el promedio). */
    val hasBuySell: Boolean get() = this == QVAPAY

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

data class Snapshot(val rates: RatesSnapshot?, val year: YearState)

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

/** Paletas de color elegibles en Ajustes (las mismas de taso-inv). `swatches` solo pinta la vista previa del selector. */
enum class Palette(val id: String, val title: String, val description: String, val swatches: List<Long>) {
    CLASICA("clasica", "Cl\u00E1sica", "Verde azulado sobre azul pizarra: la de siempre.", listOf(0xFF0E1621, 0xFF16212F, 0xFF3DD6C0, 0xFFE8EEF5)),
    SOFT("soft", "Soft Glass", "Marfil y carb\u00F3n c\u00E1lido con el amarillo de marca.", listOf(0xFFEDEBE6, 0xFFFBF9F4, 0xFFF2C94C, 0xFF14130F)),
    OCEANO("oceano", "\u00D3ceano", "Azules apagados, de la noche al humo.", listOf(0xFF06141B, 0xFF11212D, 0xFF253745, 0xFF9BA8AB, 0xFFCCD0CF)),
    AMBER("amber", "Amber Walnut Morning", "Nogal y arcilla sobre un gris verdoso.", listOf(0xFFEBEFEE, 0xFFCCB499, 0xFFC8906D, 0xFFBB6C43, 0xFF4A413C)),
    CALCITE("calcite", "Calcite", "Gris c\u00E1lido con naranja vivo y durazno.", listOf(0xFFDDDCDB, 0xFFFD7B41, 0xFFEDBF9B, 0xFF3C4044)),
    SAPPHIRE("sapphire", "Sapphire Ash Morning", "Azul zafiro con rosa, lavanda y ceniza.", listOf(0xFF35627A, 0xFFE5AEA9, 0xFFB46258, 0xFFA6A9D0, 0xFFF5F5F5, 0xFF8E9A98)),
    OPALINE("opaline", "Opaline", "Grises perlados con un toque de coral.", listOf(0xFFF4F4F6, 0xFFE7E7E7, 0xFFD2D2D4, 0xFFFF634A)),
    LEMON("lemon", "Lemon Granite Morning", "Lim\u00F3n sobre granito y azul petr\u00F3leo.", listOf(0xFFF3E308, 0xFFB8BFC1, 0xFF6C8494, 0xFF2C4C5C)),
    ARCTIC("arctic", "Arctic Reflection", "Azules de hielo con blanco polar.", listOf(0xFF5289AD, 0xFF243C4C, 0xFFACBCBF, 0xFFF4FCFB, 0xFF698696));

    companion object {
        fun fromId(value: String?): Palette? = entries.firstOrNull { it.id == value }
    }
}

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
    /** Paleta de color elegida en Ajustes; la Clásica es la de siempre. */
    val palette: Palette = Palette.CLASICA,
    /** Avisar en el telefono cuando el bot publica una alerta nueva. Apagado hasta que la persona lo active. */
    val alertNotifications: Boolean = false,
    /** Ultima alerta ya avisada en el telefono, para no repetir. */
    val lastNotifiedMessageId: Long = 0L,
    /** Ya se le pregunto (o ya eligio) lo de los avisos de alertas: no se vuelve a preguntar. */
    val alertPromptDone: Boolean = false,
    /** Avisar en el telefono de los pedidos de la tienda (taso-inv). Activo por defecto: solo hace algo con sesion en la Tienda. */
    val orderNotifications: Boolean = true,
    /** `creado` (segundos) del ultimo aviso de pedido ya mostrado; 0 = aun sin punto de partida. */
    val lastNotifiedOrder: Long = 0L,
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

/**
 * Metodos de pago de QvaPay P2P. Cada valor de la API es cuanto de ese metodo se obtiene por 1 USD de QvaPay
 * (no todos son CUP por unidad: MLC ronda 1,4 y ZELLE 1,02).
 */
object QvaPay {
    val ORDER = listOf("CUP", "CUPCASH", "MLC", "TROPIPAY", "ETECSA", "ZELLE", "CLASICA", "BOLSATM", "BANDECPREPAGO", "SBERBANK")

    val META: Map<String, CurrencyMeta> = mapOf(
        "CUP" to CurrencyMeta("Banco CUP", "🏦"),
        "CUPCASH" to CurrencyMeta("CUP Cash", "💵"),
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
