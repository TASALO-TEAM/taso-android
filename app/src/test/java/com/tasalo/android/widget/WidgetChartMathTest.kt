package com.tasalo.android.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetChartMathTest {

    @Test
    fun `el bitmap no pasa del lado maximo y conserva la proporcion`() {
        // 400x100 dp a densidad 3 = 1200x300 px -> se reduce a 360x90.
        assertEquals(Pair(360, 90), WidgetChartMath.bitmapSize(400f, 100f, 3f))
    }

    @Test
    fun `un area pequena no se amplia`() {
        assertEquals(Pair(120, 60), WidgetChartMath.bitmapSize(60f, 30f, 2f))
    }

    @Test
    fun `nunca devuelve un bitmap diminuto o vacio`() {
        assertEquals(Pair(16, 16), WidgetChartMath.bitmapSize(0f, 0f, 2f))
    }

    @Test
    fun `la variacion lleva signo y coma decimal`() {
        assertEquals("+2,5%", WidgetChartMath.pctText(2.5))
        assertEquals("+0,0%", WidgetChartMath.pctText(0.0))
    }

    @Test
    fun `sin dato no hay texto`() {
        assertEquals("", WidgetChartMath.pctText(null))
        assertEquals("", WidgetChartMath.pctText(Double.NaN))
    }
}
