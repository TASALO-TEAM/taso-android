package com.tasalo.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tasalo.android.ui.theme.LocalGlass
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

data class IslandItem(val label: String, val icon: ImageVector)

/** Altura que ocupa la cápsula flotante (con sus márgenes y la barra de navegación): el contenido la suma abajo. */
val LocalBottomBarInset = compositionLocalOf { 0.dp }

/** Relleno de las listas: 16 dp alrededor y, abajo, el hueco de la cápsula flotante para que no tape el final. */
@Composable
fun floatingContentPadding(top: androidx.compose.ui.unit.Dp = 16.dp): PaddingValues = PaddingValues(
    start = 16.dp,
    top = top,
    end = 16.dp,
    bottom = 16.dp + LocalBottomBarInset.current,
)

/**
 * Barra inferior tipo "isla" flotante con aspecto de cristal líquido: cápsula translúcida con borde de luz,
 * brillo superior y una burbuja que sigue al dedo mientras se desliza entre secciones y se estira un poco
 * en el trayecto (efecto líquido). Es solo Compose, sin dependencias: no desenfoca lo que hay detrás.
 *
 * [position] devuelve la posición continua (página + desplazamiento) y se lee al dibujar, así que mover la
 * burbuja no recompone nada.
 */
@Composable
fun GlassIslandBar(
    items: List<IslandItem>,
    selected: Int,
    position: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(32.dp)

    val base = scheme.surfaceContainerHigh.copy(alpha = if (dark) 0.84f else 0.82f)
    val sheen = Brush.verticalGradient(
        listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.55f), Color.Transparent),
    )
    val rim = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.30f else 0.95f),
            glass.border,
            Color.White.copy(alpha = if (dark) 0.10f else 0.50f),
        ),
    )
    val bubbleFill = glass.accentSoft
    val bubbleRim = glass.borderAccent
    val sidePad = 4.dp
    val bubbleInset = 5.dp

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val currentPosition by rememberUpdatedState(position)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Posición de la burbuja mientras el dedo la arrastra (null = manda el paginador).
    var dragPos by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    // 0 en reposo, 1 mientras se arrastra: la burbuja crece un poco, como si la levantaras.
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "islandLift",
    )

    androidx.compose.foundation.layout.Box(
        modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 8.dp)
            .height(60.dp)
            // Flota sobre el contenido: sombra suave (el recorte va después para que no la corte).
            .shadow(
                elevation = 14.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.50f else 0.16f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.60f else 0.26f),
            )
            .clip(shape)
            .background(base)
            .background(sheen)
            .border(1.dp, rim, shape)
            .pointerInput(items.size) {
                val pad = sidePad.toPx()
                val itemW = (size.width - pad * 2) / items.size
                val last = (items.size - 1).toFloat()
                // Al soltar, la burbuja se asienta con un resorte en la sección más cercana y el paginador la sigue.
                val finish = {
                    dragging = false
                    val from = dragPos
                    if (from != null) {
                        val target = from.roundToInt().coerceIn(0, items.size - 1)
                        currentOnSelect(target)
                        scope.launch {
                            animate(
                                initialValue = from,
                                targetValue = target.toFloat(),
                                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                            ) { value, _ -> dragPos = value }
                            dragPos = null
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragPos = currentPosition().coerceIn(0f, last)
                        dragging = true
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        dragPos = ((dragPos ?: 0f) + dx / itemW).coerceIn(0f, last)
                    },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                )
            }
            .drawBehind {
                val pad = sidePad.toPx()
                val raised = lift.coerceIn(0f, 1.2f)
                val inset = bubbleInset.toPx() * (1f - 0.4f * raised)
                val itemW = (size.width - pad * 2) / items.size
                val pos = (dragPos ?: position()).coerceIn(0f, (items.size - 1).toFloat())
                // 0 en reposo, 0,5 a mitad de trayecto: la burbuja se alarga hasta un 30 % mientras viaja.
                val travel = abs(pos - pos.roundToInt())
                val stretch = (1f + 0.30f * (travel * 2f)) * (1f + 0.08f * raised)
                val w = (itemW - inset * 2) * stretch
                val h = size.height - inset * 2
                val left = pad + (pos + 0.5f) * itemW - w / 2f
                val radius = CornerRadius(h / 2f, h / 2f)
                drawRoundRect(bubbleFill, Offset(left, inset), Size(w, h), radius)
                drawRoundRect(bubbleRim, Offset(left, inset), Size(w, h), radius, style = Stroke(1.dp.toPx()))
            },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = sidePad)) {
            items.forEachIndexed { index, item ->
                IslandTab(
                    item = item,
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun IslandTab(item: IslandItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "islandTint",
    )
    Column(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Solo icono: la etiqueta queda como descripción para lectores de pantalla (TalkBack).
        Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(26.dp))
    }
}
