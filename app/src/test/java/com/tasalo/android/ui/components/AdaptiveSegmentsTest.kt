package com.tasalo.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSegmentsTest {

    @Test
    fun `con espacio de sobra queda una fila con check`() {
        val plan = planSegments(availableDp = 360f, count = 3, widestDp = 40f)
        assertEquals(SegmentPlan(columns = 3, showIcon = true, singleLine = true), plan)
    }

    @Test
    fun `si no cabe el check se quita antes de cambiar de distribucion`() {
        // 4 segmentos en 328 dp: 82 dp por celda; etiqueta de 52 dp + holgura 4 + relleno 24 = 80 dp (sin check).
        val plan = planSegments(availableDp = 328f, count = 4, widestDp = 52f)
        assertEquals(4, plan.columns)
        assertFalse(plan.showIcon)
        assertTrue(plan.singleLine)
    }

    @Test
    fun `con letra grande pasa a dos por fila y luego a una`() {
        val two = planSegments(availableDp = 328f, count = 4, widestDp = 90f)
        assertEquals(2, two.columns)
        assertTrue(two.singleLine)

        val one = planSegments(availableDp = 328f, count = 4, widestDp = 200f)
        assertEquals(1, one.columns)
        assertTrue(one.singleLine)
    }

    @Test
    fun `si una etiqueta sola no cabe en una linea se permite el salto y no se corta`() {
        val plan = planSegments(availableDp = 328f, count = 3, widestDp = 400f)
        assertEquals(SegmentPlan(columns = 1, showIcon = false, singleLine = false), plan)
    }

    @Test
    fun `sin opciones no falla`() {
        val plan = planSegments(availableDp = 328f, count = 0, widestDp = 0f)
        assertEquals(1, plan.columns)
    }
}
