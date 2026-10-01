package com.tasalo.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.FuelPrice
import com.tasalo.android.domain.Fuel
import com.tasalo.android.domain.Rate
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.YearState
import com.tasalo.android.ui.theme.LocalChangeColors
import com.tasalo.android.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val CardShape = RoundedCornerShape(18.dp)

/** Tarjeta semitransparente con borde sutil y esquinas de 16–20 dp (plan §6). */
@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
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

@Composable
fun RateCard(rate: Rate, source: Source, modifier: Modifier = Modifier) {
    val meta = Currencies.META[rate.currency]
    GlassCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meta?.flag.orEmpty(), fontSize = 20.sp)
                if (meta != null) Spacer(Modifier.width(6.dp))
                Text(rate.currency, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (meta != null) {
                Text(
                    meta.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                Format.rate(rate.rate),
                fontFamily = FontFamily.Monospace,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            if (source == Source.CADECA && (rate.buy != null || rate.sell != null)) {
                val buy = rate.buy?.let(Format::rate) ?: "—"
                val sell = rate.sell?.let(Format::rate) ?: "—"
                Text(
                    "Compra $buy · Venta $sell",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChangeText(rate.change, Format.change(rate))
        }
    }
}

@Composable
fun FuelCard(price: FuelPrice, modifier: Modifier = Modifier) {
    val meta = Fuel.META[price.key]
    GlassCard(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(meta?.name ?: price.key, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (meta != null) {
                    Text(
                        meta.subtype,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Format.fuelRange(price),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    meta?.unit ?: "CUP",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChangeText(price.change, Format.arrow(price.change))
            }
        }
    }
}

private val MONTH_LETTERS = listOf("E", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")

@Composable
fun YearCard(year: YearState, now: Instant, modifier: Modifier = Modifier) {
    val currentMonth = now.atZone(ZoneId.systemDefault()).monthValue
    GlassCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Año ${year.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${Format.percent(year.percent)} completado",
                    fontFamily = FontFamily.Monospace,
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
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall)
    }
}

/** Skeleton estático (sin animaciones infinitas, por batería). */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Int = 96) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
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
