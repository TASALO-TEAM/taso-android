package com.tasalo.android.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import com.tasalo.android.container
import com.tasalo.android.data.local.CacheStore
import com.tasalo.android.domain.AppSettings
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.Source
import com.tasalo.android.ui.theme.TasaloTheme
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch

/**
 * Configuración al añadir W1 (fuente + 1–4 monedas) o W2 (solo fuente).
 * W3 no necesita configuración.
 */
class WidgetConfigActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className.orEmpty()
        val multi = provider.endsWith("TasasWidgetReceiver")

        setContent {
            val settings by container.settingsStore.settings.collectAsState(initial = AppSettings())
            TasaloTheme(settings.theme, settings.invertColors) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ConfigScreen(multi = multi, onSave = ::save)
                }
            }
        }
    }

    private fun save(source: Source, currencies: List<String>, multi: Boolean) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(this@WidgetConfigActivity, glanceId) { prefs ->
                prefs[WidgetKeys.SOURCE] = source.name
                if (multi) prefs[WidgetKeys.CURRENCIES] = currencies.joinToString(",")
            }
            if (multi) TasasWidget().update(this@WidgetConfigActivity, glanceId)
            else BloqueWidget().update(this@WidgetConfigActivity, glanceId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            finish()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
    @Composable
    private fun ConfigScreen(multi: Boolean, onSave: (Source, List<String>, Boolean) -> Unit) {
        val cached by container.repository.raw.collectAsState(initial = CacheStore.Raw())
        var source by remember { mutableStateOf(Source.ELTOQUE) }
        var selected by remember { mutableStateOf(listOf<String>()) }

        val rates = com.tasalo.android.data.Snapshots.from(cached, java.time.Instant.now()).rates
        val available = rates?.bySource?.get(source).orEmpty().map { it.currency }
            .ifEmpty { Currencies.PREFERRED_ORDER }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (multi) "Widget de tasas" else "Widget de bloque",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text("Fuente", style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Source.entries.forEachIndexed { index, s ->
                    SegmentedButton(
                        selected = s == source,
                        onClick = { source = s; selected = emptyList() },
                        shape = SegmentedButtonDefaults.itemShape(index, Source.entries.size),
                    ) { Text(s.title) }
                }
            }
            if (multi) {
                Text("Monedas (máximo 4)", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    available.forEach { code ->
                        val on = code in selected
                        FilterChip(
                            selected = on,
                            onClick = {
                                selected = when {
                                    on -> selected - code
                                    selected.size < 4 -> selected + code
                                    else -> selected
                                }
                            },
                            label = { Text(code) },
                        )
                    }
                }
            }
            Button(
                onClick = { onSave(source, selected.ifEmpty { available.take(2) }, multi) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Añadir widget") }
        }
    }
}
