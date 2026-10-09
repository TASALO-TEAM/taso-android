package com.tasalo.android.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.tasalo.android.ui.components.AdaptiveSegmentedChoice
import com.tasalo.android.ui.components.floatingContentPadding
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import coil.compose.SubcomposeAsyncImage
import com.tasalo.android.ui.components.GlassCard
import com.tasalo.android.ui.components.NavIcons
import com.tasalo.android.ui.components.SocialIcons
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.tasalo.android.ui.theme.TasaloMono
import com.tasalo.android.update.CertInfo
import com.tasalo.android.domain.Currencies
import com.tasalo.android.domain.REPO_URL
import com.tasalo.android.domain.Source
import com.tasalo.android.domain.defaultCodes
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
    val version = remember { appVersion(context) }
    val summaries = settingsSummaries(settings, version, state.update?.version)
    // Una sola categoria abierta a la vez (como en Notificaciones); todas cerradas al entrar.
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    fun toggle(key: String) { expanded = if (expanded == key) null else key }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = floatingContentPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Ajustes",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        item(key = SettingsCategories.APPEARANCE) {
            SettingsCategory(
                title = SettingsCategories.APPEARANCE,
                summary = summaries.getValue(SettingsCategories.APPEARANCE),
                icon = NavIcons.Appearance,
                expanded = expanded == SettingsCategories.APPEARANCE,
                onToggle = { toggle(SettingsCategories.APPEARANCE) },
            ) {
                Section("Tema") {
                    val options = listOf(ThemeMode.AUTO to "Auto", ThemeMode.DARK to "Oscuro", ThemeMode.LIGHT to "Claro")
                    AdaptiveSegmentedChoice(
                        options = options,
                        isSelected = { settings.theme == it.first },
                        label = { it.second },
                        onSelect = { vm.setTheme(it.first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Section("Vista de Tasas") {
                    AdaptiveSegmentedChoice(
                        options = listOf(false to "Tarjetas", true to "Lista"),
                        isSelected = { settings.ratesListView == it.first },
                        label = { it.second },
                        onSelect = { vm.setRatesListView(it.first) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "C\u00F3mo se muestran las monedas en la pantalla Tasas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Section("Colores de subida y bajada") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Invertir colores")
                            Text(
                                if (settings.invertColors) "Sube en verde, baja en rojo" else "Sube en rojo, baja en verde (como la extensi\u00F3n)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = settings.invertColors, onCheckedChange = vm::setInvertColors)
                    }
                }
            }
        }

        item(key = SettingsCategories.RATES) {
            SettingsCategory(
                title = SettingsCategories.RATES,
                summary = summaries.getValue(SettingsCategories.RATES),
                icon = NavIcons.Rates,
                expanded = expanded == SettingsCategories.RATES,
                onToggle = { toggle(SettingsCategories.RATES) },
            ) {
                Section("Fuente por defecto") {
                    AdaptiveSegmentedChoice(
                        options = Source.entries,
                        isSelected = { settings.defaultSource == it },
                        label = { it.title },
                        onSelect = vm::setDefaultSource,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Section("Monedas visibles") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Source.entries.forEach { source ->
                            val available = state.rates?.bySource?.get(source).orEmpty().map { it.currency }
                                .ifEmpty { source.defaultCodes() }
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
                RefreshSection(state, vm)
            }
        }

        item(key = SettingsCategories.UPDATES) {
            SettingsCategory(
                title = SettingsCategories.UPDATES,
                summary = summaries.getValue(SettingsCategories.UPDATES),
                icon = NavIcons.Download,
                expanded = expanded == SettingsCategories.UPDATES,
                onToggle = { toggle(SettingsCategories.UPDATES) },
            ) {
                UpdateSection(state, vm)
            }
        }

        item(key = SettingsCategories.ADVANCED) {
            SettingsCategory(
                title = SettingsCategories.ADVANCED,
                summary = summaries.getValue(SettingsCategories.ADVANCED),
                icon = NavIcons.Terminal,
                expanded = expanded == SettingsCategories.ADVANCED,
                onToggle = { toggle(SettingsCategories.ADVANCED) },
            ) {
                DiagnosticsSection(state, vm)
                AdvancedSection(currentUrl = settings.baseUrl, vm = vm)
            }
        }

        // Acerca de no es una categoria: seccion fija al final, centrada.
        item {
            Column(Modifier.padding(top = 10.dp)) {
                HorizontalDivider()
                Spacer(Modifier.height(20.dp))
                AboutSection(context, version)
            }
        }
    }
}

/** Categoria colapsable con el estilo de Notificaciones: tarjeta con titulo, resumen y chevron; se abre al tocar la cabecera. */
@Composable
private fun SettingsCategory(
    title: String,
    summary: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(
                        onClickLabel = if (expanded) "Contraer $title" else "Expandir $title",
                        role = Role.Button,
                        onClick = onToggle,
                    )
                    .semantics { stateDescription = if (expanded) "Expandido" else "Contra\u00EDdo" }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    NavIcons.Chevron,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
            if (expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        val ok = vm.saveBaseUrl(text)
                        if (!ok) error = "URL no válida: debe empezar por https:// y tener host"
                    }
                }, modifier = Modifier.fillMaxRowHeight()) { Text("Guardar") }
                OutlinedButton(onClick = { vm.resetBaseUrl() }, modifier = Modifier.fillMaxRowHeight()) { Text("Restaurar por defecto") }
            }
        }
    }
}

private const val DEVELOPER_GITHUB = "ersus93"
private const val DEVELOPER_PROFILE_URL = "https://github.com/$DEVELOPER_GITHUB"

/** GitHub redirige a la foto actual del perfil: si ersus93 la cambia, la app la sigue sin tocar nada. */
private const val DEVELOPER_AVATAR_URL = "https://github.com/$DEVELOPER_GITHUB.png?size=160"

@Composable
private fun AboutSection(context: Context, version: String) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Acerca de", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        GlassCard(
            Modifier
                .padding(top = 8.dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = "Abrir el perfil de GitHub de $DEVELOPER_GITHUB") {
                    runCatching { uriHandler.openUri(DEVELOPER_PROFILE_URL) }
                },
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                DeveloperAvatar()
                Spacer(Modifier.height(6.dp))
                Text(DEVELOPER_GITHUB, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Desarrollador de TASALO",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(SocialIcons.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text(
                        "github.com/$DEVELOPER_GITHUB",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text("TASALO Android \u00B7 versi\u00F3n $version", modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            SOCIAL_LINKS.forEach { link ->
                IconButton(onClick = { runCatching { uriHandler.openUri(link.url) } }) {
                    Icon(link.icon, contentDescription = link.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val fingerprint = remember { CertInfo.installedFingerprint(context) }
        Text(
            "Huella SHA-256 del certificado de firma (debe coincidir con la de la Release en GitHub):",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        SelectionContainer {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    fingerprint ?: "no disponible",
                    fontFamily = TasaloMono,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Text(
            "Las tasas son referenciales. TASALO no es una aplicaci\u00F3n oficial.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Foto de perfil de GitHub en circulo; sin conexion o si falla, un circulo neutro con icono (sin errores visibles). */
@Composable
private fun DeveloperAvatar() {
    val shape = CircleShape
    SubcomposeAsyncImage(
        model = DEVELOPER_AVATAR_URL,
        contentDescription = "Foto de perfil de $DEVELOPER_GITHUB en GitHub",
        modifier = Modifier
            .size(64.dp)
            .clip(shape)
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape),
        contentScale = ContentScale.Crop,
        loading = { AvatarFallback() },
        error = { AvatarFallback() },
    )
}

@Composable
private fun AvatarFallback() {
    Box(
        Modifier.size(64.dp).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SocialIcons.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UpdateSection(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Versión instalada: ${state.currentVersion}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.checkForUpdates(manual = true) }, enabled = !state.checkingUpdate, modifier = Modifier.fillMaxRowHeight()) {
                    Text(if (state.checkingUpdate) "Buscando…" else "Buscar actualizaciones")
                }
                val update = state.update
                if (update != null) {
                    OutlinedButton(onClick = { vm.showUpdateDialog() }, modifier = Modifier.fillMaxRowHeight()) { Text("Ver novedades de ${update.version}") }
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
            AdaptiveSegmentedChoice(
                options = options,
                isSelected = { state.settings.refreshMinutes == it.first },
                label = { it.second },
                onSelect = { vm.setRefreshMinutes(it.first) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Cada cuánto se descargan las tasas aunque la app esté cerrada (los widgets leen de aquí). " +
                    "Más tiempo = menos batería, datos y consultas a la API. Al abrir la app siempre se actualiza si hace falta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
private data class SocialLink(val label: String, val url: String, val icon: ImageVector)

/** Iconos de trazo de la maqueta (`SocialIcons`). Más canales de Telegram (grupo, canal) se añaden aquí con `SocialIcons.Telegram`. */
private val SOCIAL_LINKS = listOf(
    SocialLink("Bot de Telegram (@tasalobot)", "https://t.me/tasalobot", SocialIcons.Telegram),
    SocialLink("GitHub", "https://github.com/TASALO-TEAM", SocialIcons.Code),
    SocialLink("Blog de TASALO en Ecency", "https://ecency.com/@tasalo", SocialIcons.Article),
    SocialLink("Correo del equipo", "mailto:tasaloteam@gmail.com", SocialIcons.Mail),
)