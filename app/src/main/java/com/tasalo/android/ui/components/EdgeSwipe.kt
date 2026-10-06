package com.tasalo.android.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/**
 * Deslizar a izquierda/derecha entre las opciones de una fila de pestañas (fuentes de tasas, cuentas del blog)
 * sin pelearse con el paginador de la barra principal: el gesto solo se "reclama" si hay una opción en esa
 * dirección. En la primera o la última, el dedo sigue de largo y el paginador cambia de sección.
 */
fun Modifier.edgeAwareSwipe(index: Int, count: Int, onIndexChange: (Int) -> Unit): Modifier =
    pointerInput(index, count) {
        val threshold = 72.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var total = 0f
            val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                val canHandle = (over < 0f && index < count - 1) || (over > 0f && index > 0)
                if (canHandle) {
                    change.consume()
                    total += over
                }
            } ?: return@awaitEachGesture
            horizontalDrag(first.id) { change ->
                total += change.positionChange().x
                change.consume()
            }
            val target = when {
                total <= -threshold -> index + 1
                total >= threshold -> index - 1
                else -> index
            }
            if (target != index && target in 0 until count) onIndexChange(target)
        }
    }
