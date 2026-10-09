package com.tasalo.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColor
import com.tasalo.android.MainActivity
import com.tasalo.android.container
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.Change
import com.tasalo.android.domain.Snapshot
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.util.Format
import com.tasalo.android.work.RefreshScheduler
import java.time.Instant

/** Claves del estado por `appWidgetId` (PreferencesGlanceStateDefinition). */
object WidgetKeys {
    val SOURCE = stringPreferencesKey("widget_source")
    val CURRENCIES = stringPreferencesKey("widget_currencies")
}

/**
 * Paleta de la extensión. En AUTO sigue el modo día/noche del sistema; si el usuario fijó
 * Claro u Oscuro en Ajustes, los widgets lo respetan también (antes solo seguían al sistema).
 */
data class WidgetPalette(
    val bg: GlanceColor,
    val text: GlanceColor,
    val dim: GlanceColor,
    val accent: GlanceColor,
    val track: GlanceColor,
    val up: GlanceColor,
    val down: GlanceColor,
) {
    companion object {
        private fun fixed(argb: Long): GlanceColor = ColorProvider(day = Color(argb), night = Color(argb))

        // Fondo casi opaco: sobre fondos de pantalla claros/ruidosos el texto seguía ilegible con 90 %.
        val Auto = WidgetPalette(
            bg = ColorProvider(day = Color(0xF5E6EEF1), night = Color(0xF50E1621)),
            text = ColorProvider(day = Color(0xFF1B2A33), night = Color(0xFFE8EEF5)),
            dim = ColorProvider(day = Color(0xFF4F6470), night = Color(0xFF9FB0C3)),
            accent = ColorProvider(day = Color(0xFF09665B), night = Color(0xFF3DD6C0)),
            track = ColorProvider(day = Color(0x33000000), night = Color(0x33FFFFFF)),
            up = ColorProvider(day = Color(0xFFBC2C3C), night = Color(0xFFF2555F)),
            down = ColorProvider(day = Color(0xFF13724F), night = Color(0xFF34D399)),
        )
        val Light = WidgetPalette(
            bg = fixed(0xF5E6EEF1),
            text = fixed(0xFF1B2A33),
            dim = fixed(0xFF4F6470),
            accent = fixed(0xFF09665B),
            track = fixed(0x33000000),
            up = fixed(0xFFBC2C3C),
            down = fixed(0xFF13724F),
        )
        val Dark = WidgetPalette(
            bg = fixed(0xF50E1621),
            text = fixed(0xFFE8EEF5),
            dim = fixed(0xFF9FB0C3),
            accent = fixed(0xFF3DD6C0),
            track = fixed(0x33FFFFFF),
            up = fixed(0xFFF2555F),
            down = fixed(0xFF34D399),
        )

        fun of(mode: ThemeMode): WidgetPalette = when (mode) {
            ThemeMode.AUTO -> Auto
            ThemeMode.LIGHT -> Light
            ThemeMode.DARK -> Dark
        }
    }
}

val LocalPalette = staticCompositionLocalOf { WidgetPalette.Auto }

/** Accesos cortos a la paleta vigente; solo se usan dentro de composables de widget. */
object WidgetColors {
    val bg: GlanceColor @Composable get() = LocalPalette.current.bg
    val text: GlanceColor @Composable get() = LocalPalette.current.text
    val dim: GlanceColor @Composable get() = LocalPalette.current.dim
    val accent: GlanceColor @Composable get() = LocalPalette.current.accent
    val track: GlanceColor @Composable get() = LocalPalette.current.track

    @Composable
    fun change(change: Change, invert: Boolean): GlanceColor {
        val p = LocalPalette.current
        return when (change) {
            Change.UP -> if (invert) p.down else p.up
            Change.DOWN -> if (invert) p.up else p.down
            Change.NEUTRAL -> p.dim
        }
    }
}

@Composable
fun WidgetRoot(theme: ThemeMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides WidgetPalette.of(theme)) { content() }
}

/** Lo que leen los widgets: solo el caché, nunca la red (plan §3). */
data class WidgetData(val snapshot: Snapshot, val settings: AppSettings, val now: Instant) {
    companion object {
        suspend fun load(context: Context): WidgetData {
            val c = context.container
            val now = Instant.now()
            return WidgetData(c.repository.snapshot(now), c.settingsStore.current(), now)
        }

        /** Si algo falla al leer, el widget muestra "Abre TASALO" en lugar de romperse. */
        suspend fun loadOrNull(context: Context): WidgetData? = try {
            load(context)
        } catch (e: Exception) {
            DiagnosticLog.e("Widget", "no se pudo leer el caché", e)
            null
        }
    }
}

/** Base común: registra en el diagnóstico cualquier error de composición (clave para fallos por dispositivo). */
abstract class SafeGlanceWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        DiagnosticLog.e("Widget", "error de composición en ${javaClass.simpleName} (id=$appWidgetId)", throwable)
        super.onCompositionError(context, glanceId, appWidgetId, throwable)
    }
}

object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        try {
            TasasWidget().updateAll(context)
            BloqueWidget().updateAll(context)
            AnioFraseWidget().updateAll(context)
        } catch (e: Exception) {
            DiagnosticLog.e("Widget", "updateAll falló", e)
        }
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

/** Escala de fuente máxima que toleran los widgets: más allá, el texto se sale de la celda. */
private const val MAX_FONT_SCALE = 1.15f

@Composable
fun fontScale(): Float = LocalContext.current.resources.configuration.fontScale.coerceAtLeast(1f)

@Composable
fun WText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    color: GlanceColor = WidgetColors.text,
    size: Int = 14,
    bold: Boolean = false,
    mono: Boolean = false,
    end: Boolean = false,
    maxLines: Int = 1,
) {
    // "Tamaño de fuente muy grande" (Samsung/Xiaomi) rompía las filas: se limita el escalado.
    val scale = fontScale()
    val adjust = if (scale > MAX_FONT_SCALE) MAX_FONT_SCALE / scale else 1f
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        style = TextStyle(
            color = color,
            fontSize = (size * adjust).sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else null,
            textAlign = if (end) TextAlign.End else TextAlign.Start,
        ),
    )
}

/**
 * Fondo del widget. En Android 12+ usa el radio de esquina del sistema (dimen del propio Android) para que el widget
 * case con el launcher; antes de eso, un radio fijo. El padding de 12 dp evita que el contenido
 * choque con las esquinas grandes de Android 12+.
 */
@Composable
fun WidgetFrame(onClick: Action, content: @Composable () -> Unit) {
    val shape = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        GlanceModifier.cornerRadius(22.dp)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(WidgetColors.bg)
            .then(shape)
            .padding(12.dp)
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
        WText(
            " ↻",
            modifier = GlanceModifier.clickable(actionRunCallback<RefreshCallback>()),
            color = WidgetColors.accent,
            size = 16,
            bold = true,
        )
    }
}

fun subtitleFor(fetchedAt: Instant?, now: Instant): String =
    if (fetchedAt == null) "sin datos" else "act. ${Format.absolute(fetchedAt, now)}"
