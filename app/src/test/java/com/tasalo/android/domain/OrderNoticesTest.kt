package com.tasalo.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderNoticesTest {
    private fun aviso(creado: Long, tipo: String = "pedido_estado", leida: Boolean = false, texto: String = "Aviso $creado") =
        OrderAviso(tipo, texto, creado, leida)

    @Test
    fun `lee la respuesta de api avisos y descarta lo raro`() {
        val json = """{"no_leidas":1,"items":[
            {"id":"a1","tipo":"pedido_estado","texto":"Tu pedido ahora esta: Listo","enlace":"tienda:pedidos","creado":1700000100,"leida":false},
            {"id":"a2","tipo":"pedido_nuevo","texto":"Llego un pedido nuevo","creado":1700000200,"leida":true},
            {"id":"a3","texto":"sin tipo","creado":1},
            "basura"
        ]}"""
        val items = OrderNotices.parse(json)!!
        assertEquals(listOf(1700000100L, 1700000200L), items.map { it.creado })
        assertEquals(listOf(false, true), items.map { it.leida })
    }

    @Test
    fun `una respuesta que no es valida no avisa nada`() {
        assertNull(OrderNotices.parse("no es json"))
        assertNull(OrderNotices.parse("""{"ok":true}"""))
    }

    @Test
    fun `solo cuentan pedidos sin leer y mas nuevos que el ultimo avisado`() {
        val all = listOf(
            aviso(10), aviso(20), aviso(30, leida = true), aviso(40, tipo = "otro"), aviso(50),
        )
        assertEquals(listOf(50L, 20L), OrderNotices.pending(all, lastNotified = 10).map { it.creado })
        assertEquals(emptyList<OrderAviso>(), OrderNotices.pending(all, lastNotified = 50))
    }

    @Test
    fun `uno muestra su texto y varios un resumen con el mas reciente`() {
        val one = OrderNotices.notice(listOf(aviso(7, texto = "Tu pedido ahora esta: Entregado")))!!
        assertEquals("Pedido de la tienda", one.title)
        assertEquals("Tu pedido ahora esta: Entregado", one.text)
        assertEquals(7L, one.newestId)

        val many = OrderNotices.notice(listOf(aviso(9, texto = "B"), aviso(3, texto = "A")))!!
        assertEquals("2 avisos de pedidos", many.title)
        assertEquals("B", many.text)
        assertEquals(9L, many.newestId)
        assertNull(OrderNotices.notice(emptyList()))
    }

    @Test
    fun `la linea base es el aviso mas nuevo o 1 si no hay ninguno`() {
        assertEquals(40L, OrderNotices.baseline(listOf(aviso(10), aviso(40, leida = true))))
        assertEquals(1L, OrderNotices.baseline(emptyList()))
    }

    @Test
    fun `los usuarios nuevos tambien cuentan y tienen su propio titulo`() {
        val user = { c: Long -> aviso(c, tipo = "usuario_nuevo", texto = "Nuevo ingreso con Google: ana") }
        assertEquals(listOf(5L), OrderNotices.pending(listOf(user(5), user(2)), lastNotified = 3).map { it.creado })

        val one = OrderNotices.notice(listOf(user(5)))!!
        assertEquals("Nuevo usuario", one.title)
        assertEquals("Nuevo ingreso con Google: ana", one.text)

        assertEquals("2 usuarios nuevos", OrderNotices.notice(listOf(user(5), user(6)))!!.title)
        assertEquals("2 avisos de TASALO inv", OrderNotices.notice(listOf(user(5), aviso(6)))!!.title)
        assertEquals("2 avisos de pedidos", OrderNotices.notice(listOf(aviso(5), aviso(6)))!!.title)
    }
}
