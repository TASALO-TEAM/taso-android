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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.Source
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W2 — "Bloque completo": todas las monedas de El Toque, BCC o CADECA. */
class BloqueWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.load(context)
        provideContent {
            val prefs = currentState<Preferences>()
            val source = Source.fromId(prefs[WidgetKeys.SOURCE]) ?: data.settings.defaultSource
            BloqueContent(context, data, source)
        }
    }
}

class BloqueWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BloqueWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.refreshNow(context)
    }
}

@Composable
private fun BloqueContent(context: Context, data: WidgetData, source: Source) {
    val rates = data.snapshot.rates
    val size = LocalSize.current
    WidgetFrame(openApp(context, source)) {
        if (rates == null) {
            EmptyWidget()
            return@WidgetFrame
        }
        val stale = Format.isStale(rates.fetchedAt, data.now)
        val invert = data.settings.invertColors
        val textColor = if (stale) WidgetColors.dim else WidgetColors.text
        val list = rates.bySource[source].orEmpty().filter { !data.settings.isHidden(source, it.currency) }
        // Sin scroll: se muestran las primeras filas que caben según el alto.
        val reserved = if (source == Source.CADECA) 58f else 42f
        val maxRows = ((size.height.value - reserved) / 24f).toInt().coerceIn(1, 15)

        WidgetHeader(source.title, subtitleFor(rates.fetchedAt, data.now))
        if (source == Source.CADECA && list.isNotEmpty()) {
            Row(GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.defaultWeight())
                WText("Compra", color = WidgetColors.dim, size = 10, modifier = GlanceModifier.width(64.dp))
                WText("Venta", color = WidgetColors.dim, size = 10, modifier = GlanceModifier.width(64.dp))
                Spacer(GlanceModifier.width(16.dp))
            }
        } else {
            Spacer(GlanceModifier.height(4.dp))
        }

        if (list.isEmpty()) {
            WText("Sin datos para esta fuente", color = WidgetColors.dim, size = 12)
            return@WidgetFrame
        }

        list.take(maxRows).forEach { rate ->
            val flag = Currencies.META[rate.currency]?.flag.orEmpty()
            Row(GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                WText("$flag ${rate.currency}".trim(), size = 13, bold = true, color = textColor)
                Spacer(GlanceModifier.defaultWeight())
                if (source == Source.CADECA) {
                    WText(rate.buy?.let(Format::rate) ?: "—", size = 13, mono = true, color = textColor, modifier = GlanceModifier.width(64.dp))
                    WText(rate.sell?.let(Format::rate) ?: Format.rate(rate.rate), size = 13, mono = true, bold = true, color = textColor, modifier = GlanceModifier.width(64.dp))
                } else {
                    WText(Format.rate(rate.rate), size = 14, mono = true, bold = true, color = textColor)
                }
                WText(" ${Format.arrow(rate.change)}", color = WidgetColors.change(rate.change, invert), size = 13, bold = true, modifier = GlanceModifier.width(16.dp))
            }
        }
    }
}
