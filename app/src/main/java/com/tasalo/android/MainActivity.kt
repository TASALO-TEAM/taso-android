package com.tasalo.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.mutableIntStateOf
import android.os.Build
import com.tasalo.android.ui.components.LocalBottomBarInset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasalo.android.diag.DiagnosticLog
import com.tasalo.android.diag.ReportSender
import com.tasalo.android.domain.Source
import com.tasalo.android.ui.CrashReportDialog
import com.tasalo.android.ui.UpdateDialog
import com.tasalo.android.ui.calculator.CalculatorScreen
import com.tasalo.android.ui.components.GlassIslandBar
import com.tasalo.android.ui.components.IslandItem
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
import kotlin.math.abs
import kotlinx.coroutines.launch

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

/** Orden de la barra: Tasas · Combustible · Calculadora (centro) · Blog · Ajustes. */
private val TABS = listOf(
    IslandItem("Tasas", Icons.Filled.SwapHoriz),
    IslandItem("Combustible", Icons.Filled.LocalGasStation),
    IslandItem("Calculadora", Icons.Filled.Calculate),
    IslandItem("Blog", Icons.AutoMirrored.Filled.Article),
    IslandItem("Ajustes", Icons.Filled.Settings),
)

private const val CALC_TAB = 2
private const val BLOG_TAB = 3

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
        // Android 10+ pinta un velo translúcido tras la barra de navegación de 3 botones: sería una franja
        // detrás de la cápsula flotante. Se quita (después de enableEdgeToEdge, que lo vuelve a fijar).
        if (Build.VERSION.SDK_INT >= 29) activity.window.isNavigationBarContrastEnforced = false
        onDispose { }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshIfStale() }

    TasaloTheme(settings.theme, settings.invertColors) {
        // Las secciones viven en un paginador: se cambia tocando la isla o deslizando con el dedo.
        val pagerState = rememberPagerState(pageCount = { TABS.size })
        val scope = rememberCoroutineScope()
        var showNotifications by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = showNotifications) { showNotifications = false }
        // Dentro de un post del blog, "atrás" vuelve a la lista (no cierra la app).
        BackHandler(enabled = !showNotifications && pagerState.currentPage == BLOG_TAB && blog.selected != null) {
            vm.closePost()
        }
        // Al quedarse en la sección Blog: muestra lo guardado y descarga si hace falta (más de 15 min).
        val settledTab = pagerState.settledPage
        LaunchedEffect(settledTab) { if (settledTab == BLOG_TAB) vm.loadBlog() }

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
        // La cápsula flota sobre todo: el paginador ocupa la pantalla entera y cada lista reserva el hueco de abajo.
        var barHeightPx by remember { mutableIntStateOf(0) }
        val barInset = with(LocalDensity.current) { barHeightPx.toDp() }
        val layoutDirection = LocalLayoutDirection.current
        Scaffold(
            modifier = Modifier.quietGlassBackground(),
            containerColor = Color.Transparent,
            // Con el fondo transparente Material ya no deduce el color del contenido: sin esto, todo texto o icono
            // sin color propio (Markdown del Blog, IconButton...) caía al negro por defecto y se perdía en modo oscuro.
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalBottomBarInset provides barInset) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Solo se respeta el borde de arriba y los laterales: abajo manda la cápsula flotante.
                            .padding(
                                start = padding.calculateStartPadding(layoutDirection),
                                top = padding.calculateTopPadding(),
                                end = padding.calculateEndPadding(layoutDirection),
                            ),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            beyondViewportPageCount = 1,
                            userScrollEnabled = !showNotifications,
                            // Al soltar, la sección se asienta con un resorte en vez del frenado lineal por defecto.
                            flingBehavior = PagerDefaults.flingBehavior(
                                state = pagerState,
                                snapAnimationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                            ),
                        ) { page ->
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    // Profundidad suave: la sección que se va se aleja y se apaga con una curva
                                    // (no lineal) y se mueve un poco más despacio que el dedo (parallax).
                                    .graphicsLayer {
                                        val signed = (page - pagerState.currentPage - pagerState.currentPageOffsetFraction)
                                            .coerceIn(-1f, 1f)
                                        val d = abs(signed)
                                        val eased = d * d * (3f - 2f * d)
                                        alpha = 1f - 0.45f * eased
                                        val scale = 1f - 0.06f * eased
                                        scaleX = scale
                                        scaleY = scale
                                        // Parallax leve que vale 0 en reposo y a una página entera: nunca asoma una página vecina.
                                        translationX = -signed * size.width * 0.18f * (1f - d)
                                    },
                            ) {
                                when (page) {
                                    0 -> HomeScreen(state, vm::refresh, vm::selectSource, onOpenNotifications = { showNotifications = true })
                                    1 -> FuelScreen(state, vm::refresh)
                                    CALC_TAB -> CalculatorScreen(state, vm::refresh)
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
                        if (showNotifications) {
                            // Encima del paginador (que se queda como estaba) y tapando los toques de lo que hay debajo.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .quietGlassBackground()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                            ) {
                                NotificationsScreen(
                                    state = state,
                                    vm = vm,
                                    onBack = { showNotifications = false },
                                    onUpdate = vm::showUpdateDialog,
                                )
                            }
                        }
                    }
                }
                GlassIslandBar(
                    items = TABS,
                    selected = pagerState.currentPage,
                    position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
                    onSelect = { index ->
                        showNotifications = false
                        scope.launch {
                            val from = pagerState.currentPage
                            // Un salto largo no recorre las secciones del medio: se salta junto al destino
                            // y solo el último tramo se anima.
                            if (abs(index - from) > 1) {
                                pagerState.scrollToPage(if (index > from) index - 1 else index + 1)
                            }
                            pagerState.animateScrollToPage(
                                index,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { barHeightPx = it.height },
                )
            }
        }
    }
}
