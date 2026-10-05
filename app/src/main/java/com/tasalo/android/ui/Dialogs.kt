package com.tasalo.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tasalo.android.ui.components.MarkdownView
import com.tasalo.android.update.UpdateInfo
import com.tasalo.android.update.UpdatePhase

/** Diálogo de actualización: novedades + consentimiento, y después progreso/errores sin salir de la app. */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    phase: UpdatePhase,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
    onBrowser: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val busy = phase is UpdatePhase.Downloading || phase is UpdatePhase.Verifying || phase is UpdatePhase.Installing
    val size = info.apkBytes?.let { " (%.1f MB)".format(it / 1_048_576.0) }.orEmpty()

    AlertDialog(
        // Mientras se descarga no se cierra con un toque fuera: para eso está el botón Cancelar.
        onDismissRequest = { if (!busy) onLater() },
        title = {
            Text(
                when (phase) {
                    is UpdatePhase.Downloading -> "Descargando ${info.version}"
                    UpdatePhase.Verifying -> "Comprobando la descarga"
                    UpdatePhase.Installing -> "Instalando ${info.version}"
                    UpdatePhase.NeedsPermission -> "Falta un permiso"
                    is UpdatePhase.Failed -> "No se pudo actualizar"
                    UpdatePhase.Idle -> "Nueva versión ${info.version}"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (phase) {
                    UpdatePhase.Idle -> {
                        Text(
                            "Tienes la versión $currentVersion. Al pulsar Actualizar, TASALO descargará$size e instalará " +
                                "la versión ${info.version}. Android puede pedirte que confirmes la instalación.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Column(
                            Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            MarkdownView(info.notes ?: "[Ver cambios en GitHub](${info.pageUrl})")
                        }
                        TextButton(onClick = onSkip) { Text("Omitir esta versión") }
                    }
                    is UpdatePhase.Downloading -> {
                        LinearProgressIndicator(progress = { phase.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("${phase.percent} %", style = MaterialTheme.typography.bodyMedium)
                    }
                    UpdatePhase.Verifying, UpdatePhase.Installing -> {
                        CircularProgressIndicator()
                        Text(
                            if (phase == UpdatePhase.Installing) {
                                "Si Android pide confirmar, pulsa Instalar. La app se reiniciará con la versión nueva."
                            } else {
                                "Comprobando que el archivo no está dañado y que es de TASALO."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    UpdatePhase.NeedsPermission -> Text(
                        "Para instalar la actualización, Android necesita que permitas a TASALO instalar aplicaciones. " +
                            "Solo se usa para actualizarse a sí misma. Actívalo en la pantalla que se abrirá, vuelve y pulsa Actualizar.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is UpdatePhase.Failed -> Column(
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(phase.reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            when (phase) {
                UpdatePhase.Idle -> TextButton(onClick = onUpdate) { Text("Actualizar") }
                UpdatePhase.NeedsPermission -> TextButton(onClick = onOpenSettings) { Text("Abrir ajustes") }
                // Las tres acciones van apiladas a todo el ancho: en pantallas estrechas o con fuente grande
                // (p. ej. Redmi/MIUI) los textos largos se parten en varias líneas en vez de salirse de la ventana.
                is UpdatePhase.Failed -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(onClick = onUpdate) { Text("Reintentar", textAlign = TextAlign.End) }
                    TextButton(onClick = onBrowser) { Text("Descargar con el navegador", textAlign = TextAlign.End) }
                    TextButton(onClick = onLater) { Text("Cerrar", textAlign = TextAlign.End) }
                }
                else -> TextButton(onClick = onCancel, enabled = phase is UpdatePhase.Downloading) { Text("Cancelar") }
            }
        },
        dismissButton = {
            when (phase) {
                UpdatePhase.Idle -> TextButton(onClick = onLater) { Text("Más tarde") }
                is UpdatePhase.Failed -> Unit
                UpdatePhase.NeedsPermission -> TextButton(onClick = onLater) { Text("Ahora no") }
                else -> Unit
            }
        },
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
