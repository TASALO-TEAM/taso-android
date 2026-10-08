package com.tasalo.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Relleno horizontal interno de un segmento de Material 3 (a cada lado). */
private const val SEGMENT_PADDING_DP = 12f

/** El check del segmento seleccionado: icono de 18 dp + 8 dp de separación. */
private const val SEGMENT_ICON_DP = 26f

/** Holgura para que un redondeo de la medición no provoque un salto de línea. */
private const val FIT_SLACK_DP = 4f

/**
 * Cómo se reparten los segmentos: cuántos por fila, si cabe el check de selección y si la etiqueta
 * se puede mantener en una sola línea.
 */
internal data class SegmentPlan(val columns: Int, val showIcon: Boolean, val singleLine: Boolean)

/**
 * Elige la distribución de [count] segmentos en [availableDp] de ancho, dado el ancho de la etiqueta más larga
 * en una línea ([widestDp], ya con el tamaño de fuente del sistema aplicado).
 *
 * Escalera, de la más parecida al diseño original a la más tolerante:
 * 1. una fila con check de selección;
 * 2. una fila sin check (la selección se sigue viendo por el relleno y la semántica);
 * 3. menos segmentos por fila (2×2, luego uno por fila), con la misma regla;
 * 4. si ni siquiera una etiqueta sola cabe en una línea, se deja que haga salto de línea: nunca se corta.
 */
internal fun planSegments(availableDp: Float, count: Int, widestDp: Float): SegmentPlan {
    if (count <= 0) return SegmentPlan(columns = 1, showIcon = false, singleLine = true)
    val text = widestDp + FIT_SLACK_DP + SEGMENT_PADDING_DP * 2
    for (columns in count downTo 1) {
        val cell = availableDp / columns
        if (cell >= text + SEGMENT_ICON_DP) return SegmentPlan(columns, showIcon = true, singleLine = true)
        if (cell >= text) return SegmentPlan(columns, showIcon = false, singleLine = true)
    }
    return SegmentPlan(columns = 1, showIcon = false, singleLine = false)
}

/**
 * Selector de una opción entre varias (cápsulas segmentadas) que respeta el tamaño de fuente del sistema.
 *
 * Material 3 deja que la etiqueta de un segmento pase a dos líneas cuando no cabe, y entonces toda la fila
 * crece y se ve distinta a sus vecinas. Aquí se mide primero la etiqueta más larga y, según el espacio real,
 * se elige la distribución ([planSegments]): la fila queda siempre con segmentos de la misma altura y, si
 * hace falta, pasa a varias filas en lugar de romper la cápsula. No se reduce ni se corta el texto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AdaptiveSegmentedChoice(
    options: List<T>,
    isSelected: (T) -> Boolean,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MaterialTheme.typography.labelLarge
    val labels = options.map(label)
    val widestDp = remember(labels, style, density) {
        val px = labels.maxOfOrNull {
            measurer.measure(text = it, style = style, softWrap = false, maxLines = 1).size.width
        } ?: 0
        px / density.density
    }

    BoxWithConstraints(modifier) {
        val plan = planSegments(maxWidth.value, options.size, widestDp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(plan.columns).forEach { rowOptions ->
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    rowOptions.forEachIndexed { index, option ->
                        val selected = isSelected(option)
                        SegmentedButton(
                            selected = selected,
                            onClick = { onSelect(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, rowOptions.size),
                            icon = { if (plan.showIcon) SegmentedButtonDefaults.Icon(selected) },
                        ) {
                            Text(
                                text = label(option),
                                maxLines = if (plan.singleLine) 1 else Int.MAX_VALUE,
                                softWrap = !plan.singleLine,
                            )
                        }
                    }
                }
            }
        }
    }
}
