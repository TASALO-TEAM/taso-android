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
import com.tasalo.android.domain.Rate
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W2 — "Bloque": las monedas de El Toque, BCC o CADECA, en una o dos columnas según el ancho. */
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

private val ArrowWidth = 20.dp

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

        val scale = fontScale().coerceAtMost(1.15f)
        val usableW = size.width.value - 24f
        val usableH = size.height.value - 24f

        // Con ancho de sobra, dos columnas: la misma información cabe en menos alto
        // (antes un 4x3 quedaba medio vacío). CADECA necesita más ancho por las dos cifras.
        val columns = if (usableW >= (if (isCadeca) 280f else 220f)) 2 else 1
        val rowDp = 24f * scale
        val headerDp = 24f * scale + if (isCadeca) 14f else 4f
        val maxRows = ((usableH - headerDp) / rowDp).toInt().coerceIn(1, 15)

        WidgetHeader(source.title, subtitleFor(rates.fetchedAt, data.now))
        if (isCadeca && list.isNotEmpty()) {
            Row(GlanceModifier.fillMaxWidth()) {
                repeat(columns) { index ->
                    if (index > 0) Spacer(GlanceModifier.width(10.dp))
                    CadecaHeader(GlanceModifier.defaultWeight())
                }
            }
        } else {
            Spacer(GlanceModifier.height(4.dp))
        }

        if (list.isEmpty()) {
            WText("Sin datos para esta fuente", color = WidgetColors.dim, size = 12)
            return@WidgetFrame
        }

        list.take(maxRows * columns).chunked(columns).forEach { chunk ->
            Row(GlanceModifier.fillMaxWidth()) {
                chunk.forEachIndexed { index, rate ->
                    if (index > 0) Spacer(GlanceModifier.width(10.dp))
                    RateCellRow(rate, isCadeca, textColor, invert, GlanceModifier.defaultWeight())
                }
                // Última fila incompleta: se reserva el hueco para que la columna no se estire.
                repeat(columns - chunk.size) {
                    Spacer(GlanceModifier.width(10.dp))
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun CadecaHeader(modifier: GlanceModifier) {
    Row(modifier) {
        Spacer(GlanceModifier.defaultWeight())
        WText("Compra", color = WidgetColors.dim, size = 10, end = true, modifier = GlanceModifier.defaultWeight())
        WText("Venta", color = WidgetColors.dim, size = 10, end = true, modifier = GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(ArrowWidth))
    }
}

/** Una moneda: código a la izquierda (flexible), cifras alineadas a la derecha y flecha de ancho fijo. */
@Composable
private fun RateCellRow(
    rate: Rate,
    isCadeca: Boolean,
    textColor: androidx.glance.unit.ColorProvider,
    invert: Boolean,
    modifier: GlanceModifier,
) {
    Row(modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
