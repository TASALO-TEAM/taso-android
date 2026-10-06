package com.tasalo.android.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tasalo.android.ui.theme.LocalGlass
import kotlin.math.abs
import kotlin.math.roundToInt

data class IslandItem(val label: String, val icon: ImageVector)

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

    androidx.compose.foundation.layout.Box(
        modifier
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .padding(top = 4.dp, bottom = 8.dp)
            .height(60.dp)
            .clip(shape)
            .background(base)
            .background(sheen)
            .border(1.dp, rim, shape)
            .drawBehind {
                val pad = sidePad.toPx()
                val inset = bubbleInset.toPx()
                val itemW = (size.width - pad * 2) / items.size
                val pos = position().coerceIn(0f, (items.size - 1).toFloat())
                // 0 en reposo, 0,5 a mitad de trayecto: la burbuja se alarga hasta un 30 % mientras viaja.
                val travel = abs(pos - pos.roundToInt())
                val stretch = 1f + 0.30f * (travel * 2f)
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
