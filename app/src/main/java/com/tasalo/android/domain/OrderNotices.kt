package com.tasalo.android.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Un aviso de `GET /api/avisos` de taso-inv. `creado` son segundos Unix; el `id` de taso-inv es texto y no crece, no se usa. */
data class OrderAviso(val tipo: String, val texto: String, val creado: Long, val leida: Boolean)

/**
 * Decide que avisos de pedidos de taso-inv se muestran en el telefono. Puro (sin Android) para probarlo en la JVM.
 * Un aviso cuenta si es de pedidos, no esta leido en la web y es mas nuevo que el ultimo ya avisado.
 * shortcut: se compara por `creado` (segundos); dos avisos del mismo segundo que el ultimo avisado se perderian, usar el id si molesta.
 */
object OrderNotices {
    private val TYPES = setOf("pedido_estado", "pedido_nuevo", "usuario_nuevo")
    // taso-inv solo crea `usuario_nuevo` para admins, asi que no hace falta mirar el rol aqui.
    private const val NEW_USER = "usuario_nuevo"
    private val json = Json { ignoreUnknownKeys = true }

    /** null = respuesta que no es valida (no se avisa nada). Un aviso raro se descarta sin tumbar el resto. */
    fun parse(text: String): List<OrderAviso>? {
        val items = runCatching { (json.parseToJsonElement(text) as? JsonObject)?.get("items") as? JsonArray }.getOrNull() ?: return null
        return items.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val tipo = (o["tipo"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val texto = (o["texto"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val creado = (o["creado"] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: return@mapNotNull null
            OrderAviso(tipo, texto, creado, leida = (o["leida"] as? JsonPrimitive)?.booleanOrNull ?: false)
        }
    }

    /**
     * Punto de partida la primera vez con sesion: lo que ya habia no cuenta. Sin avisos vale 1 (0 = «aun sin punto de partida»),
     * asi todo lo que llegue despues cuenta sin depender de que el reloj del telefono coincida con el del servidor.
     */
    fun baseline(items: List<OrderAviso>): Long = items.maxOfOrNull { it.creado } ?: 1L

    fun pending(items: List<OrderAviso>, lastNotified: Long): List<OrderAviso> =
        items.filter { it.tipo in TYPES && !it.leida && it.creado > lastNotified }.sortedByDescending { it.creado }

    /** null = nada que avisar. Uno: su texto; varios: un resumen con el texto del mas reciente. `newestId` = su `creado`. */
    fun notice(pending: List<OrderAviso>): AlertNotice? {
        val newest = pending.maxByOrNull { it.creado } ?: return null
        val onlyUsers = pending.all { it.tipo == NEW_USER }
        val noUsers = pending.none { it.tipo == NEW_USER }
        return when {
            pending.size == 1 -> AlertNotice(if (onlyUsers) "Nuevo usuario" else "Pedido de la tienda", newest.texto, newest.creado)
            onlyUsers -> AlertNotice("${pending.size} usuarios nuevos", newest.texto, newest.creado)
            noUsers -> AlertNotice("${pending.size} avisos de pedidos", newest.texto, newest.creado)
            else -> AlertNotice("${pending.size} avisos de TASALO inv", newest.texto, newest.creado)
        }
    }
}
