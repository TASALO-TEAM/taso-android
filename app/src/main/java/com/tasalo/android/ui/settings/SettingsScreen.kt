package com.tasalo.android.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.diag.ReportSender
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.REPO_URL
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.ThemeMode
import com.tasalo.android.ui.MainViewModel
import com.tasalo.android.ui.UiState
import com.tasalo.android.util.Format
import java.time.Instant
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    val settings = state.settings
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }

        item {
            Section("Tema") {
                val options = listOf(ThemeMode.AUTO to "Auto", ThemeMode.DARK to "Oscuro", ThemeMode.LIGHT to "Claro")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, (mode, label) ->
                        SegmentedButton(
                            selected = settings.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(label) }
                    }
                }
            }
        }

        item {
            Section("Fuente por defecto") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Source.entries.forEachIndexed { index, source ->
                        SegmentedButton(
                            selected = settings.defaultSource == source,
                            onClick = { vm.setDefaultSource(source) },
                            shape = SegmentedButtonDefaults.itemShape(index, Source.entries.size),
                        ) { Text(source.title) }
                    }
                }
            }
        }

        item {
            Section("Colores de subida y bajada") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Invertir colores")
                        Text(
                            if (settings.invertColors) "Sube en verde, baja en rojo" else "Sube en rojo, baja en verde (como la extensión)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = settings.invertColors, onCheckedChange = vm::setInvertColors)
                }
            }
        }

        item {
            Section("Monedas visibles") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Source.entries.forEach { source ->
                        val available = state.rates?.bySource?.get(source).orEmpty().map { it.currency }
                            .ifEmpty { Currencies.PREFERRED_ORDER }
                        Text(source.title, style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            available.forEach { code ->
                                val visible = !settings.isHidden(source, code)
                                FilterChip(
                                    selected = visible,
                                    onClick = { vm.setCurrencyVisible(source, code, !visible) },
                                    label = { Text(code) },
                                )
                            }
                        }
                    }
                }
            }
        }

        item { RefreshSection(state, vm) }

        item { UpdateSection(state, vm) }

        item { DiagnosticsSection(state, vm) }

        item { AdvancedSection(currentUrl = settings.baseUrl, vm = vm) }

        item {
            HorizontalDivider()
            AboutSection(context)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun AdvancedSection(currentUrl: String, vm: MainViewModel) {
    var text by remember(currentUrl) { mutableStateOf(currentUrl) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(text) { error = null }

    Section("Avanzado") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("URL base de la API") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "Solo https. Por defecto: tasalo.duckdns.org") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val ok = vm.saveBaseUrl(text)
                        if (!ok) error = "URL no válida: debe empezar por https:// y tener host"
                    }
                }) { Text("Guardar") }
                OutlinedButton(onClick = { vm.resetBaseUrl() }) { Text("Restaurar por defecto") }
            }
        }
    }
}

@Composable
private fun AboutSection(context: Context) {
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    Section("Acerca de") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text("TASALO Android · versión $version")
            OutlinedButton(onClick = { uriHandler.openUri(REPO_URL) }) { Text("Ver repositorio") }
            Text(
                "Las tasas son referenciales. TASALO no es una aplicación oficial.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateSection(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    Section("Actualizaciones") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Versión instalada: ${state.currentVersion}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { vm.checkForUpdates(manual = true) }, enabled = !state.checkingUpdate) {
                    Text(if (state.checkingUpdate) "Buscando…" else "Buscar actualizaciones")
                }
                val update = state.update
                if (update != null) {
                    OutlinedButton(onClick = { vm.showUpdateDialog() }) { Text("Ver novedades de ${update.version}") }
                }
            }
            state.updateMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DiagnosticsSection(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    Section("Diagnóstico (beta)") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Preguntar al detectar un fallo")
                    Text(
                        "Si la app se cierra sola, te ofrece enviar un reporte al equipo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.settings.crashPrompt, onCheckedChange = vm::setCrashPrompt)
            }
            Text(
                if (state.settings.lastBackgroundRefresh > 0L) {
                    "Último refresco en segundo plano: " + Format.relative(Instant.ofEpochMilli(state.settings.lastBackgroundRefresh), state.now)
                } else {
                    "Aún no ha habido refrescos en segundo plano."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {
                if (!ReportSender.send(context)) {
                    Toast.makeText(
                        context,
                        "No hay app de correo. El reporte se copió: envíalo a ${DiagnosticLog.REPORT_EMAIL}",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }) { Text("Enviar registro por correo") }
            Text(
                "Abre tu app de correo con el reporte listo para ${DiagnosticLog.REPORT_EMAIL}. " +
                    "Lo revisas antes de enviarlo; no incluye datos personales.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshSection(state: UiState, vm: MainViewModel) {
    val options = listOf(15 to "15 min", 30 to "30 min", 60 to "1 h", 0 to "Manual")
    Section("Actualización en segundo plano") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (minutes, label) ->
                    SegmentedButton(
                        selected = state.settings.refreshMinutes == minutes,
                        onClick = { vm.setRefreshMinutes(minutes) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    ) { Text(label) }
                }
            }
            Text(
                "Cada cuánto se descargan las tasas aunque la app esté cerrada (los widgets leen de aquí). " +
                    "Más tiempo = menos batería, datos y consultas a la API. Al abrir la app siempre se actualiza si hace falta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}