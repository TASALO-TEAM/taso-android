package com.tasalo.android.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler

/** W3 — "Año y frase": progreso del año (siempre disponible, cálculo local) y frase del día. */
class AnioFraseWidget : SafeGlanceWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetData.loadOrNull(context)
        provideContent {
            WidgetRoot(data?.settings?.theme ?: ThemeMode.AUTO) {
                if (data == null) {
                    WidgetFrame(openApp(context, null)) { EmptyWidget() }
                } else {
                    AnioFraseContent(context, data)
                }
            }
        }
    }
}

class AnioFraseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AnioFraseWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.refreshNow(context)
    }
}

@Composable
private fun AnioFraseContent(context: Context, data: WidgetData) {
    val year = data.snapshot.year
    val size = LocalSize.current
    WidgetFrame(openApp(context, null)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WText("Año ${year.year}", color = WidgetColors.accent, size = 13, bold = true)
            Spacer(GlanceModifier.defaultWeight())
            WText(Format.percent(year.percent), size = 13, bold = true, mono = true)
            WText(
                " ↻",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()),
                color = WidgetColors.accent,
                size = 16,
                bold = true,
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (year.percent / 100).toFloat().coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = WidgetColors.accent,
            backgroundColor = WidgetColors.track,
        )
        Spacer(GlanceModifier.height(4.dp))
        WText("Quedan ${year.daysLeft} días · ${year.weeksLeft} semanas", color = WidgetColors.dim, size = 12)

        val quote = year.quote
        if (quote != null && size.height >= 100.dp) {
            Spacer(GlanceModifier.height(6.dp))
            WText("“$quote”", size = 12, maxLines = if (size.height >= 160.dp) 6 else 3)
        }
    }
}
