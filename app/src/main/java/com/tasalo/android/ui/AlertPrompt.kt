package com.tasalo.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Los avisos de alertas del bot nacen activos. En Android 12 o menos (o con el permiso ya dado) se dejan activos sin preguntar;
 * en Android 13+ se explica y se pide el permiso del sistema una sola vez. Se puede apagar cuando quiera en Ajustes.
 */
@Composable
fun AlertPermissionPrompt(state: UiState, vm: MainViewModel, canShow: Boolean) {
    if (!state.loaded || state.settings.alertPromptDone || !canShow) return
    val context = LocalContext.current
    val needsAsk = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setAlertNotifications(granted)
    }
    if (!needsAsk) {
        LaunchedEffect(Unit) { vm.setAlertNotifications(true) }
        return
    }
    AlertDialog(
        onDismissRequest = { vm.setAlertNotifications(false) },
        title = { Text("\u00bfAvisarte de las alertas del bot?") },
        text = {
            Text(
                "Te llega una notificaci\u00f3n cuando el bot publique una alerta nueva. " +
                    "Todo ocurre en tu tel\u00e9fono. Puedes apagarlo cuando quieras en Ajustes.",
            )
        },
        confirmButton = { TextButton(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Activar") } },
        dismissButton = { TextButton(onClick = { vm.setAlertNotifications(false) }) { Text("Ahora no") } },
    )
}
