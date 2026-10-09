package com.tasalo.android.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tasalo.android.domain.CUBA_ZONE
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.PriceHistory
import com.tasalo.android.domain.PricePoint
import com.tasalo.android.domain.Rate
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.metaFor
import com.tasalo.android.ui.components.AdaptiveSegmentedChoice
import com.tasalo.android.ui.components.ChangeText
import com.tasalo.android.ui.components.EmptyMessage
import com.tasalo.android.ui.components.GlassCard
import com.tasalo.android.ui.components.StatusBanner
import com.tasalo.android.ui.components.floatingContentPadding
import com.tasalo.android.ui.theme.LocalChangeColors
import com.tasalo.android.ui.theme.LocalGlass
import com.tasalo.android.ui.theme.TasaloMono
import com.tasalo.android.util.Format
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class RangeChoice(val label: String, val days: Int) {
    D7("7D", 7),
    D30("30D", 30),
    D90("90D", 90),
    M6("6M", 180),
    CUSTOM("Fechas", 0),
}

/** Con menos días con dato que esto se avisa de que el historial es corto (ej. QvaPay recién añadida). */
private const val SHORT_HISTORY_DAYS = 7

private val LIST_DAYS = listOf(7, 14, 30)
private val locale = Locale("es", "CU")
private val shortDate = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
private val rowDate = DateTimeFormatter.ofPattern("EEE d MMM", locale)

private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
private fun String.capitalized(): String = replaceFirstChar { it.uppercase(locale) }

/**
 * Detalle histórico de una moneda de una fuente. Se muestra a pantalla completa por encima de las secciones
 * (igual que Notificaciones): cabe el gráfico con su lista y el botón atrás funciona sin librería de navegación.
 */
@Composable
fun HistoryDetailScreen(
    source: Source,
    currency: String,
    rate: Rate?,
    state: DetailState,
    now: Instant,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = metaFor(source, currency)
    val today = now.atZone(CUBA_ZONE).toLocalDate()
    var choice by rememberSaveable { mutableStateOf(RangeChoice.M6) }
    var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
    var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var listDays by rememberSaveable { mutableStateOf(7) }
    var scrub by remember { mutableStateOf<PricePoint?>(null) }

    val all = state.points
    val custom = if (customStart != null && customEnd != null) {
        LocalDate.ofEpochDay(customStart!!) to LocalDate.ofEpochDay(customEnd!!)
    } else {
        null
    }
    val visible = when {
        choice == RangeChoice.CUSTOM && custom != null -> PriceHistory.between(all, custom.first, custom.second)
        choice == RangeChoice.CUSTOM -> all
        else -> PriceHistory.lastDays(all, choice.days, today)
    }
    val stats = PriceHistory.stats(visible)
    val trendColor = LocalChangeColors.current.of(stats?.change ?: Change.NEUTRAL)
    val lineColor = if (stats == null || stats.change == Change.NEUTRAL) MaterialTheme.colorScheme.primary else trendColor

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text(
                "${meta?.flag.orEmpty()} $currency · ${source.title}".trim(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(floatingContentPadding(top = 4.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PriceHeader(source, currency, rate, all.lastOrNull())

            AdaptiveSegmentedChoice(
                options = RangeChoice.entries,
                isSelected = { it == choice },
                label = {
                    if (it == RangeChoice.CUSTOM && custom != null) {
                        "${custom.first.dayOfMonth}/${custom.first.monthValue}–${custom.second.dayOfMonth}/${custom.second.monthValue}"
                    } else {
                        it.label
                    }
                },
                onSelect = {
                    scrub = null
                    choice = it
                    if (it == RangeChoice.CUSTOM) showPicker = true
                },
                modifier = Modifier.fillMaxWidth(),
            )

            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        state.loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                        }
                        all.isEmpty() -> {
                            EmptyMessage(
                                if (state.error) "No se pudo cargar el historial." else "Todavía no hay historial para esta moneda.",
                            )
                            if (state.error) {
                                Button(onClick = onRetry, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Reintentar") }
                            }
                        }
                        visible.isEmpty() -> EmptyMessage("No hay datos en este rango. Prueba con uno más amplio.")
                        else -> {
                            ScrubLine(scrub, stats?.last ?: visible.last(), visible)
                            PriceChart(
                                points = visible,
                                color = lineColor,
                                gridColor = LocalGlass.current.border,
                                description = "Gráfico de ${visible.size} días con dato. " +
                                    (stats?.let { "Desde ${Format.rate(it.first.rate)} hasta ${Format.rate(it.last.rate)}." }.orEmpty()),
                                onScrub = { scrub = it },
                            )
                            if (visible.size >= 2) stats?.let { StatsRow(it) }
                        }
                    }
                    if (state.error && all.isNotEmpty()) {
                        Text(
                            "No se pudo actualizar; se muestran los últimos datos guardados.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.loading && all.isNotEmpty() && all.size < SHORT_HISTORY_DAYS) {
                val n = all.size
                StatusBanner(
                    "Historial corto: solo $n ${if (n == 1) "día con dato" else "días con dato"}. " +
                        "El gráfico se irá completando con los días.",
                    isError = false,
                )
            }

            DailyList(all, listDays, today, loading = state.loading, onDays = { listDays = it })
        }
    }

    if (showPicker && all.isNotEmpty()) {
        RangeDialog(
            min = all.first().date,
            max = all.last().date,
            initial = custom,
            onConfirm = { start, end ->
                customStart = start.toEpochDay()
                customEnd = end.toEpochDay()
                showPicker = false
            },
            onDismiss = {
                showPicker = false
                // Cancelar sin haber elegido nunca fechas devuelve al rango por defecto.
                if (custom == null) choice = RangeChoice.M6
            },
        )
    } else if (showPicker) {
        showPicker = false
        choice = RangeChoice.M6
    }
}

@Composable
private fun PriceHeader(source: Source, currency: String, rate: Rate?, last: PricePoint?) {
    val meta = metaFor(source, currency)
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (meta != null) {
                Text(meta.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val price = rate?.rate ?: last?.rate
            Text(
                if (price != null) Format.rate(price) else "—",
                fontFamily = TasaloMono,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            )
            if (source == Source.QVAPAY) {
                Text("por 1 USD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (rate != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChangeText(rate.change, Format.change(rate))
                    Text("vs anterior", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ScrubLine(active: PricePoint?, fallback: PricePoint, visible: List<PricePoint>) {
    val p = active ?: fallback
    val text = if (active != null) {
        "${p.date.format(shortDate)} · ${Format.rate(p.rate)}"
    } else {
        val first = visible.first().date.format(shortDate)
        "$first → ${visible.last().date.format(shortDate)}"
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = TasaloMono,
        fontWeight = if (active != null) FontWeight.Bold else FontWeight.Normal,
        color = if (active != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun StatsRow(stats: com.tasalo.android.domain.PeriodStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Inicial", stats.first, Modifier.weight(1f))
            Stat("Final", stats.last, Modifier.weight(1f))
            Stat("Máximo", stats.max, Modifier.weight(1f))
            Stat("Mínimo", stats.min, Modifier.weight(1f))
        }
        val pct = stats.pct?.let { (if (it < 0) "-" else "+") + Format.percent(abs(it)) }
        val body = Format.arrow(stats.change) +
            if (stats.change == Change.NEUTRAL) "" else " ${Format.rate(abs(stats.delta))}" + (pct?.let { " ($it)" } ?: "")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChangeText(stats.change, body)
            Text("en el periodo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(label: String, point: PricePoint, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(Format.rate(point.rate), fontFamily = TasaloMono, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            "${point.date.dayOfMonth}/${point.date.monthValue}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DailyList(
    points: List<PricePoint>,
    days: Int,
    today: LocalDate,
    loading: Boolean,
    onDays: (Int) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Precio por día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Tomado a las 7:00 a. m. de Cuba (o la lectura más cercana).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AdaptiveSegmentedChoice(
                options = LIST_DAYS,
                isSelected = { it == days },
                label = { "$it días" },
                onSelect = onDays,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!loading) {
                val rows = PriceHistory.rows(points, days, today)
                Column {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(color = LocalGlass.current.border)
                        val label = (if (i == 0) "Hoy, " else "") + row.date.format(rowDate).capitalized()
                        val rate = row.rate
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics {
                                contentDescription = label + ": " + (rate?.let { Format.rate(it) } ?: "sin dato")
                            },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            if (rate != null) {
                                Text(Format.rate(rate), fontFamily = TasaloMono, fontWeight = FontWeight.Bold)
                            } else {
                                Text(
                                    "sin dato",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RangeDialog(
    min: LocalDate,
    max: LocalDate,
    initial: Pair<LocalDate, LocalDate>?,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    // Solo se pueden elegir fechas dentro de los datos que existen.
    val selectable = remember(min, max) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = utcTimeMillis.fromPickerMillis()
                return !d.isBefore(min) && !d.isAfter(max)
            }
        }
    }
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.first?.toPickerMillis(),
        initialSelectedEndDateMillis = initial?.second?.toPickerMillis(),
        initialDisplayedMonthMillis = (initial?.second ?: max).toPickerMillis(),
        selectableDates = selectable,
    )
    val start = pickerState.selectedStartDateMillis
    val end = pickerState.selectedEndDateMillis
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = start != null && end != null,
                onClick = { if (start != null && end != null) onConfirm(start.fromPickerMillis(), end.fromPickerMillis()) },
            ) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    ) {
        DateRangePicker(state = pickerState, modifier = Modifier.height(500.dp))
    }
}
