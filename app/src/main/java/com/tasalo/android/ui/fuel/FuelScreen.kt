package com.tasalo.android.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tasalo.android.ui.RefreshError
import com.tasalo.android.ui.UiState
import com.tasalo.android.ui.components.EmptyMessage
import com.tasalo.android.ui.components.ErrorState
import com.tasalo.android.ui.components.FuelCard
import com.tasalo.android.ui.components.SkeletonBlock
import com.tasalo.android.ui.components.StatusBanner
import com.tasalo.android.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelScreen(state: UiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val fuel = state.fuel

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Combustible", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (fuel != null) "Actualizado ${Format.relative(fuel.fetchedAt, state.now)}" else "Sin datos aún",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (fuel != null) {
                val ago = Format.relative(fuel.fetchedAt, state.now)
                val banner = when {
                    state.fuelError == RefreshError.OFFLINE -> "Sin conexión · datos de $ago"
                    state.fuelError == RefreshError.API -> "No se pudo actualizar · datos de $ago"
                    Format.isStale(fuel.fetchedAt, state.now) -> "Datos desactualizados · $ago"
                    else -> null
                }
                if (banner != null) item { StatusBanner(banner, isError = true) }
            }

            when {
                !state.loaded || (fuel == null && (state.refreshing || state.fuelError == RefreshError.NONE)) -> {
                    items(3) { SkeletonBlock(height = 80) }
                }
                fuel == null -> {
                    item {
                        ErrorState(
                            message = if (state.fuelError == RefreshError.OFFLINE) {
                                "Sin conexión y sin datos guardados."
                            } else {
                                "No se pudo cargar el combustible."
                            },
                            onRetry = onRefresh,
                        )
                    }
                }
                fuel.items.isEmpty() -> item { EmptyMessage("Sin datos de combustible") }
                else -> items(fuel.items, key = { it.key }) { FuelCard(it) }
            }
        }
    }
}
