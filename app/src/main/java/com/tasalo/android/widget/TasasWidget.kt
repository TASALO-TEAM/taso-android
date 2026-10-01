package com.tasalo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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

@Composable
private fun TasasContent(context: Context, data: WidgetData, source: Source, saved: String?) {
    val rates = data.snapshot.rates
    val size = LocalSize.current
    WidgetFrame(openApp(context, source)) {
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

        WidgetHeader(source.title, subtitleFor(rates.fetchedAt, data.now))
        Spacer(GlanceModifier.height(4.dp))

        // Se muestran tantas monedas como caben: así un widget pequeño no se corta ni se desborda.
        val rowsThatFit = ((size.height.value - 40f) / (24f * fontScale().coerceAtMost(1.15f))).toInt().coerceAtLeast(1)
        when {
            chosen.isEmpty() -> WText("Sin datos para esta fuente", color = WidgetColors.dim, size = 12)
            chosen.size == 1 -> BigRate(chosen.first(), stale, invert, tall = size.height >= 90.dp)
            size.width >= 220.dp && chosen.size >= 3 -> {
                chosen.chunked(2).take(rowsThatFit.coerceAtLeast(1)).forEach { pair ->
                    Row(GlanceModifier.fillMaxWidth()) {
                        pair.forEach { rate ->
                            Column(GlanceModifier.defaultWeight()) { RateCell(rate, stale, invert) }
                        }
                        if (pair.size == 1) Spacer(GlanceModifier.defaultWeight())
                    }
                }
            }
            else -> chosen.take(rowsThatFit).forEach { rate -> RateLine(rate, stale, invert) }
        }
    }
}

@Composable
private fun BigRate(rate: Rate, stale: Boolean, invert: Boolean, tall: Boolean) {
    val meta = Currencies.META[rate.currency]
    val main = if (stale) WidgetColors.dim else WidgetColors.text
    Row(verticalAlignment = Alignment.CenterVertically) {
        WText(rate.currency, size = 14, bold = true, color = main)
        if (tall && meta != null) WText("  ${meta.name}", size = 12, color = WidgetColors.dim)
    }
    WText(Format.rate(rate.rate), size = 30, bold = true, mono = true, color = main)
    WText(Format.change(rate), color = WidgetColors.change(rate.change, invert), size = 13, bold = true)
}

@Composable
private fun RateLine(rate: Rate, stale: Boolean, invert: Boolean) {
    val main = if (stale) WidgetColors.dim else WidgetColors.text
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WText(rate.currency, size = 14, bold = true, color = main, modifier = GlanceModifier.defaultWeight())
        WText(Format.rate(rate.rate), size = 15, bold = true, mono = true, color = main, end = true)
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
