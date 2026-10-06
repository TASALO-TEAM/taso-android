package com.tasalo.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import android.widget.Toast
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.diag.ReportSender
import com.tasalo.android.domain.Source
import com.tasalo.android.ui.CrashReportDialog
import com.tasalo.android.ui.UpdateDialog
import com.tasalo.android.ui.notifications.NotificationsScreen
import com.tasalo.android.ui.theme.quietGlassBackground
import com.tasalo.android.update.UpdatePhase
import com.tasalo.android.ui.MainViewModel
import com.tasalo.android.ui.blog.BlogScreen
import com.tasalo.android.ui.fuel.FuelScreen
import com.tasalo.android.ui.home.HomeScreen
import com.tasalo.android.ui.settings.SettingsScreen
import com.tasalo.android.ui.theme.TasaloTheme
import com.tasalo.android.ui.theme.isDarkTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { TasaloRoot(viewModel) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Tap en un widget: abre la pestaña Tasas en la fuente que mostraba (plan §5). */
    private fun handleIntent(intent: Intent?) {
        Source.fromId(intent?.getStringExtra(EXTRA_SOURCE))?.let(viewModel::selectSource)
    }

    companion object {
        const val EXTRA_SOURCE = "com.tasalo.android.extra.SOURCE"
    }
}

/** Abre la descarga en el navegador: Android pide la confirmación de instalación habitual. */
fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        DiagnosticLog.w("Update", "no hay navegador para abrir $url", e)
    }
}

private data class Tab(val label: String, val icon: String)

private val TABS = listOf(Tab("Tasas", "💱"), Tab("Combustible", "⛽"), Tab("Blog", "📰"), Tab("Ajustes", "⚙️"))

private const val BLOG_TAB = 2

@Composable
private fun TasaloRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val blog by vm.blog.collectAsStateWithLifecycle()
    val settings = state.settings
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val dark = isDarkTheme(settings.theme)

    // Que los iconos de las barras del sistema respeten el tema elegido en Ajustes, no solo el del sistema.
    DisposableEffect(dark) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale() }

    TasaloTheme(settings.theme, settings.invertColors) {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var showNotifications by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = showNotifications) { showNotifications = false }
        // Dentro de un post del blog, "atrás" vuelve a la lista (no cierra la app).
        BackHandler(enabled = !showNotifications && tab == BLOG_TAB && blog.selected != null) { vm.closePost() }
        // Al abrir la pestaña Blog: muestra lo guardado y descarga si hace falta (más de 15 min).
        LaunchedEffect(tab) { if (tab == BLOG_TAB) vm.loadBlog() }

        // Un solo diálogo a la vez: primero el reporte de fallo, después la actualización.
        val update = state.update
        if (state.showCrash) {
            CrashReportDialog(
                onSend = {
                    val opened = ReportSender.send(activity)
                    if (!opened) {
                        Toast.makeText(
                            activity,
                            "No hay app de correo. El reporte se copió al portapapeles: envíalo a ${DiagnosticLog.REPORT_EMAIL}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    vm.onCrashHandled()
                },
                onDismiss = vm::onCrashHandled,
            )
        } else if ((state.showUpdate || state.updatePhase != UpdatePhase.Idle) && update != null) {
            UpdateDialog(
                info = update,
                currentVersion = state.currentVersion,
                phase = state.updatePhase,
                onUpdate = vm::startUpdate,
                onLater = {
                    vm.resetUpdatePhase()
                    vm.dismissUpdate()
                },
                onSkip = vm::skipUpdate,
                onCancel = vm::cancelUpdate,
                onBrowser = {
                    openUrl(activity, update.apkUrl ?: update.pageUrl)
                    vm.resetUpdatePhase()
                    vm.dismissUpdate()
                },
                onOpenSettings = {
                    runCatching {
                        activity.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                        )
                    }
                    vm.resetUpdatePhase()
                },
            )
        }
        Scaffold(
            modifier = Modifier.quietGlassBackground(),
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = {
                                tab = index
                                showNotifications = false
                            },
                            icon = { Text(item.icon, modifier = Modifier.semantics { contentDescription = item.label }) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                if (showNotifications) {
                    NotificationsScreen(
                        state = state,
                        vm = vm,
                        onBack = { showNotifications = false },
                        onUpdate = vm::showUpdateDialog,
                    )
                } else {
                    when (tab) {
                        0 -> HomeScreen(state, vm::refresh, vm::selectSource, onOpenNotifications = { showNotifications = true })
                        1 -> FuelScreen(state, vm::refresh)
                        BLOG_TAB -> BlogScreen(
                            state = blog,
                            onRefresh = { vm.loadBlog(force = true) },
                            onSelectAccount = vm::selectBlogAccount,
                            onSetCustom = vm::setBlogCustom,
                            onClearCustom = vm::clearBlogCustom,
                            onOpen = vm::openPost,
                            onClose = vm::closePost,
                        )
                        else -> SettingsScreen(state, vm)
                    }
                }
            }
        }
    }
}
