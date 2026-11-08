package com.tasalo.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlertNoticesTest {
    private fun msg(id: Long, title: String = "Alerta $id", body: String = "Cuerpo $id") =
        AppMessage(id = id, title = title, body = body, format = "telegram", createdAt = null)

    @Test
    fun `solo cuentan las alertas mas nuevas que la ultima avisada y que la ultima vista`() {
        val all = listOf(msg(5), msg(4), msg(3), msg(2), msg(1))
        assertEquals(listOf(5L, 4L), AlertNotices.pending(all, lastNotifiedId = 3, lastSeenId = 1).map { it.id })
        assertEquals(listOf(5L), AlertNotices.pending(all, lastNotifiedId = 1, lastSeenId = 4).map { it.id })
    }

    @Test
    fun `sin alertas nuevas no hay aviso`() {
        val all = listOf(msg(2), msg(1))
        assertEquals(emptyList<AppMessage>(), AlertNotices.pending(all, lastNotifiedId = 2, lastSeenId = 0))
        assertNull(AlertNotices.notice(emptyList()))
    }

    @Test
    fun `una alerta muestra su titulo y un adelanto limpio`() {
        val notice = AlertNotices.notice(listOf(msg(7, title = "Sube el USD", body = "*El Toque*: el [USD](https://x.cu) sube\na 400")))!!
        assertEquals("Sube el USD", notice.title)
        assertEquals("El Toque: el USD sube a 400", notice.text)
        assertEquals(7L, notice.newestId)
    }

    @Test
    fun `varias alertas se resumen y cubren la mas reciente`() {
        val notice = AlertNotices.notice(listOf(msg(3, title = "Tercera"), msg(9, title = "Novena"), msg(5)))!!
        assertEquals("3 alertas nuevas", notice.title)
        assertEquals("Novena", notice.text)
        assertEquals(9L, notice.newestId)
    }

    @Test
    fun `el adelanto se recorta con puntos suspensivos`() {
        val preview = AlertNotices.preview("a".repeat(300), max = 20)
        assertEquals(20, preview.length)
        assertEquals('\u2026', preview.last())
    }

    @Test
    fun `un aviso ya mostrado no se repite al volver a evaluar`() {
        val all = listOf(msg(4), msg(3))
        val first = AlertNotices.notice(AlertNotices.pending(all, 0, 0))!!
        assertNull(AlertNotices.notice(AlertNotices.pending(all, first.newestId, 0)))
    }
}
