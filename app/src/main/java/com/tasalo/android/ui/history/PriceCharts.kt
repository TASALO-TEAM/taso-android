package com.tasalo.android.ui.history

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasalo.android.domain.PricePoint
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Posiciones normalizadas (0..1) de cada punto: x según la fecha (los huecos se ven), y según el rango de precios. */
private fun layout(points: List<PricePoint>): List<Pair<Float, Float>> {
    val firstDate = points.first().date
    val span = ChronoUnit.DAYS.between(firstDate, points.last().date).toFloat()
    val min = points.minOf { it.rate }
    val max = points.maxOf { it.rate }
    val range = (max - min).toFloat()
    return points.map { p ->
        val x = if (span <= 0f) 0.5f else ChronoUnit.DAYS.between(firstDate, p.date) / span
        // Serie plana: línea en el centro, sin amplificar ruido.
        val y = if (range <= 0f) 0.5f else ((p.rate - min).toFloat() / range)
        x to y
    }
}

/**
 * Fondo de la tarjeta: tendencia reciente muy tenue y difuminada. Es solo decoración (sin semántica para
 * TalkBack); el precio y el cambio ya van en el texto. Con menos de 2 puntos no dibuja nada.
 */
@Composable
fun SparklineBackground(points: List<PricePoint>, color: Color, modifier: Modifier = Modifier) {
    if (points.size < 2) return
    val shape = remember(points) { layout(points) }
    // `blur` solo actúa en Android 12+; en versiones anteriores queda el trazo suave sin desenfoque.
    Canvas(modifier.blur(3.dp)) {
        val topPad = size.height * 0.34f
        val bottomPad = size.height * 0.10f
        val h = size.height - topPad - bottomPad
        val coords = shape.map { (x, y) -> Offset(x * size.width, topPad + (1f - y) * h) }
        val line = Path().apply {
            moveTo(coords.first().x, coords.first().y)
            for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(coords.last().x, size.height)
            lineTo(coords.first().x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
        drawPath(line, color.copy(alpha = 0.14f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(line, color.copy(alpha = 0.50f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Gráfico del detalle. Tocar o deslizar el dedo muestra el punto más cercano (`onScrub` avisa cuál; null al soltar).
 * Los días sin dato se unen con trazo discontinuo para que el hueco se note y no parezca un dato inventado.
 * Con 1 punto dibuja solo el punto; con 0, el llamador muestra un mensaje en su lugar.
 */
@Composable
fun PriceChart(
    points: List<PricePoint>,
    color: Color,
    gridColor: Color,
    description: String,
    onScrub: (PricePoint?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    if (points.isEmpty()) return
    val shape = remember(points) { layout(points) }
    var active by remember(points) { mutableStateOf<Int?>(null) }
    // Entrada suave: la curva se "dibuja" de izquierda a derecha al cargar o cambiar de rango.
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) }

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun nearest(x: Float): Int {
                        val nx = (x / size.width).coerceIn(0f, 1f)
                        return shape.indices.minBy { abs(shape[it].first - nx) }
                    }
                    active = nearest(down.position.x)
                    onScrub(points[active!!])
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        active = nearest(change.position.x)
                        onScrub(points[active!!])
                        change.consume()
                    }
                    active = null
                    onScrub(null)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val padX = 8.dp.toPx()
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padTop - padBottom
            val coords = shape.map { (x, y) -> Offset(padX + x * w, padTop + (1f - y) * h) }

            // Tres guías horizontales tenues.
            for (i in 0..2) {
                val y = padTop + h * i / 2f
                drawLine(gridColor, Offset(padX, y), Offset(padX + w, y), strokeWidth = 1.dp.toPx())
            }

            clipRect(right = padX + w * reveal.value + 2.dp.toPx()) {
                if (coords.size == 1) {
                    drawCircle(color, 5.dp.toPx(), coords.first())
                } else {
                    drawSeries(points, coords, color, bottom = size.height)
                }
            }

            active?.let { i ->
                val c = coords[i]
                drawLine(color.copy(alpha = 0.5f), Offset(c.x, padTop), Offset(c.x, padTop + h), strokeWidth = 1.dp.toPx())
                drawCircle(Color.Black.copy(alpha = 0.35f), 8.dp.toPx(), c)
                drawCircle(color, 6.dp.toPx(), c)
                drawCircle(Color.White, 2.5f.dp.toPx(), c)
            }
        }
    }
}

private fun DrawScope.drawSeries(points: List<PricePoint>, coords: List<Offset>, color: Color, bottom: Float) {
    val solid = Path()
    val dashed = Path()
    var penDown = false
    for (i in coords.indices) {
        val gap = i > 0 && ChronoUnit.DAYS.between(points[i - 1].date, points[i].date) > 1
        if (i == 0) {
            solid.moveTo(coords[i].x, coords[i].y)
            penDown = true
        } else if (gap) {
            dashed.moveTo(coords[i - 1].x, coords[i - 1].y)
            dashed.lineTo(coords[i].x, coords[i].y)
            solid.moveTo(coords[i].x, coords[i].y)
        } else if (penDown) {
            solid.lineTo(coords[i].x, coords[i].y)
        }
    }
    val area = Path().apply {
        moveTo(coords.first().x, coords.first().y)
        for (i in 1 until coords.size) lineTo(coords[i].x, coords[i].y)
        lineTo(coords.last().x, bottom)
        lineTo(coords.first().x, bottom)
        close()
    }
    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
    drawPath(dashed, color.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
    drawPath(solid, color, style = Stroke(2.5f.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}
