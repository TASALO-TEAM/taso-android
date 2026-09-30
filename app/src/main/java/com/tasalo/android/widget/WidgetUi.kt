package com.tasalo.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tasalo.android.MainActivity
import com.tasalo.android.container
import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Snapshot
import com.tasalo.android.domain.Source
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler
import java.time.Instant

/** Claves del estado por `appWidgetId` (PreferencesGlanceStateDefinition). */
object WidgetKeys {
    val SOURCE = stringPreferencesKey("widget_source")
    val CURRENCIES = stringPreferencesKey("widget_currencies")
}

/** Paleta de la extensión, con variante clara/oscura (plan §6). */
object WidgetColors {
    val bg = ColorProvider(day = Color(0xE6E8EAF3), night = Color(0xE609091E))
    val text = ColorProvider(day = Color(0xFF1A1B2E), night = Color(0xFFE8EAF3))
    val dim = ColorProvider(day = Color(0xFF6A6E88), night = Color(0xFF8A8FB0))
    val accent = ColorProvider(day = Color(0xFF3B6EE8), night = Color(0xFF5B8AFF))
    val track = ColorProvider(day = Color(0x33000000), night = Color(0x33FFFFFF))
    private val red = ColorProvider(day = Color(0xFFDC2626), night = Color(0xFFFF6B6B))
    private val green = ColorProvider(day = Color(0xFF16A34A), night = Color(0xFF4ADE80))

    fun change(change: Change, invert: Boolean): ColorProvider = when (change) {
        Change.UP -> if (invert) green else red
        Change.DOWN -> if (invert) red else green
        Change.NEUTRAL -> dim
    }
}

/** Lo que leen los widgets: solo el caché, nunca la red (plan §3). */
data class WidgetData(val snapshot: Snapshot, val settings: AppSettings, val now: Instant) {
    companion object {
        suspend fun load(context: Context): WidgetData {
            val c = context.container
            val now = Instant.now()
            return WidgetData(c.repository.snapshot(now), c.settingsStore.current(), now)
        }
    }
}

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        TasasWidget().updateAll(context)
        BloqueWidget().updateAll(context)
        AnioFraseWidget().updateAll(context)
    }
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RefreshScheduler.refreshNow(context)
    }
}

/** Tap en el widget: abre la app en la fuente correspondiente. */
fun openApp(context: Context, source: Source?): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // El `data` distingue los PendingIntent entre widgets (los extras no cuentan para la igualdad).
        data = Uri.parse("tasalo://open/${source?.name ?: "home"}")
        source?.let { putExtra(MainActivity.EXTRA_SOURCE, it.name) }
    }
    return actionStartActivity(intent)
}

@Composable
fun WText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = WidgetColors.text,
    size: Int = 14,
    bold: Boolean = false,
    mono: Boolean = false,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else null,
        ),
    )
}

/** Fondo translúcido con esquinas redondeadas (Glance no soporta blur). */
@Composable
fun WidgetFrame(onClick: Action, content: @Composable () -> Unit) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.bg)
            .cornerRadius(20.dp)
            .padding(10.dp)
            .clickable(onClick),
    ) {
        content()
    }
}

@Composable
fun EmptyWidget() {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WText("Abre TASALO para cargar", color = WidgetColors.dim, size = 13)
    }
}

/** Título + subtítulo (hora relativa) + botón de refresco. */
@Composable
fun WidgetHeader(title: String, subtitle: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WText(title, color = WidgetColors.accent, size = 13, bold = true)
        Spacer(GlanceModifier.defaultWeight())
        WText(subtitle, color = WidgetColors.dim, size = 11)
        WText(" ↻", modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()), color = WidgetColors.accent, size = 16, bold = true)
    }
}

fun subtitleFor(fetchedAt: Instant?, now: Instant): String =
    if (fetchedAt == null) "sin datos" else Format.relative(fetchedAt, now)
