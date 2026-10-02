package com.tasalo.android.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tasalo.android.ui.MainViewModel
import com.tasalo.android.ui.UiState
import com.tasalo.android.ui.components.EmptyMessage
import com.tasalo.android.ui.components.GlassCard
import com.tasalo.android.ui.components.MarkdownView
import com.tasalo.android.update.ReleaseInfo
import com.tasalo.android.update.SemVer
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Notificaciones, con dos secciones: **Alertas** (mensajes del equipo; llegan en la v0.4.0 desde el bot)
 * y **Actualizaciones** (las versiones publicadas). Los elementos están contraídos y se expanden al tocarlos.
 */
@Composable
fun NotificationsScreen(
    state: UiState,
    vm: MainViewModel,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.loadReleases() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Notificaciones", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Alertas") })
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(if (state.update != null) "Actualizaciones •" else "Actualizaciones") },
            )
        }
        when (tab) {
            0 -> EmptyMessage("Aquí llegarán los avisos y mensajes del equipo TASALO. Por ahora no hay ninguno.")
            else -> UpdatesTab(state, onUpdate)
        }
    }
}

@Composable
private fun UpdatesTab(state: UiState, onUpdate: () -> Unit) {
    // Un solo elemento abierto a la vez: así la lista sigue siendo corta.
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val releases = state.releases
    val pending = state.update

    if (releases.isEmpty()) {
        EmptyMessage(
            if (pending != null) {
                "Hay una versión nueva (${pending.version}). Cuando haya conexión verás aquí la lista de cambios."
            } else {
                "Sin conexión o sin versiones publicadas todavía."
            },
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(releases, key = { it.version }) { release ->
            val newer = SemVer.isNewer(release.version, state.currentVersion)
            ReleaseItem(
                release = release,
                installed = !newer && release.version == state.currentVersion,
                newer = newer,
                expanded = expanded == release.version,
                onToggle = { expanded = if (expanded == release.version) null else release.version },
                onUpdate = onUpdate,
            )
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault())

@Composable
private fun ReleaseItem(
    release: ReleaseInfo,
    installed: Boolean,
    newer: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onUpdate: () -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onToggle)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Versión ${release.version}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val date = release.publishedAt?.let { dateFormat.format(it) }
                    val tag = when {
                        newer -> "Nueva"
                        installed -> "Instalada"
                        else -> null
                    }
                    Text(
                        listOfNotNull(date, tag).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (newer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "▴" else "▾", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                MarkdownView(release.notes ?: "[Ver cambios en GitHub](${release.pageUrl})")
                if (newer) Button(onClick = onUpdate) { Text("Actualizar") }
            }
        }
    }
}
