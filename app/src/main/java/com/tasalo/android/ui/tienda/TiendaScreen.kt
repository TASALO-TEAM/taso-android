@file:OptIn(ExperimentalLayoutApi::class)

package com.tasalo.android.ui.tienda

import android.view.ViewGroup
import android.webkit.WebChromeClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tasalo.android.ui.components.ErrorState
import com.tasalo.android.ui.components.LocalBottomBarInset

/**
 * Pestaña Tienda: taso-inv dentro de la app. Tienda para clientes y visitantes; herramienta completa para admin y
 * empleado (la propia web decide qué ver según la sesión). Se comporta como el navegador pero con la piel de la app:
 * barra de progreso fina, fondo del tema antes de pintar, teclado que no tapa los campos y pantalla de error con
 * reintento en vez de una página en blanco.
 *
 * [active] = esta pestaña es la que se ve: solo entonces se carga la tienda y solo entonces «atrás» retrocede en la web
 * (el paginador mantiene compuestas las páginas vecinas y no deben robarse el botón atrás).
 */
@Composable
fun TiendaScreen(controller: TiendaController, active: Boolean, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val backgroundArgb = background.toArgb()
    // Con el teclado abierto la cápsula flotante queda tapada detrás de él: no se reserva su hueco (igual que en Efectivo).
    val bottom = if (WindowInsets.isImeVisible) 0.dp else LocalBottomBarInset.current

    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        controller.finishFileChooser(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    DisposableEffect(controller, chooser) {
        controller.chooseFile = { intent -> runCatching { chooser.launch(intent) }.isSuccess }
        onDispose { controller.chooseFile = null }
    }
    LaunchedEffect(active) { if (active) controller.ensureLoaded() }
    BackHandler(enabled = active && controller.canGoBack) { controller.goBack() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { controller.onPause() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { controller.onResume() }

    Box(modifier.fillMaxSize().imePadding().padding(bottom = bottom)) {
        // key(generation): si el proceso de render del WebView muere se crea uno nuevo y se vuelve a montar.
        key(controller.generation) {
            AndroidView(
                factory = { controller.obtain(backgroundArgb) },
                modifier = Modifier.fillMaxSize(),
                update = { controller.setBackground(backgroundArgb) },
                // Se suelta de su contenedor para poder montarlo otra vez cuando la pestaña vuelva a componerse.
                onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view) },
            )
        }
        if (!controller.failed && controller.progress in 1..99) {
            LinearProgressIndicator(
                progress = { controller.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter),
                trackColor = Color.Transparent,
            )
        }
        if (controller.failed) {
            // Tapa la página de error del WebView (y sus toques) con el aspecto de la app.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(background)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(
                    message = "No se pudo conectar con la tienda. Revisa tu conexión e inténtalo de nuevo.",
                    onRetry = controller::retry,
                )
            }
        }
    }
}
