package com.tasalo.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import com.tasalo.android.ui.theme.LocalReduceMotion
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.PriceHistory
import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.Rate
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.metaFor
import com.tasalo.android.domain.YearState
import com.tasalo.android.ui.history.SparklineBackground
import com.tasalo.android.ui.theme.LocalChangeColors
import com.tasalo.android.ui.theme.LocalGlass
import com.tasalo.android.ui.theme.TasaloMono
import com.tasalo.android.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tarjeta "Quiet Glass": cristal casi transparente, borde fino y esquinas de 22 dp (como `--r` de la extensión). */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlass.current
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = glass.surfaceGlass,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, glass.border),
        content = content,
    )
}

/** El cambio nunca se indica solo con color: flecha + texto accesible (plan §6). */
@Composable
fun ChangeText(change: Change, text: String, modifier: Modifier = Modifier) {
    val description = Format.changeDescription(change)
    Text(
        text = text,
        color = LocalChangeColors.current.of(change),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.semantics { contentDescription = "$description. $text" },
    )
}

/**
 * Tarjeta de una moneda. Con `history` (últimos 30 días) dibuja de fondo la tendencia, muy tenue y difuminada, y
 * con `onClick` se abre el detalle histórico. Sin ninguno de los dos se ve y se comporta como antes.
 */
@Composable
fun RateCard(
    rate: Rate,
    source: Source,
    modifier: Modifier = Modifier,
    history: List<PricePoint> = emptyList(),
    onClick: (() -> Unit)? = null,
    /** true = fila a todo el ancho (vista en lista); false = tarjeta compacta de la cuadricula. */
    listMode: Boolean = false,
) {
    val meta = metaFor(source, rate.currency)
    val trend = PriceHistory.trend(history)
    val sparkColor = if (trend == Change.NEUTRAL) MaterialTheme.colorScheme.primary else LocalChangeColors.current.of(trend)
    val cardModifier = if (onClick != null) {
        modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = "Ver historial de ${rate.currency}", onClick = onClick)
    } else {
        modifier
    }
    GlassCard(cardModifier) {
        Box(Modifier.fillMaxWidth()) {
            SparklineBackground(history, sparkColor, Modifier.matchParentSize())
            if (listMode) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RateTitle(rate.currency, meta?.flag, meta?.name)
                        ChangeText(rate.change, Format.change(rate))
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        RateValue(rate, source)
                    }
                }
            } else {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RateTitle(rate.currency, meta?.flag, meta?.name)
                    RateValue(rate, source)
                    ChangeText(rate.change, Format.change(rate))
                }
            }
        }
    }
}

/** Bandera, codigo y nombre de la moneda: igual en tarjeta y en lista. */
@Composable
private fun RateTitle(currency: String, flag: String?, name: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(flag.orEmpty(), fontSize = 20.sp)
        if (name != null) Spacer(Modifier.width(6.dp))
        Text(currency, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
    if (name != null) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Precio, aclaracion de QvaPay y compra/venta: igual en tarjeta y en lista. */
@Composable
private fun RateValue(rate: Rate, source: Source) {
    Text(Format.rate(rate.rate), fontFamily = TasaloMono, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    if (source == Source.QVAPAY) {
        Text("por 1 USD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (source.hasBuySell && (rate.buy != null || rate.sell != null)) {
        val buy = rate.buy?.let(Format::rate) ?: "\u2014"
        val sell = rate.sell?.let(Format::rate) ?: "\u2014"
        Text(
            "Compra $buy \u00B7 Venta $sell",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = TasaloMono,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MONTH_LETTERS = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YearCard(year: YearState, now: Instant, modifier: Modifier = Modifier) {
    val currentMonth = now.atZone(ZoneId.systemDefault()).monthValue
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text("Año ${year.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${Format.percent(year.percent)} completado",
                    fontFamily = TasaloMono,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LinearProgressIndicator(
                progress = { (year.percent / 100).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Row(Modifier.fillMaxWidth()) {
                MONTH_LETTERS.forEachIndexed { index, letter ->
                    val active = index + 1 == currentMonth
                    Text(
                        letter,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                "Transcurridos ${year.daysPassed} · Restantes ${year.daysLeft} · Semanas restantes ${year.weeksLeft}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun QuoteCard(quote: String, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Frase del día",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text("“$quote”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
fun StatusBanner(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** Skeleton con un pulso suave que se detiene solo tras unos segundos (sin animacion infinita, por bateria). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Int = 96) {
    val tone = MaterialTheme.colorScheme.onSurface
    val reduce = LocalReduceMotion.current
    val alpha = remember { Animatable(0.08f) }
    LaunchedEffect(reduce) {
        if (!reduce) repeat(3) {
            alpha.animateTo(0.14f, tween(700))
            alpha.animateTo(0.08f, tween(700))
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(MaterialTheme.shapes.medium)
            .drawBehind { drawRect(tone.copy(alpha = alpha.value)) },
    )
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(24.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
