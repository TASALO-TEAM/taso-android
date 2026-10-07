package com.tasalo.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Deslizar a izquierda/derecha entre las opciones de una fila de pestañas (fuentes de tasas, cuentas del blog)
 * sin pelearse con el paginador de la barra principal: el gesto solo se "reclama" si hay una opción en esa
 * dirección. En la primera o la última, el dedo sigue de largo y el paginador cambia de sección.
 *
 * El contenido sigue al dedo (amortiguado). Al soltar pasado el umbral, el contenido actual sale por un lado,
 * cambia la opción y el nuevo entra por el lado contrario con un resorte; si no se llega al umbral, vuelve a su sitio.
 */
@Composable
fun Modifier.edgeAwareSwipe(index: Int, count: Int, onIndexChange: (Int) -> Unit): Modifier {
    val scope = rememberCoroutineScope()
    val onChange by rememberUpdatedState(onIndexChange)
    var dragX by remember { mutableFloatStateOf(0f) }
    var span by remember { mutableFloatStateOf(1f) }

    return this
        // El detector de gestos va por fuera de la capa que se desplaza: así sus coordenadas no se mueven con el contenido.
        .pointerInput(index, count) {
            val threshold = 72.dp.toPx()
            val canNext = index < count - 1
            val canPrev = index > 0
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val width = size.width.toFloat()
                span = width
                var total = 0f
                val shown = { t: Float ->
                    if ((t < 0f && canNext) || (t > 0f && canPrev)) (t * 0.6f).coerceIn(-width * 0.6f, width * 0.6f) else 0f
                }
                val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                    val canHandle = (over < 0f && canNext) || (over > 0f && canPrev)
                    if (canHandle) {
                        change.consume()
                        total += over
                    }
                } ?: return@awaitEachGesture
                dragX = shown(total)
                horizontalDrag(first.id) { change ->
                    total += change.positionChange().x
                    change.consume()
                    dragX = shown(total)
                }
                val target = when {
                    total <= -threshold && canNext -> index + 1
                    total >= threshold && canPrev -> index - 1
                    else -> index
                }
                val from = dragX
                scope.launch {
                    if (target != index) {
                        val exit = (if (target > index) -1f else 1f) * width * 0.6f
                        animate(from, exit, animationSpec = tween(120)) { v, _ -> dragX = v }
                        onChange(target)
                        animate(-exit, 0f, animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)) { v, _ ->
                            dragX = v
                        }
                    } else {
                        animate(from, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) { v, _ ->
                            dragX = v
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            translationX = dragX
            val progress = (abs(dragX) / (span * 0.6f)).coerceIn(0f, 1f)
            alpha = 1f - 0.75f * progress
        }
}
