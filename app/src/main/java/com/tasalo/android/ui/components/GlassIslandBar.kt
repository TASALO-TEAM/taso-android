package com.tasalo.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
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
 * La burbuja va pegada al paginador, sin resortes propios: su sitio y su estiramiento salen de la posición continua
 * de las secciones, así que se mueve exactamente a la vez que el contenido (dedo, toque o salto) y llega con él.
 * Al viajar entre dos secciones el borde de delante adelanta al de atrás (efecto gota). Solo cuando la mueve la propia
 * barra (arrastrarla con el dedo o tocar una sección lejana, donde el paginador salta) usa el mismo resorte que el
 * paginador y le devuelve el mando al llegar. Con «quitar animaciones» se coloca de golpe.
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
    compact: Boolean = false,
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
    // Posición de la burbuja cuando la mueve la propia barra (dedo o salto largo); si [ovrOn] es falso manda el paginador.
    var ovr by remember { mutableFloatStateOf(0f) }
    var ovrOn by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var travelJob by remember { mutableStateOf<Job?>(null) }
    val pos: () -> Float = { (if (ovrOn) ovr else currentPosition()).coerceIn(0f, last) }
    // 0 en reposo, 1 mientras se arrastra: la burbuja crece un poco, como si la levantaras.
    val lift by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "islandLift",
    )

    // Lleva la burbuja a [target] con el mismo resorte que el paginador y, cuando éste ha llegado, le devuelve el mando.
    val travelTo: (Int) -> Unit = { target ->
        travelJob?.cancel()
        if (!ovrOn) ovr = pos()
        ovrOn = true
        travelJob = scope.launch {
            if (reduce) {
                ovr = target.toFloat()
            } else {
                animate(ovr, target.toFloat(), animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) { v, _ -> ovr = v }
            }
            withTimeoutOrNull(600L) { snapshotFlow { abs(currentPosition() - target) }.first { it < 0.02f } }
            if (!dragging) ovrOn = false
        }
    }

    val compactAmount by animateFloatAsState(
        targetValue = if (compact) 1f else 0f,
        animationSpec = if (reduce) snap() else spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "islandCompact",
    )

    Box(
        modifier
            // Compacta (al bajar por una lista): se encoge y baja un poco hacia el borde, anclada abajo. Solo dibujo, no cambia el tamaño medido.
            .graphicsLayer {
                val k = compactAmount
                val s = 1f - 0.14f * k
                scaleX = s
                scaleY = s
                translationY = 6.dp.toPx() * k
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
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
                // Al soltar, la burbuja se asienta en la sección más cercana con el resorte del paginador.
                val finish = {
                    dragging = false
                    if (ovrOn) {
                        val target = ovr.roundToInt().coerceIn(0, items.size - 1)
                        currentOnSelect(target)
                        travelTo(target)
                        if (!reduce) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
                detectHorizontalDragGestures(
                    onDragStart = {
                        travelJob?.cancel()
                        ovr = pos()
                        ovrOn = true
                        dragging = true
                    },
                    onHorizontalDrag = { change, dx ->
                        change.consume()
                        ovr = (ovr + dx / itemW).coerceIn(0f, last)
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
                // Entre dos secciones el borde de delante adelanta al de atrás (a = izquierdo, b = derecho, centrados en p).
                val p = pos()
                val cell = floor(p)
                val f = p - cell
                val stretch = f * (1f - f)
                val a = cell + f - stretch
                val b = cell + f + stretch
                val halfW = (itemW - bubbleInsetH.toPx() * 2) / 2f
                val left = pad + (a + 0.5f) * itemW - halfW - grow
                val right = pad + (b + 0.5f) * itemW + halfW + grow
                // Al viajar se estira y se achata un poco, como una gota.
                val spread = (b - a).coerceAtMost(0.5f)
                val h = (size.height - insetV * 2) * (1f - 0.12f * spread)
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
                    progress = { (1f - abs(pos() - index) * 1.6f).coerceIn(0f, 1f) },
                    onClick = {
                        // Un salto largo no recorre las secciones del medio: el paginador salta junto al destino y la
                        // burbuja viaja entera con el mismo resorte, para no teletransportarse.
                        if (!reduce && abs(index - selected) > 1) travelTo(index)
                        onSelect(index)
                    },
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
