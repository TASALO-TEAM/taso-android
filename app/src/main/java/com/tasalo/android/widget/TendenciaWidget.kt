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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import com.tasalo.android.domain.PriceHistory
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W4 - "Tendencia": una moneda con su curva de 30 dias de fondo, variacion del periodo, minimo y maximo. */
class TendenciaWidget : SafeGlanceWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.loadOrNull(context)
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetRoot(data?.settings?.theme ?: ThemeMode.AUTO) {
                if (data == null) {
                    WidgetFrame(openApp(context, null)) { EmptyWidget() }
                } else {
                    val source = Source.fromId(prefs[WidgetKeys.SOURCE]) ?: data.settings.defaultSource
                    TendenciaContent(context, data, source, prefs[WidgetKeys.CURRENCIES])
                }
            }
        }
    }
}

class TendenciaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TendenciaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.refreshNow(context)
    }
}

@Composable
private fun TendenciaContent(context: Context, data: WidgetData, source: Source, saved: String?) {
    val rates = data.snapshot.rates
    val size = LocalSize.current
    val all = rates?.bySource?.get(source).orEmpty()
    val code = saved?.split(",")?.firstOrNull { c -> all.any { it.currency == c } } ?: all.firstOrNull()?.currency
    val rate = all.firstOrNull { it.currency == code }
    val points = if (code != null) data.history["${source.name}:$code"].orEmpty() else emptyList()
    val stats = PriceHistory.stats(points)
    val backdrop = if (code != null) WidgetChart.backdrop(context, data, source, code, size, strong = true) else null

    WidgetFrame(openApp(context, source), backdrop) {
        if (rates == null || rate == null) {
            EmptyWidget()
            return@WidgetFrame
        }
        val stale = Format.isStale(rates.fetchedAt, data.now)
        val invert = data.settings.invertColors
        val main = if (stale) WidgetColors.dim else WidgetColors.text

        WidgetHeader("${rate.currency} \u00B7 30D", subtitleFor(rates.fetchedAt, data.now))
        Spacer(GlanceModifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            WText(Format.rate(rate.rate), size = 28, bold = true, mono = true, color = main)
            if (stats != null) {
                WText(
                    "  ${Format.arrow(stats.change)} ${WidgetChartMath.pctText(stats.pct)}",
                    color = WidgetColors.change(stats.change, invert),
                    size = 13,
                    bold = true,
                )
            }
        }
        if (stats != null) {
            WText(
                "M\u00EDn ${Format.rate(stats.min.rate)}  \u00B7  M\u00E1x ${Format.rate(stats.max.rate)}",
                color = WidgetColors.dim,
                size = 11,
            )
        } else {
            WText("Sin historial todav\u00EDa", color = WidgetColors.dim, size = 11)
        }
    }
}
