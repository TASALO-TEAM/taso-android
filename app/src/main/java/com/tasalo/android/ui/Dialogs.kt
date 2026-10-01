package com.tasalo.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasalo.android.ui.components.MarkdownView
import com.tasalo.android.update.UpdateInfo

@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Nueva versión ${info.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Tienes la versión $currentVersion. ¿Quieres actualizar ahora?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Column(
                    Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MarkdownView(info.notes ?: "[Ver cambios en GitHub](${info.pageUrl})")
                }
                TextButton(onClick = onSkip) { Text("Omitir esta versión") }
            }
        },
        confirmButton = { TextButton(onClick = onUpdate) { Text("Actualizar") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Más tarde") } },
    )
}

@Composable
fun CrashReportDialog(onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("TASALO se cerró inesperadamente") },
        text = {
            Text(
                "Estamos en beta y tu ayuda es importante. ¿Quieres enviar un reporte al equipo " +
                    "(tasaloteam@gmail.com)? Se abrirá tu app de correo con el texto ya escrito para que lo revises. " +
                    "Incluye el modelo del teléfono, la versión de Android y el error; no incluye datos personales.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onSend) { Text("Enviar por correo") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
    )
}
