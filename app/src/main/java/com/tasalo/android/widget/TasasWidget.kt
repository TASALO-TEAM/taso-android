package com.tasalo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.Rate
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.metaFor
import com.tasalo.android.domain.widgetTitle
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W1 — "Tasas": de 1 a 4 monedas de una fuente elegida al añadir el widget. */
class TasasWidget : SafeGlanceWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.loadOrNull(context)
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetRoot(data?.settings?.theme ?: ThemeMode.AUTO) {
                if (data == null) {
                    WidgetFrame(openApp(context, null)) { EmptyWidget() }
                } else {
                    val source = Source.fromId(prefs[WidgetKeys.SOURCE]) ?: data.settings.defaultSource
                    TasasContent(context, data, source, prefs[WidgetKeys.CURRENCIES])
                }
            }
        }
    }
}

class TasasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasasWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.refreshNow(context)
    }
}

/** Tamaño del valor según el ancho útil: en un 2x1 estrecho tiene que caber junto al código. */
private fun valueSizeFor(usableWidthDp: Float): Int = when {
    usableWidthDp >= 150f -> 26
    usableWidthDp >= 115f -> 22
    else -> 18
}

@Composable
private fun TasasContent(context: Context, data: WidgetData, source: Source, saved: String?) {
    val rates = data.snapshot.rates
    val size = LocalSize.current
    val backdropCode = run {
        val all = rates?.bySource?.get(source).orEmpty()
        val wanted = saved?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        wanted.firstOrNull { c -> all.any { it.currency == c } } ?: all.firstOrNull()?.currency
    }
    val backdrop = if (backdropCode != null) WidgetChart.backdrop(context, data, source, backdropCode, size) else null
    WidgetFrame(openApp(context, source), backdrop) {
        if (rates == null) {
            EmptyWidget()
            return@WidgetFrame
        }
        val stale = Format.isStale(rates.fetchedAt, data.now)
        val invert = data.settings.invertColors
        val all = rates.bySource[source].orEmpty()
        val wanted = saved?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val chosen = wanted.mapNotNull { code -> all.firstOrNull { it.currency == code } }
            .ifEmpty { all.take(2) }
            .take(4)
        val subtitle = subtitleFor(rates.fetchedAt, data.now)

        // Medidas útiles (sin el padding de 12 dp por lado) y escala de fuente limitada.
        val scale = fontScale().coerceAtMost(1.15f)
        val usableW = size.width.value - 24f
        val usableH = size.height.value - 24f

        when {
            chosen.isEmpty() -> {
                WidgetHeader(source.widgetTitle(), subtitle)
                WText("Sin datos para esta fuente", color = WidgetColors.dim, size = 12)
            }

            // 2x1 (una celda de alto): sin cabecera y todo en una fila; el valor va a la derecha.
            usableH < 66f * scale -> {
                val lines = (usableH / (34f * scale)).toInt().coerceAtLeast(1)
                chosen.take(lines).forEach { rate ->
                    CompactRow(rate, source, subtitle.takeIf { lines == 1 }, stale, invert, valueSizeFor(usableW))
                }
            }

            else -> {
                WidgetHeader(source.widgetTitle(), subtitle)
                Spacer(GlanceModifier.height(4.dp))
                val bodyH = usableH - 28f * scale
                val bigFits = bodyH >= 78f * scale
                val lines = (bodyH / (28f * scale)).toInt().coerceAtLeast(1)
                when {
                    chosen.size == 1 && bigFits ->
                        BigRate(chosen.first(), source, stale, invert, showName = bodyH >= 96f * scale)
                    usableW >= 200f && chosen.size >= 3 -> {
                        val gridRows = (bodyH / (44f * scale)).toInt().coerceAtLeast(1)
                        chosen.chunked(2).take(gridRows).forEach { pair ->
                            Row(GlanceModifier.fillMaxWidth()) {
                                pair.forEach { rate ->
                                    Column(GlanceModifier.defaultWeight()) { RateCell(rate, stale, invert) }
                                }
                                if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
                            }
                        }
                    }
                    else -> chosen.take(lines).forEach { rate ->
                        RateLine(rate, stale, invert, valueSize = valueSizeFor(usableW))
                    }
                }
            }
        }
    }
}

/**
 * Fila única para widgets de una celda de alto: código (y hora) a la izquierda, valor a la derecha.
 * La columna izquierda reparte el espacio sobrante, así el valor nunca se corta.
 */
@Composable
private fun CompactRow(
    rate: Rate,
    source: Source,
    subtitle: String?,
    stale: Boolean,
    invert: Boolean,
    valueSize: Int,
) {
    val main = if (stale) WidgetColors.dim else WidgetColors.text
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WText(rate.currency, size = 14, bold = true, color = main)
                WText(" ${Format.arrow(rate.change)}", color = WidgetColors.change(rate.change, invert), size = 12, bold = true)
            }
            if (subtitle != null) {
                // Tocar la hora refresca los datos (no hay botón de refresco en este tamaño).
                WText(
                    "↻ ${source.title} · $subtitle",
                    modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()),
                    color = WidgetColors.dim,
                    size = 10,
                )
            }
        }
        WText(Format.rate(rate.rate), size = valueSize, bold = true, mono = true, color = main, end = true)
    }
}

@Composable
private fun BigRate(rate: Rate, source: Source, stale: Boolean, invert: Boolean, showName: Boolean) {
    val meta = metaFor(source, rate.currency)
    val main = if (stale) WidgetColors.dim else WidgetColors.text
    Row(verticalAlignment = Alignment.CenterVertically) {
        WText(rate.currency, size = 14, bold = true, color = main)
        if (showName && meta != null) WText("  ${meta.name}", size = 12, color = WidgetColors.dim)
    }
    WText(Format.rate(rate.rate), size = 30, bold = true, mono = true, color = main)
    WText(Format.change(rate), color = WidgetColors.change(rate.change, invert), size = 13, bold = true)
}

@Composable
private fun RateLine(rate: Rate, stale: Boolean, invert: Boolean, valueSize: Int) {
    val main = if (stale) WidgetColors.dim else WidgetColors.text
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WText(rate.currency, size = 14, bold = true, color = main, modifier = GlanceModifier.defaultWeight())
        WText(Format.rate(rate.rate), size = minOf(valueSize, 20), bold = true, mono = true, color = main, end = true)
        WText(" ${Format.arrow(rate.change)}", color = WidgetColors.change(rate.change, invert), size = 13, bold = true)
    }
}

@Composable
private fun RateCell(rate: Rate, stale: Boolean, invert: Boolean) {
    val main = if (stale) WidgetColors.dim else WidgetColors.text
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WText(rate.currency, size = 12, bold = true, color = WidgetColors.dim)
            WText(" ${Format.arrow(rate.change)}", color = WidgetColors.change(rate.change, invert), size = 12, bold = true)
        }
        WText(Format.rate(rate.rate), size = 18, bold = true, mono = true, color = main)
    }
}
