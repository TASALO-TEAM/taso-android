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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W2 — "Bloque completo": todas las monedas de El Toque, BCC o CADECA. */
class BloqueWidget : SafeGlanceWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.loadOrNull(context)
        provideContent {
            val prefs = currentState<Preferences>()
            WidgetRoot(data?.settings?.theme ?: ThemeMode.AUTO) {
                if (data == null) {
                    WidgetFrame(openApp(context, null)) { EmptyWidget() }
                } else {
                    val source = Source.fromId(prefs[WidgetKeys.SOURCE]) ?: data.settings.defaultSource
                    BloqueContent(context, data, source)
                }
            }
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

private val ArrowWidth = 22.dp

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
        val isCadeca = source == Source.CADECA
        val list = rates.bySource[source].orEmpty().filter { !data.settings.isHidden(source, it.currency) }

        // Sin scroll: se muestran las primeras filas que caben según el alto y la escala de fuente.
        val rowDp = 24f * fontScale().coerceAtMost(1.15f)
        val reserved = if (isCadeca) 68f else 52f
        val maxRows = ((size.height.value - reserved) / rowDp).toInt().coerceIn(1, 15)

        WidgetHeader(source.title, subtitleFor(rates.fetchedAt, data.now))
        if (isCadeca && list.isNotEmpty()) {
            // Las columnas reparten el ancho disponible (antes eran de 64 dp fijos y se desbordaban en widgets estrechos).
            Row(GlanceModifier.fillMaxWidth()) {
                Spacer(GlanceModifier.defaultWeight())
                WText("Compra", color = WidgetColors.dim, size = 10, end = true, modifier = GlanceModifier.defaultWeight())
                WText("Venta", color = WidgetColors.dim, size = 10, end = true, modifier = GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(ArrowWidth))
            }
        } else {
            Spacer(GlanceModifier.height(4.dp))
        }

        if (list.isEmpty()) {
            WText("Sin datos para esta fuente", color = WidgetColors.dim, size = 12)
            return@WidgetFrame
        }

        list.take(maxRows).forEach { rate ->
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WText(rate.currency, size = 13, bold = true, color = textColor, modifier = GlanceModifier.defaultWeight())
                if (isCadeca) {
                    WText(rate.buy?.let(Format::rate) ?: "—", size = 13, mono = true, color = textColor, end = true, modifier = GlanceModifier.defaultWeight())
                    WText(rate.sell?.let(Format::rate) ?: Format.rate(rate.rate), size = 13, mono = true, bold = true, color = textColor, end = true, modifier = GlanceModifier.defaultWeight())
                } else {
                    WText(Format.rate(rate.rate), size = 14, mono = true, bold = true, color = textColor, end = true, modifier = GlanceModifier.defaultWeight())
                }
                WText(
                    Format.arrow(rate.change),
                    color = WidgetColors.change(rate.change, invert),
                    size = 13,
                    bold = true,
                    end = true,
                    modifier = GlanceModifier.width(ArrowWidth),
                )
            }
        }
    }
}
