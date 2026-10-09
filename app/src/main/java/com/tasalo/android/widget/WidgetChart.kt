package com.tasalo.android.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.ui.unit.DpSize
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.PriceHistory
import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode

/** Matematica pura de los graficos de widget (sin Android): se prueba en JVM. */
object WidgetChartMath {
    /** Lado maximo del bitmap: RemoteViews limita la memoria de imagenes y varios widgets suman. */
    const val MAX_SIDE = 360

    /** Tamano en pixeles del bitmap para un area en dp, conservando la proporcion y sin pasar de MAX_SIDE. */
    fun bitmapSize(widthDp: Float, heightDp: Float, density: Float): Pair<Int, Int> {
        val w = (widthDp * density).coerceAtLeast(1f)
        val h = (heightDp * density).coerceAtLeast(1f)
        val k = minOf(1f, MAX_SIDE / maxOf(w, h))
        return Pair((w * k).toInt().coerceAtLeast(16), (h * k).toInt().coerceAtLeast(16))
    }

    /** Variacion con signo y coma decimal ("+2,5%"); vacio si no hay dato. */
    fun pctText(pct: Double?): String =
        if (pct == null || !pct.isFinite()) "" else String.format(java.util.Locale.forLanguageTag("es-ES"), "%+.1f%%", pct)
}

/**
 * Graficos de los widgets. Glance no tiene lienzo propio: la curva se dibuja en un Bitmap y se muestra como imagen.
 * Los widgets solo leen el resumen de 30 dias que ya esta en el movil (nunca usan la red).
 */
object WidgetChart {
    // Mismos colores que WidgetPalette (dia / noche), como enteros ARGB para pintar en Canvas.
    private const val UP_DAY = 0xFFBC2C3C.toInt()
    private const val UP_NIGHT = 0xFFF2555F.toInt()
    private const val DOWN_DAY = 0xFF13724F.toInt()
    private const val DOWN_NIGHT = 0xFF34D399.toInt()
    private const val DIM_DAY = 0xFF4F6470.toInt()
    private const val DIM_NIGHT = 0xFF9FB0C3.toInt()

    fun isNight(context: Context, theme: ThemeMode): Boolean = when (theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.AUTO ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Color de la curva segun la tendencia y la convencion sube/baja de Ajustes. */
    fun colorFor(change: Change, invert: Boolean, night: Boolean): Int {
        val up = if (night) UP_NIGHT else UP_DAY
        val down = if (night) DOWN_NIGHT else DOWN_DAY
        return when (change) {
            Change.UP -> if (invert) down else up
            Change.DOWN -> if (invert) up else down
            Change.NEUTRAL -> if (night) DIM_NIGHT else DIM_DAY
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    /**
     * Curva (y relleno opcional que se desvanece hacia abajo). X por fecha, asi un dia sin dato se ve como tramo recto
     * y no se inventa ningun valor. Devuelve null con menos de 2 puntos.
     */
    fun sparkline(
        points: List<PricePoint>,
        widthPx: Int,
        heightPx: Int,
        color: Int,
        density: Float,
        fill: Boolean,
        inset: Float,
        strokeDp: Float,
        strokeAlpha: Int,
        fillAlpha: Int,
    ): Bitmap? {
        if (points.size < 2 || widthPx < 8 || heightPx < 8) return null
        val sorted = points.sortedBy { it.date }
        val x0 = sorted.first().date.toEpochDay().toDouble()
        val xSpan = (sorted.last().date.toEpochDay() - sorted.first().date.toEpochDay()).toDouble().coerceAtLeast(1.0)
        val lo = sorted.minOf { it.rate }
        val hi = sorted.maxOf { it.rate }
        val flat = (hi - lo) < 1e-9
        val ySpan = if (flat) 1.0 else hi - lo
        val padX = widthPx * inset
        val padY = heightPx * inset
        val bottom = heightPx - padY

        fun px(p: PricePoint): Float = (padX + (p.date.toEpochDay() - x0) / xSpan * (widthPx - 2 * padX)).toFloat()
        fun py(p: PricePoint): Float =
            if (flat) heightPx / 2f else (bottom - (p.rate - lo) / ySpan * (heightPx - 2 * padY)).toFloat()

        val line = Path()
        sorted.forEachIndexed { i, p -> if (i == 0) line.moveTo(px(p), py(p)) else line.lineTo(px(p), py(p)) }

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (fill) {
            val area = Path(line)
            area.lineTo(px(sorted.last()), bottom)
            area.lineTo(px(sorted.first()), bottom)
            area.close()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f, padY, 0f, bottom,
                withAlpha(color, fillAlpha), withAlpha(color, 0),
                Shader.TileMode.CLAMP,
            )
            canvas.drawPath(area, paint)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
        stroke.style = Paint.Style.STROKE
        stroke.strokeJoin = Paint.Join.ROUND
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.strokeWidth = strokeDp * density
        stroke.color = withAlpha(color, strokeAlpha)
        canvas.drawPath(line, stroke)
        return bitmap
    }

    private fun seriesOf(data: WidgetData, source: Source, currency: String): List<PricePoint> =
        data.history["${source.name}:$currency"].orEmpty()

    /** Fondo del widget: curva de 30 dias muy tenue, dentro de un margen para no chocar con las esquinas. */
    fun backdrop(context: Context, data: WidgetData, source: Source, currency: String, size: DpSize, strong: Boolean = false): Bitmap? {
        val points = seriesOf(data, source, currency)
        if (points.size < 2) return null
        val density = context.resources.displayMetrics.density
        val (w, h) = WidgetChartMath.bitmapSize(size.width.value, size.height.value, density)
        val color = colorFor(PriceHistory.trend(points), data.settings.invertColors, isNight(context, data.settings.theme))
        return sparkline(
            points, w, h, color, density,
            fill = true, inset = 0.08f, strokeDp = 2f,
            strokeAlpha = if (strong) 200 else 120,
            fillAlpha = if (strong) 110 else 70,
        )
    }

    /** Curva pequena para una fila de lista (sin relleno). */
    fun rowSpark(context: Context, data: WidgetData, source: Source, currency: String, widthDp: Float, heightDp: Float): Bitmap? {
        val points = seriesOf(data, source, currency)
        if (points.size < 2) return null
        val density = context.resources.displayMetrics.density
        val (w, h) = WidgetChartMath.bitmapSize(widthDp, heightDp, density)
        val color = colorFor(PriceHistory.trend(points), data.settings.invertColors, isNight(context, data.settings.theme))
        return sparkline(points, w, h, color, density, fill = false, inset = 0.1f, strokeDp = 1.5f, strokeAlpha = 255, fillAlpha = 0)
    }
}
