package com.tasalo.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Margen bajo la fila fija: sirve de degradado para que el contenido se esconda detrás sin un corte seco. */
private val FadeBottom = 8.dp

/**
 * Estado del encabezado colapsable de Tasas y Blog.
 *
 * La fila del título (nombre + campana/actualizar) se esconde al bajar y reaparece en cuanto se sube,
 * aunque se esté a mitad de la lista; la fila fija (fuentes o cuentas) sube hasta ocupar su sitio y se queda.
 * Es un único contenedor de scroll: la lista de debajo sigue siendo la única que desplaza.
 *
 * [offsetPx] va de 0 (título visible) a -[titleHeightPx] (título escondido).
 */
@Stable
class CollapsingHeaderState(private val reduceMotion: Boolean) {
    var titleHeightPx by mutableFloatStateOf(0f)
    var pinnedHeightPx by mutableFloatStateOf(0f)
    var offsetPx by mutableFloatStateOf(0f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y != 0f) {
                offsetPx = (offsetPx + available.y).coerceIn(-titleHeightPx, 0f)
            }
            // No se consume nada: el encabezado solo observa, la lista desplaza igual.
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            settle()
            return Velocity.Zero
        }
    }

    /** Al soltar, el título no se queda a medias: se asienta con un resorte en el extremo más cercano. */
    private suspend fun settle() {
        if (titleHeightPx <= 0f) return
        val target = if (offsetPx > -titleHeightPx / 2f) 0f else -titleHeightPx
        if (offsetPx == target) return
        if (reduceMotion) {
            offsetPx = target
        } else {
            animate(
                initialValue = offsetPx,
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offsetPx = value }
        }
    }
}

@Composable
fun rememberCollapsingHeaderState(): CollapsingHeaderState {
    val context = LocalContext.current
    return remember {
        // Con las animaciones del sistema desactivadas, el encabezado cambia de estado sin animar.
        val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        CollapsingHeaderState(reduceMotion = scale == 0f)
    }
}

/** Alto total del encabezado con todo visible: la lista lo usa como relleno superior para arrancar justo debajo. */
@Composable
fun collapsingHeaderInset(state: CollapsingHeaderState): Dp =
    with(LocalDensity.current) { (state.titleHeightPx + state.pinnedHeightPx).toDp() } + FadeBottom

/**
 * Encabezado que flota sobre la lista (colócalo después de ella en un `Box`). [title] se colapsa;
 * [pinned] se queda arriba. El fondo es translúcido y se desvanece abajo, así el contenido pasa por detrás.
 */
@Composable
fun CollapsingHeader(
    state: CollapsingHeaderState,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    pinned: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = 0.94f),
                        0.82f to background.copy(alpha = 0.94f),
                        1f to background.copy(alpha = 0f),
                    ),
                )
            }
            .padding(bottom = FadeBottom),
    ) {
        Box(
            Modifier
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val full = placeable.height.toFloat()
                    if (state.titleHeightPx != full) state.titleHeightPx = full
                    val offset = state.offsetPx.coerceIn(-full, 0f).roundToInt()
                    val visible = (placeable.height + offset).coerceAtLeast(0)
                    layout(placeable.width, visible) { placeable.placeRelative(0, offset) }
                }
                .graphicsLayer {
                    val full = state.titleHeightPx
                    alpha = if (full > 0f) (1f + state.offsetPx / full).coerceIn(0f, 1f) else 1f
                },
        ) { title() }
        Box(Modifier.onSizeChanged { state.pinnedHeightPx = it.height.toFloat() }) { pinned() }
    }
}
