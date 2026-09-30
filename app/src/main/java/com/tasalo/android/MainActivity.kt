package com.tasalo.android

import android.content.Intent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasalo.android.domain.Source
import com.tasalo.android.ui.MainViewModel
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

private data class Tab(val label: String, val icon: String)

private val TABS = listOf(Tab("Tasas", "💱"), Tab("Combustible", "⛽"), Tab("Ajustes", "⚙️"))

@Composable
private fun TasaloRoot(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
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
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar {
                    TABS.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(item.icon, modifier = Modifier.semantics { contentDescription = item.label }) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(state, vm::refresh, vm::selectSource)
                    1 -> FuelScreen(state, vm::refresh)
                    else -> SettingsScreen(state, vm)
                }
            }
        }
    }
}
