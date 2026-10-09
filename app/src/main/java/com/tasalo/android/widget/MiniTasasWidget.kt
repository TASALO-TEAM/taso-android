package com.tasalo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W5 - "Mini tasas": hasta 3 monedas de una fuente, cada una con su curva de 30 dias en la misma fila. */
class MiniTasasWidget : SafeGlanceWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.loadOrNull(context)
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetRoot(data?.settings?.theme ?: ThemeMode.AUTO) {
                if (data == null) {
                    WidgetFrame(openApp(context, null)) { EmptyWidget() }
                } else {
                    val source = Source.fromId(prefs[WidgetKeys.SOURCE]) ?: data.settings.defaultSource
                    MiniTasasContent(context, data, source, prefs[WidgetKeys.CURRENCIES])
                }
            }
        }
    }
}

class MiniTasasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniTasasWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.refreshNow(context)
    }
}

private val SparkWidth = 64.dp
private val SparkHeight = 22.dp

@Composable
private fun MiniTasasContent(context: Context, data: WidgetData, source: Source, saved: String?) {
    val rates = data.snapshot.rates
    val size = LocalSize.current
    WidgetFrame(openApp(context, source)) {
        if (rates == null) {
            EmptyWidget()
            return@WidgetFrame
        }
        val stale = Format.isStale(rates.fetchedAt, data.now)
        val invert = data.settings.invertColors
        val main = if (stale) WidgetColors.dim else WidgetColors.text
        val all = rates.bySource[source].orEmpty()
        val wanted = saved?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        val chosen = wanted.mapNotNull { code -> all.firstOrNull { it.currency == code } }
            .ifEmpty { all.take(3) }
            .take(3)

        val scale = fontScale().coerceAtMost(1.15f)
        val usableH = size.height.value - 24f
        val lines = ((usableH - 28f * scale) / (30f * scale)).toInt().coerceIn(1, 3)

        WidgetHeader("${source.title} \u00B7 30D", subtitleFor(rates.fetchedAt, data.now))
        Spacer(GlanceModifier.height(4.dp))
        if (chosen.isEmpty()) {
            WText("Sin datos para esta fuente", color = WidgetColors.dim, size = 12)
            return@WidgetFrame
        }
        chosen.take(lines).forEach { rate ->
            val spark = WidgetChart.rowSpark(context, data, source, rate.currency, SparkWidth.value, SparkHeight.value)
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                WText(rate.currency, size = 14, bold = true, color = main, modifier = GlanceModifier.width(44.dp))
                if (spark != null) {
                    Image(
                        provider = ImageProvider(spark),
                        contentDescription = null,
                        modifier = GlanceModifier.width(SparkWidth).height(SparkHeight),
                    )
                } else {
                    Spacer(GlanceModifier.width(SparkWidth))
                }
                WText(Format.rate(rate.rate), size = 15, bold = true, mono = true, color = main, end = true, modifier = GlanceModifier.defaultWeight())
                WText(
                    " ${Format.arrow(rate.change)}",
                    color = WidgetColors.change(rate.change, invert),
                    size = 13,
                    bold = true,
                )
            }
        }
    }
}
