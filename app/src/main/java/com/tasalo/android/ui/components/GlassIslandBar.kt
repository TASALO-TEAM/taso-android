package com.tasalo.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasalo.android.ui.theme.LocalGlass
import com.tasalo.android.ui.theme.LocalReduceMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * Barra inferior tipo "isla" flotante con aspecto de cristal líquido: cápsula translúcida con borde de luz y una
 * burbuja de alto completo que lleva el icono y el nombre de la sección activa. Es solo Compose, sin dependencias:
 * no desenfoca lo que hay detrás.
 *
 * La burbuja no copia la posición del paginador: la persiguen dos resortes, uno por borde. El borde que va delante
 * es rápido y el de atrás más lento y con algo de rebote, así que la burbuja se estira al viajar y se recoge al
 * llegar (efecto gota). Sigue al dedo mientras se arrastra, y en un salto largo (de la primera a la última sección)
 * recorre todo el trayecto en vez de teletransportarse. Con «quitar animaciones» se coloca de golpe.
 *
 * [position] devuelve la posición continua (página + desplazamiento) y se lee fuera de la composición, así que
 * mover la burbuja solo vuelve a dibujar, no recompone.
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
    val sidePad = 2.dp
    val bubbleInsetV = 4.dp
    val bubbleInsetH = 2.dp
    val last = (items.size - 1).toFloat()

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val currentPosition by rememberUpdatedState(position)
    val currentOnSelect by rememberUpdatedState(onSelect)
    // Posición de la burbuja mientras el dedo la arrastra o se asienta (null = manda el paginador).
    var dragPos by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // 0 en reposo, 1 mientras se arrastra: la burbuja crece un poco, como si la levantaras.
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "islandLift",
    )

    // Borde izquierdo y derecho de la burbuja, en «posición de sección» (0 = primera). En reposo valen lo mismo.
    val leftEdge = remember { Animatable(selected.toFloat()) }
    val rightEdge = remember { Animatable(selected.toFloat()) }
    LaunchedEffect(reduce, items.size) {
        snapshotFlow { (dragPos ?: currentPosition()).coerceIn(0f, last) }.collectLatest { target ->
            if (reduce) {
                leftEdge.snapTo(target)
                rightEdge.snapTo(target)
            } else {
                val goingRight = target >= (leftEdge.value + rightEdge.value) / 2f
                coroutineScope {
                    launch { rightEdge.animateTo(target, spring(0.78f, if (goingRight) 900f else 320f)) }
                    launch { leftEdge.animateTo(target, spring(0.78f, if (goingRight) 320f else 900f)) }
                }
            }
        }
    }

    Box(
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
            .pointerInput(items.size, reduce) {
                val pad = sidePad.toPx()
                val itemW = (size.width - pad * 2) / items.size
                // Al soltar, la burbuja se queda en la sección más cercana (el resorte de arriba la asienta) y el
                // paginador la alcanza; solo entonces se le devuelve el mando.
                val finish = {
                    dragging = false
                    val from = dragPos
                    if (from != null) {
                        val target = from.roundToInt().coerceIn(0, items.size - 1)
                        dragPos = target.toFloat()
                        currentOnSelect(target)
                        settleJob?.cancel()
                        settleJob = scope.launch {
                            withTimeoutOrNull(1500L) {
                                snapshotFlow { abs(currentPosition() - target) }.first { it < 0.01f }
                            }
                            if (!dragging) dragPos = null
                            if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        settleJob?.cancel()
                        dragPos = ((leftEdge.value + rightEdge.value) / 2f).coerceIn(0f, last)
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
                val insetV = bubbleInsetV.toPx() * (1f - 0.35f * raised)
                val grow = 2.dp.toPx() * raised
                val itemW = (size.width - pad * 2) / items.size
                val a = leftEdge.value.coerceIn(-0.05f, last + 0.05f)
                val b = rightEdge.value.coerceIn(-0.05f, last + 0.05f)
                val halfW = (itemW - bubbleInsetH.toPx() * 2) / 2f
                val left = pad + (minOf(a, b) + 0.5f) * itemW - halfW - grow
                val right = pad + (maxOf(a, b) + 0.5f) * itemW + halfW + grow
                // Al viajar se estira (lo hacen los dos resortes) y se achata un poco, como una gota.
                val spread = abs(b - a).coerceAtMost(1f)
                val h = (size.height - insetV * 2) * (1f - 0.06f * spread)
                val top = (size.height - h) / 2f
                val radius = CornerRadius(h / 2f, h / 2f)
                val topLeft = Offset(left, top)
                val bubble = Size(right - left, h)
                drawRoundRect(bubbleFill, topLeft, bubble, radius)
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = if (dark) 0.10f else 0.30f), Color.Transparent),
                        startY = top,
                        endY = top + h,
                    ),
                    topLeft,
                    bubble,
                    radius,
                )
                drawRoundRect(bubbleRim, topLeft, bubble, radius, style = Stroke(1.dp.toPx()))
            },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = sidePad).selectableGroup()) {
            items.forEachIndexed { index, item ->
                IslandTab(
                    item = item,
                    selected = index == selected,
                    // 1 cuando la burbuja está justo sobre esta pestaña y 0 al alejarse: el nombre aparece y se va con ella.
                    progress = { (1f - abs((leftEdge.value + rightEdge.value) / 2f - index) * 1.6f).coerceIn(0f, 1f) },
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun IslandTab(
    item: IslandItem,
    selected: Boolean,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val reduce = LocalReduceMotion.current
    // Se tiñe cuando la burbuja está sobre ella (también mientras se arrastra), no solo cuando el paginador ya cambió.
    val currentProgress by rememberUpdatedState(progress)
    val under by remember { derivedStateOf { currentProgress() >= 0.5f } }
    val tint by animateColorAsState(
        targetValue = if (under) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = if (reduce) snap() else tween(180),
        label = "islandTint",
    )
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    // El nombre solo se ve con letra normal (con letra muy grande no cabría sin recortarse): queda el icono.
    val showLabel = LocalDensity.current.fontScale <= 1.15f
    Box(
        modifier
            .fillMaxHeight()
            // Foco visible para teclado o control por interruptores (sin indicación de pulsado, la burbuja ya marca la activa).
            .then(if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp)) else Modifier)
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // El icono lleva siempre el nombre para TalkBack; el texto visible queda fuera de la accesibilidad (no se dice dos veces).
        // Sube un poco al llegar la burbuja para dejar sitio al nombre, sin saltar.
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier
                .size(24.dp)
                .graphicsLayer { translationY = if (showLabel) -7.dp.toPx() * progress() else 0f },
        )
        if (showLabel) {
            // Si el nombre no cabe en el ancho de la pestaña, baja la letra de medio en medio punto; nunca se recorta.
            var labelSp by remember(item.label) { mutableFloatStateOf(10f) }
            Text(
                item.label,
                color = tint,
                fontSize = labelSp.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { if (it.didOverflowWidth && labelSp > 7f) labelSp -= 0.5f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 5.dp, end = 5.dp, bottom = 10.dp)
                    .graphicsLayer { alpha = progress() }
                    .clearAndSetSemantics { },
            )
        }
    }
}
